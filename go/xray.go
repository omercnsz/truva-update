//go:build linux
// +build linux

package xray

import (
	"context"
	"errors"
	"fmt"
	"io"
	"log"
	"net"
	"os"
	"sync"
	"time"

	"github.com/xtls/xray-core/core"
	_ "github.com/xtls/xray-core/main/distro/all" // JSON loader + tüm protokoller (vless, reality, socks, freedom vb.)
	"gvisor.dev/gvisor/pkg/buffer"
	"gvisor.dev/gvisor/pkg/tcpip"
	"gvisor.dev/gvisor/pkg/tcpip/adapters/gonet"
	"gvisor.dev/gvisor/pkg/tcpip/header"
	"gvisor.dev/gvisor/pkg/tcpip/link/channel"
	"gvisor.dev/gvisor/pkg/tcpip/network/ipv4"
	"gvisor.dev/gvisor/pkg/tcpip/network/ipv6"
	"gvisor.dev/gvisor/pkg/tcpip/stack"
	"gvisor.dev/gvisor/pkg/tcpip/transport/tcp"
	"gvisor.dev/gvisor/pkg/tcpip/transport/udp"
	"gvisor.dev/gvisor/pkg/waiter"
)

// ═══════════════════════════════════════════════════════════════════
// Sabitler
// ═══════════════════════════════════════════════════════════════════

const (
	nicID         tcpip.NICID = 1
	xraySOCKSPort int         = 10808
)

// ═══════════════════════════════════════════════════════════════════
// Global durum
// ═══════════════════════════════════════════════════════════════════

var (
	xrayInstance *core.Instance
	ipStack      *stack.Stack
	linkEndpoint *channel.Endpoint
	tunFile      *os.File
	mu           sync.Mutex
	stopCh       chan struct{}
	wg           sync.WaitGroup // goroutine'lerin bitmesini beklemek için
)

// ═══════════════════════════════════════════════════════════════════
// Init — Xray motorunu ve gVisor netstack'i başlatır
//
// Yaşam döngüsü:
//   1. Önceki instance varsa temizle
//   2. Xray-core'u JSON config ile başlat
//   3. gVisor netstack oluştur: channel endpoint + NIC + routing
//   4. TCP forwarder başlat
//   5. UDP forwarder başlat (DNS + genel UDP)
// ═══════════════════════════════════════════════════════════════════

func Init(configJSON string) error {
	mu.Lock()
	defer mu.Unlock()

	// Temizlik
	cleanup()

	// 1. Xray-core başlat
	instance, err := core.StartInstance("json", []byte(configJSON))
	if err != nil {
		return fmt.Errorf("xray başlatma hatası: %w", err)
	}
	xrayInstance = instance

	// 2. gVisor netstack
	linkEndpoint = channel.New(256, 1500, "")
	ipStack = stack.New(stack.Options{
		NetworkProtocols:   []stack.NetworkProtocolFactory{ipv4.NewProtocol, ipv6.NewProtocol},
		TransportProtocols: []stack.TransportProtocolFactory{tcp.NewProtocol, udp.NewProtocol},
	})

	// 3. NIC
	if tcpipErr := ipStack.CreateNIC(nicID, linkEndpoint); tcpipErr != nil {
		return fmt.Errorf("CreateNIC hatası: %v", tcpipErr)
	}
	ipStack.SetPromiscuousMode(nicID, true)
	ipStack.SetSpoofing(nicID, true)
	ipStack.SetRouteTable([]tcpip.Route{
		{Destination: header.IPv4EmptySubnet, NIC: nicID},
		{Destination: header.IPv6EmptySubnet, NIC: nicID},
	})

	// 4. TCP Forwarder
	tcpFwd := tcp.NewForwarder(ipStack, 0, 256, func(r *tcp.ForwarderRequest) {
		id := r.ID()
		var wq waiter.Queue
		ep, tcpErr := r.CreateEndpoint(&wq)
		if tcpErr != nil {
			r.Complete(true)
			return
		}
		r.Complete(false)
		conn := gonet.NewTCPConn(&wq, ep)
		go bridgeTCP(conn, id.LocalAddress, id.LocalPort)
	})
	ipStack.SetTransportProtocolHandler(tcp.ProtocolNumber, tcpFwd.HandlePacket)

	// 5. UDP Forwarder (sadece DNS)
	udpFwd := udp.NewForwarder(ipStack, func(r *udp.ForwarderRequest) bool {
		id := r.ID()
		var wq waiter.Queue
		ep, udpErr := r.CreateEndpoint(&wq)
		if udpErr != nil {
			return false
		}
		conn := gonet.NewUDPConn(&wq, ep)
		go bridgeUDP(conn, id.LocalAddress, id.LocalPort)
		return true
	})
	ipStack.SetTransportProtocolHandler(udp.ProtocolNumber, udpFwd.HandlePacket)

	stopCh = make(chan struct{})
	return nil
}

// ═══════════════════════════════════════════════════════════════════
// SetTunFD — Android TUN fd'sini netstack'e bağlar
// ═══════════════════════════════════════════════════════════════════

func SetTunFD(fd int64) error {
	mu.Lock()
	defer mu.Unlock()

	if fd <= 0 {
		return errors.New("geçersiz TUN FD")
	}
	if linkEndpoint == nil || ipStack == nil {
		return errors.New("Init() henüz çağrılmadı")
	}

	tunFile = os.NewFile(uintptr(fd), "tun")

	// TUN → Netstack
	wg.Add(1)
	go func() {
		defer wg.Done()
		defer func() {
			if r := recover(); r != nil {
				log.Printf("[TUN→Netstack] recover: %v", r)
			}
		}()
		buf := make([]byte, 1600)
		for {
			select {
			case <-stopCh:
				return
			default:
			}

			n, err := tunFile.Read(buf)
			if err != nil || n < 1 {
				return
			}

			pkt := make([]byte, n)
			copy(pkt, buf[:n])

			var proto tcpip.NetworkProtocolNumber
			switch pkt[0] >> 4 {
			case 4:
				proto = ipv4.ProtocolNumber
			case 6:
				proto = ipv6.ProtocolNumber
			default:
				continue
			}

			// ipStack veya linkEndpoint kapalıysa panic olabilir
			select {
			case <-stopCh:
				return
			default:
			}

			pb := stack.NewPacketBuffer(stack.PacketBufferOptions{
				Payload: buffer.MakeWithData(pkt),
			})
			linkEndpoint.InjectInbound(proto, pb)
			pb.DecRef()
		}
	}()

	// Netstack → TUN
	wg.Add(1)
	go func() {
		defer wg.Done()
		defer func() {
			if r := recover(); r != nil {
				log.Printf("[Netstack→TUN] recover: %v", r)
			}
		}()
		for {
			pkt := linkEndpoint.ReadContext(context.Background())
			if pkt == nil {
				return
			}
			data := pkt.ToView().AsSlice()
			tunFile.Write(data)
			pkt.DecRef()
		}
	}()

	return nil
}

// ═══════════════════════════════════════════════════════════════════
// bridgeTCP — TCP'yi SOCKS5 CONNECT ile Xray'e köprüler
//
// KRİTİK FIX: IP adresi binary olarak gönderilir (0x01=IPv4, 0x04=IPv6)
// Eski kodda string olarak 0x03 (domain) gönderiliyordu — Xray bunu
// resolve edemeyebilir ve bağlantı kopardı.
// ═══════════════════════════════════════════════════════════════════

func bridgeTCP(localConn net.Conn, destAddr tcpip.Address, destPort uint16) {
	defer localConn.Close()

	socksConn, err := net.Dial("tcp", fmt.Sprintf("127.0.0.1:%d", xraySOCKSPort))
	if err != nil {
		log.Printf("[bridgeTCP] SOCKS5 bağlantı hatası %v:%d → %v", destAddr, destPort, err)
		return
	}
	defer socksConn.Close()

	// SOCKS5 auth: no-auth
	socksConn.Write([]byte{0x05, 0x01, 0x00})
	authResp := make([]byte, 2)
	if _, err := io.ReadFull(socksConn, authResp); err != nil || authResp[1] != 0x00 {
		log.Printf("[bridgeTCP] SOCKS5 auth başarısız %v:%d", destAddr, destPort)
		return
	}

	// SOCKS5 CONNECT
	connectReq := buildSocks5Connect(destAddr, destPort)
	if connectReq == nil {
		log.Printf("[bridgeTCP] buildSocks5Connect nil döndü %v:%d", destAddr, destPort)
		return
	}
	socksConn.Write(connectReq)

	if !readSocks5Reply(socksConn) {
		log.Printf("[bridgeTCP] SOCKS5 CONNECT reddedildi %v:%d", destAddr, destPort)
		return
	}

	log.Printf("[bridgeTCP] OK %v:%d", destAddr, destPort)

	// Bidirectional copy
	done := make(chan struct{}, 2)
	go func() { io.Copy(socksConn, localConn); done <- struct{}{} }()
	go func() { io.Copy(localConn, socksConn); done <- struct{}{} }()
	<-done
}

// ═══════════════════════════════════════════════════════════════════
// bridgeUDP — Tüm UDP trafiğini Xray'e yönlendirir
//
// Port 53 (DNS): DNS-over-TCP ile SOCKS5 CONNECT üzerinden
// Diğer portlar: SOCKS5 UDP ASSOCIATE ile (Roblox UDMUX vb.)
// ═══════════════════════════════════════════════════════════════════

func bridgeUDP(localConn net.Conn, destAddr tcpip.Address, destPort uint16) {
	defer localConn.Close()

	if destPort == 53 {
		bridgeDNS(localConn, destAddr)
		return
	}

	// Genel UDP → SOCKS5 UDP ASSOCIATE
	bridgeGeneralUDP(localConn, destAddr, destPort)
}

// ═══════════════════════════════════════════════════════════════════
// bridgeGeneralUDP — SOCKS5 UDP ASSOCIATE ile genel UDP yönlendirme
//
// SOCKS5 akışı:
//   1. TCP kontrol kanalı aç (auth + UDP ASSOCIATE)
//   2. Sunucudan relay adresi al
//   3. UDP datagramları relay'e gönder (SOCKS5 UDP formatında)
//   4. Yanıtları localConn'a geri yaz
//   5. TCP kontrol kanalı kapanınca oturum biter
// ═══════════════════════════════════════════════════════════════════

func bridgeGeneralUDP(localConn net.Conn, destAddr tcpip.Address, destPort uint16) {
	// 1. TCP kontrol kanalı → SOCKS5
	controlConn, err := net.DialTimeout("tcp", fmt.Sprintf("127.0.0.1:%d", xraySOCKSPort), 5*time.Second)
	if err != nil {
		log.Printf("[bridgeUDP] SOCKS5 control bağlantı hatası %v:%d → %v", destAddr, destPort, err)
		return
	}
	defer controlConn.Close()

	// 2. Auth
	controlConn.Write([]byte{0x05, 0x01, 0x00})
	authResp := make([]byte, 2)
	if _, err := io.ReadFull(controlConn, authResp); err != nil || authResp[1] != 0x00 {
		log.Printf("[bridgeUDP] SOCKS5 auth başarısız %v:%d", destAddr, destPort)
		return
	}

	// 3. UDP ASSOCIATE (CMD=0x03) — istemci adresi 0.0.0.0:0
	assocReq := []byte{
		0x05, 0x03, 0x00, 0x01, // VER, CMD=UDP_ASSOCIATE, RSV, ATYP=IPv4
		0x00, 0x00, 0x00, 0x00, // 0.0.0.0
		0x00, 0x00, // port 0
	}
	controlConn.Write(assocReq)

	// 4. Yanıt oku — BND.ADDR ve BND.PORT
	hdr := make([]byte, 4)
	if _, err := io.ReadFull(controlConn, hdr); err != nil {
		log.Printf("[bridgeUDP] UDP ASSOCIATE okuma hatası %v:%d → %v", destAddr, destPort, err)
		return
	}
	if hdr[1] != 0x00 {
		log.Printf("[bridgeUDP] UDP ASSOCIATE reddedildi %v:%d status=%02x", destAddr, destPort, hdr[1])
		return
	}

	var relayHost string
	var relayPort int
	switch hdr[3] {
	case 0x01: // IPv4
		addrPort := make([]byte, 6)
		if _, err := io.ReadFull(controlConn, addrPort); err != nil {
			log.Printf("[bridgeUDP] IPv4 relay adres okuma hatası %v:%d", destAddr, destPort)
			return
		}
		relayHost = fmt.Sprintf("%d.%d.%d.%d", addrPort[0], addrPort[1], addrPort[2], addrPort[3])
		relayPort = int(addrPort[4])<<8 | int(addrPort[5])
	case 0x04: // IPv6
		addrPort := make([]byte, 18)
		if _, err := io.ReadFull(controlConn, addrPort); err != nil {
			log.Printf("[bridgeUDP] IPv6 relay adres okuma hatası %v:%d", destAddr, destPort)
			return
		}
		relayHost = net.IP(addrPort[:16]).String()
		relayPort = int(addrPort[16])<<8 | int(addrPort[17])
	default:
		log.Printf("[bridgeUDP] bilinmeyen ATYP %02x %v:%d", hdr[3], destAddr, destPort)
		return
	}

	// 0.0.0.0 → 127.0.0.1 (loopback)
	if relayHost == "0.0.0.0" || relayHost == "::" {
		relayHost = "127.0.0.1"
	}
	relayAddr := fmt.Sprintf("%s:%d", relayHost, relayPort)
	log.Printf("[bridgeUDP] UDP ASSOCIATE OK → relay=%s dest=%v:%d", relayAddr, destAddr, destPort)

	// 5. UDP relay soketini aç
	relayUDPAddr, err := net.ResolveUDPAddr("udp", relayAddr)
	if err != nil {
		log.Printf("[bridgeUDP] relay adres çözümleme hatası %s → %v", relayAddr, err)
		return
	}
	relayConn, err := net.DialUDP("udp", nil, relayUDPAddr)
	if err != nil {
		log.Printf("[bridgeUDP] relay UDP bağlantı hatası %s → %v", relayAddr, err)
		return
	}
	defer relayConn.Close()

	// SOCKS5 UDP datagram header oluştur (sabit — her pakette aynı hedef)
	udpHeader := buildSocks5UDPHeader(destAddr, destPort)

	done := make(chan struct{}, 2)

	// localConn → relay (uygulama → Xray)
	go func() {
		defer func() { done <- struct{}{} }()
		buf := make([]byte, 65535)
		for {
			localConn.(*gonet.UDPConn).SetReadDeadline(time.Now().Add(120 * time.Second))
			n, err := localConn.Read(buf)
			if err != nil || n == 0 {
				return
			}

			// SOCKS5 UDP datagram: header + payload
			datagram := make([]byte, 0, len(udpHeader)+n)
			datagram = append(datagram, udpHeader...)
			datagram = append(datagram, buf[:n]...)

			relayConn.SetWriteDeadline(time.Now().Add(10 * time.Second))
			if _, err := relayConn.Write(datagram); err != nil {
				log.Printf("[bridgeUDP] relay yazma hatası %v:%d → %v", destAddr, destPort, err)
				return
			}
		}
	}()

	// relay → localConn (Xray → uygulama)
	go func() {
		defer func() { done <- struct{}{} }()
		buf := make([]byte, 65535)
		for {
			relayConn.SetReadDeadline(time.Now().Add(120 * time.Second))
			n, err := relayConn.Read(buf)
			if err != nil || n == 0 {
				return
			}

			// SOCKS5 UDP datagram header'ı çıkar
			data := parseSocks5UDPData(buf[:n])
			if data == nil {
				continue
			}

			localConn.Write(data)
		}
	}()

	// Bir taraf kapanınca veya kontrol kanalı kapanınca çık
	// TCP kontrol kanalını da izle — kapanırsa oturum biter
	go func() {
		one := make([]byte, 1)
		controlConn.Read(one) // TCP kontrol kanalı kapanınca burası döner
		done <- struct{}{}
	}()

	<-done
	log.Printf("[bridgeUDP] oturum sonlandı %v:%d", destAddr, destPort)
}

// ═══════════════════════════════════════════════════════════════════
// buildSocks5UDPHeader — SOCKS5 UDP datagram header oluşturur
//
//	+----+------+------+----------+----------+
//	|RSV | FRAG | ATYP | DST.ADDR | DST.PORT |
//	+----+------+------+----------+----------+
//	| 2  |  1   |  1   | Variable |    2     |
//	+----+------+------+----------+----------+
//
// ═══════════════════════════════════════════════════════════════════

func buildSocks5UDPHeader(addr tcpip.Address, port uint16) []byte {
	raw := addr.AsSlice()
	var atyp byte
	switch len(raw) {
	case 4:
		atyp = 0x01
	case 16:
		atyp = 0x04
	default:
		return []byte{0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00}
	}
	hdr := make([]byte, 0, 4+len(raw)+2)
	hdr = append(hdr, 0x00, 0x00) // RSV
	hdr = append(hdr, 0x00)       // FRAG
	hdr = append(hdr, atyp)       // ATYP
	hdr = append(hdr, raw...)     // DST.ADDR
	hdr = append(hdr, byte(port>>8), byte(port&0xff)) // DST.PORT
	return hdr
}

// ═══════════════════════════════════════════════════════════════════
// parseSocks5UDPData — SOCKS5 UDP datagram'dan payload'ı çıkarır
// ═══════════════════════════════════════════════════════════════════

func parseSocks5UDPData(buf []byte) []byte {
	if len(buf) < 4 {
		return nil
	}
	// RSV(2) + FRAG(1) + ATYP(1)
	atyp := buf[3]
	var dataStart int
	switch atyp {
	case 0x01: // IPv4
		dataStart = 4 + 4 + 2 // header(4) + IPv4(4) + port(2)
	case 0x04: // IPv6
		dataStart = 4 + 16 + 2 // header(4) + IPv6(16) + port(2)
	case 0x03: // Domain
		if len(buf) < 5 {
			return nil
		}
		domainLen := int(buf[4])
		dataStart = 4 + 1 + domainLen + 2
	default:
		return nil
	}

	if dataStart >= len(buf) {
		return nil
	}
	return buf[dataStart:]
}

// ═══════════════════════════════════════════════════════════════════
// bridgeDNS — DNS trafiğini SOCKS5 TCP üzerinden yönlendirir
// ═══════════════════════════════════════════════════════════════════

func bridgeDNS(localConn net.Conn, destAddr tcpip.Address) {
	// DNS paketini oku
	buf := make([]byte, 4096)
	n, err := localConn.Read(buf)
	if err != nil || n == 0 {
		log.Printf("[bridgeDNS] DNS okuma hatası %v → %v", destAddr, err)
		return
	}
	dnsQuery := buf[:n]

	// SOCKS5 üzerinden DNS sunucusuna TCP bağlantısı
	socksConn, err := net.Dial("tcp", fmt.Sprintf("127.0.0.1:%d", xraySOCKSPort))
	if err != nil {
		log.Printf("[bridgeDNS] SOCKS5 bağlantı hatası DNS %v → %v", destAddr, err)
		return
	}
	defer socksConn.Close()

	socksConn.Write([]byte{0x05, 0x01, 0x00})
	authResp := make([]byte, 2)
	if _, err := io.ReadFull(socksConn, authResp); err != nil || authResp[1] != 0x00 {
		log.Printf("[bridgeDNS] SOCKS5 auth başarısız DNS %v", destAddr)
		return
	}

	connectReq := buildSocks5Connect(destAddr, 53)
	if connectReq == nil {
		log.Printf("[bridgeDNS] buildSocks5Connect nil DNS %v", destAddr)
		return
	}
	socksConn.Write(connectReq)
	if !readSocks5Reply(socksConn) {
		log.Printf("[bridgeDNS] SOCKS5 CONNECT reddedildi DNS %v", destAddr)
		return
	}

	// DNS-over-TCP: 2-byte length prefix
	lenBuf := []byte{byte(n >> 8), byte(n & 0xff)}
	socksConn.Write(lenBuf)
	socksConn.Write(dnsQuery)

	respLen := make([]byte, 2)
	if _, err := io.ReadFull(socksConn, respLen); err != nil {
		log.Printf("[bridgeDNS] DNS yanıt uzunluk okuma hatası %v → %v", destAddr, err)
		return
	}
	sz := int(respLen[0])<<8 | int(respLen[1])
	if sz > 4096 || sz == 0 {
		log.Printf("[bridgeDNS] DNS yanıt boyutu geçersiz %v → %d", destAddr, sz)
		return
	}
	dnsResp := make([]byte, sz)
	if _, err := io.ReadFull(socksConn, dnsResp); err != nil {
		log.Printf("[bridgeDNS] DNS yanıt okuma hatası %v → %v", destAddr, err)
		return
	}
	localConn.Write(dnsResp)
	log.Printf("[bridgeDNS] DNS OK %v (%d → %d bytes)", destAddr, n, sz)
}

// ═══════════════════════════════════════════════════════════════════
// buildSocks5Connect — IP tipine göre binary SOCKS5 CONNECT oluşturur
// ═══════════════════════════════════════════════════════════════════

func buildSocks5Connect(addr tcpip.Address, port uint16) []byte {
	raw := addr.AsSlice()
	var atyp byte
	switch len(raw) {
	case 4:
		atyp = 0x01 // IPv4
	case 16:
		atyp = 0x04 // IPv6
	default:
		return nil
	}
	// VER + CMD + RSV + ATYP + ADDR + PORT
	req := make([]byte, 0, 4+len(raw)+2)
	req = append(req, 0x05, 0x01, 0x00, atyp)
	req = append(req, raw...)
	req = append(req, byte(port>>8), byte(port&0xff))
	return req
}

// ═══════════════════════════════════════════════════════════════════
// readSocks5Reply — SOCKS5 sunucu cevabını okur
// ═══════════════════════════════════════════════════════════════════

func readSocks5Reply(conn net.Conn) bool {
	hdr := make([]byte, 4)
	if _, err := io.ReadFull(conn, hdr); err != nil {
		return false
	}
	if hdr[1] != 0x00 {
		return false
	}
	switch hdr[3] {
	case 0x01:
		_, err := io.ReadFull(conn, make([]byte, 6))
		return err == nil
	case 0x03:
		lb := make([]byte, 1)
		if _, err := io.ReadFull(conn, lb); err != nil {
			return false
		}
		_, err := io.ReadFull(conn, make([]byte, int(lb[0])+2))
		return err == nil
	case 0x04:
		_, err := io.ReadFull(conn, make([]byte, 18))
		return err == nil
	}
	return false
}

// ═══════════════════════════════════════════════════════════════════
// cleanup — Tüm kaynakları serbest bırak
// ═══════════════════════════════════════════════════════════════════

func cleanup() {
	// 1. Goroutine'lere dur sinyali gönder
	if stopCh != nil {
		close(stopCh)
		stopCh = nil
	}

	// 2. TUN dosyasını kapat — Read() hata döner → TUN→Netstack goroutine çıkar
	if tunFile != nil {
		tunFile.Close()
		tunFile = nil
	}

	// 3. Link endpoint'i kapat — ReadContext() nil döner → Netstack→TUN goroutine çıkar
	if linkEndpoint != nil {
		linkEndpoint.Close()
		linkEndpoint = nil
	}

	// 4. Goroutine'lerin tamamen çıkmasını bekle (max 2 saniye)
	done := make(chan struct{})
	go func() {
		wg.Wait()
		close(done)
	}()
	select {
	case <-done:
		log.Printf("[cleanup] Goroutine'ler temiz çıktı")
	case <-time.After(2 * time.Second):
		log.Printf("[cleanup] Goroutine timeout (2s) — devam ediliyor")
	}

	// 5. Artık kimse stack'e yazamıyor — güvenle kapat
	if ipStack != nil {
		ipStack.Close()
		ipStack = nil
	}

	// 6. En son Xray motoru
	if xrayInstance != nil {
		xrayInstance.Close()
		xrayInstance = nil
	}
}

// ═══════════════════════════════════════════════════════════════════
// ProcessPacket — Eski API (geriye uyumluluk)
// ═══════════════════════════════════════════════════════════════════

func ProcessPacket(pkt []byte) []byte {
	return nil
}

// ═════════════════════════════════════════════════════════════════
// Stop — Xray motoru ve gVisor netstack’i temiz kapat
// ═════════════════════════════════════════════════════════════════

func Stop() {
	mu.Lock()
	defer mu.Unlock()
	cleanup()
	log.Printf("[Stop] Xray motoru durduruldu")
}

// ═══════════════════════════════════════════════════════════════════
// TestConnection — SOCKS5→Xray→VLESS→İnternet pipeline testi
//
// Xray'ın SOCKS5 proxy'si üzerinden HTTP isteği yapar ve sonucu döner.
// Bu test gVisor'dan BAĞIMSIZDIR — doğrudan Xray pipeline'ı test eder.
//
// Dönüş: "OK:<status_code>:<ilk_256_byte>" veya "FAIL:<adım>:<hata>"
// ═══════════════════════════════════════════════════════════════════

func TestConnection() string {
	// Adım 1: SOCKS5'e bağlan
	socksConn, err := net.DialTimeout("tcp", fmt.Sprintf("127.0.0.1:%d", xraySOCKSPort), 5*time.Second)
	if err != nil {
		return fmt.Sprintf("FAIL:socks_connect:%v", err)
	}
	defer socksConn.Close()
	socksConn.SetDeadline(time.Now().Add(10 * time.Second))

	// Adım 2: SOCKS5 auth
	socksConn.Write([]byte{0x05, 0x01, 0x00})
	authResp := make([]byte, 2)
	if _, err := io.ReadFull(socksConn, authResp); err != nil {
		return fmt.Sprintf("FAIL:socks_auth_read:%v", err)
	}
	if authResp[1] != 0x00 {
		return fmt.Sprintf("FAIL:socks_auth_rejected:%02x", authResp[1])
	}

	// Adım 3: SOCKS5 CONNECT → 1.1.1.1:80 (Cloudflare)
	// Binary IPv4: VER=5, CMD=1(CONNECT), RSV=0, ATYP=1(IPv4), ADDR(4 bytes), PORT(2 bytes)
	connectReq := []byte{
		0x05, 0x01, 0x00, 0x01, // VER, CMD CONNECT, RSV, ATYP IPv4
		0x01, 0x01, 0x01, 0x01, // 1.1.1.1
		0x00, 0x50, // port 80
	}
	socksConn.Write(connectReq)

	// Adım 4: SOCKS5 CONNECT yanıtı
	hdr := make([]byte, 4)
	if _, err := io.ReadFull(socksConn, hdr); err != nil {
		return fmt.Sprintf("FAIL:socks_connect_read:%v", err)
	}
	if hdr[1] != 0x00 {
		return fmt.Sprintf("FAIL:socks_connect_rejected:%02x", hdr[1])
	}
	// Consume the rest of the reply (ATYP-specific)
	switch hdr[3] {
	case 0x01:
		io.ReadFull(socksConn, make([]byte, 6)) // IPv4(4) + port(2)
	case 0x04:
		io.ReadFull(socksConn, make([]byte, 18)) // IPv6(16) + port(2)
	case 0x03:
		lb := make([]byte, 1)
		io.ReadFull(socksConn, lb)
		io.ReadFull(socksConn, make([]byte, int(lb[0])+2))
	}

	// Adım 5: HTTP GET
	httpReq := "GET / HTTP/1.1\r\nHost: 1.1.1.1\r\nConnection: close\r\n\r\n"
	socksConn.Write([]byte(httpReq))

	// Adım 6: Yanıt oku
	resp := make([]byte, 512)
	n, err := socksConn.Read(resp)
	if err != nil && n == 0 {
		return fmt.Sprintf("FAIL:http_read:%v", err)
	}
	// İlk satırı al (HTTP/1.1 301 Moved... gibi)
	firstLine := ""
	for i := 0; i < n; i++ {
		if resp[i] == '\n' {
			firstLine = string(resp[:i])
			break
		}
	}
	if firstLine == "" && n > 0 {
		firstLine = string(resp[:n])
		if len(firstLine) > 80 {
			firstLine = firstLine[:80]
		}
	}

	log.Printf("[TestConnection] OK: %s", firstLine)
	return fmt.Sprintf("OK:%s", firstLine)
}
