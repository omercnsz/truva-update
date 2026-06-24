package com.truva.gamemode

import android.util.Log
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Yerel SOCKS5 CONNECT proxy'si — Nitro Oyun (Oyun Modu) için DPI-bypass uygular.
 *
 * Akış: TUN → Xray (tun2socks) → Xray socks-outbound → BU PROXY → gerçek sunucu.
 *   Xray her bağlantıyı SOCKS5 ile bu proxy'ye verir; proxy hedefe çıkış soketi
 *   açar, ilk istemci→sunucu chunk'ı (TLS ClientHello) [strategy]'ye göre manipüle
 *   edilir, sonra çift yönlü pump.
 *
 * Manipülasyon (root'suz, akış seviyesi — TruvaRun fragmentasyon dalı + OOB):
 *   - SNI_MID_SPLIT: ClientHello SNI ortasından 2 TCP segmentine bölünür.
 *   - TLS_REC_SPLIT: tek TLS record 2 record'a bölünür.
 *   - OOB_SPLIT (en güçlü root'suz): SNI ortasında bölüp araya bir OOB (urgent)
 *     bayt sokar. DPI baytı inline sayıp SNI imzasını kaçırır; sunucu OOBINLINE
 *     kapalı olduğu için baytı akıştan çıkarıp geçerli ClientHello'yu alır.
 *
 * NOT — overlapping-seq sahte paket (masaüstü ttl-fake-disorder) root ister,
 * normal sokette yapılamaz; OOB bunun root'suz en yakın güvenli alternatifidir.
 *
 * `protect()` GEREKMEZ: Truva paketi VPN TUN'undan addDisallowedApplication ile
 * hariç olduğu için çıkış soketleri tüneli bypass eder.
 */
class DesyncProxy(
    private val listenPort: Int,
    private val strategy: Strategy = Strategy.OOB_SPLIT,
) {
    enum class Strategy { SNI_MID_SPLIT, TLS_REC_SPLIT, OOB_SPLIT }

    companion object {
        private const val TAG = "TruvaDesync"
        private const val FIRST_CHUNK_MAX = 16 * 1024
        private const val BUF = 32 * 1024
        private const val TLS_REC_FIRST_LEN = 1

        /** Bu alt-dizgileri içeren SNI'lara desync UYGULANMAZ (anti-cheat ban riski). */
        private val ANTI_CHEAT_HOSTS = listOf(
            "riotgames", "vanguard", "easyanticheat", "eac-", "battleye",
            "faceit", "anticheat",
        )
    }

    private val running = AtomicBoolean(false)
    private val pool = Executors.newCachedThreadPool()
    private var server: ServerSocket? = null

    val port: Int get() = server?.localPort ?: listenPort

    fun start() {
        if (running.getAndSet(true)) return
        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), listenPort))
        server = ss
        pool.execute {
            while (running.get()) {
                val client = try { ss.accept() } catch (e: Exception) { break }
                pool.execute { handle(client) }
            }
        }
        Log.i(TAG, "DesyncProxy started on 127.0.0.1:${ss.localPort} strategy=$strategy")
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        try { server?.close() } catch (_: Exception) {}
        pool.shutdownNow()
    }

    private fun handle(client: Socket) {
        var upstream: Socket? = null
        try {
            client.tcpNoDelay = true
            val cin = DataInputStream(client.getInputStream())
            val cout = client.getOutputStream()

            val target = socks5Handshake(cin, cout) ?: run { client.close(); return }

            upstream = Socket()
            upstream.tcpNoDelay = true
            // OOB baytının sunucuda akıştan çıkarılması için OOBINLINE KAPALI olmalı (varsayılan).
            try { upstream.oobInline = false } catch (_: Exception) {}
            upstream.connect(InetSocketAddress(target.host, target.port), 10_000)

            // SOCKS5 success reply.
            cout.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            cout.flush()

            val uin = upstream.getInputStream()
            val bypass = ANTI_CHEAT_HOSTS.any { target.host.contains(it, ignoreCase = true) }

            val up = upstream
            pool.execute { pumpClientToUpstream(cin, up, bypass) }
            pump(uin, cout)
        } catch (e: Exception) {
            Log.d(TAG, "handle: ${e.message}")
        } finally {
            try { client.close() } catch (_: Exception) {}
            try { upstream?.close() } catch (_: Exception) {}
        }
    }

    private fun pumpClientToUpstream(cin: InputStream, upstream: Socket, bypass: Boolean) {
        val uout = upstream.getOutputStream()
        try {
            val first = ByteArray(FIRST_CHUNK_MAX)
            val n = cin.read(first)
            if (n <= 0) return
            val chunk = first.copyOf(n)
            if (bypass) { uout.write(chunk); uout.flush() } else applyDesync(chunk, upstream, uout)
            pump(cin, uout)
        } catch (e: Exception) {
            Log.d(TAG, "c->u: ${e.message}")
        } finally {
            try { uout.close() } catch (_: Exception) {}
        }
    }

    private fun applyDesync(chunk: ByteArray, upstream: Socket, uout: OutputStream) {
        when (strategy) {
            Strategy.SNI_MID_SPLIT -> {
                val split = TlsClientHello.sniMidSplitIndex(chunk)
                if (split != null) {
                    uout.write(chunk, 0, split); uout.flush()
                    uout.write(chunk, split, chunk.size - split); uout.flush()
                    return
                }
            }
            Strategy.TLS_REC_SPLIT -> {
                val rewritten = TlsClientHello.tlsRecSplit(chunk, TLS_REC_FIRST_LEN)
                if (rewritten != null) { uout.write(rewritten); uout.flush(); return }
            }
            Strategy.OOB_SPLIT -> {
                val split = TlsClientHello.sniMidSplitIndex(chunk)
                if (split != null) {
                    // part1 → OOB(urgent) junk byte → part2.
                    uout.write(chunk, 0, split); uout.flush()
                    var oobOk = false
                    try { upstream.sendUrgentData(0x00); oobOk = true } catch (_: Exception) {}
                    uout.write(chunk, split, chunk.size - split); uout.flush()
                    if (!oobOk) Log.d(TAG, "sendUrgentData başarısız — düz split'e düşüldü")
                    return
                }
            }
        }
        // Uygulanamadı → passthrough.
        uout.write(chunk); uout.flush()
    }

    private fun pump(input: InputStream, output: OutputStream) {
        val buf = ByteArray(BUF)
        try {
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                output.write(buf, 0, n)
                output.flush()
            }
        } catch (_: Exception) {
        } finally {
            try { output.close() } catch (_: Exception) {}
        }
    }

    private fun socks5Handshake(cin: DataInputStream, cout: OutputStream): Target? {
        val ver = cin.read()
        if (ver != 0x05) return null
        val nm = cin.read()
        if (nm < 0) return null
        val methods = ByteArray(nm); cin.readFully(methods)
        cout.write(byteArrayOf(0x05, 0x00)); cout.flush()

        if (cin.read() != 0x05) return null
        val cmd = cin.read()
        if (cmd != 0x01) {
            cout.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0)); cout.flush()
            return null
        }
        cin.read() // RSV
        val atyp = cin.read()
        val host: String = when (atyp) {
            0x01 -> { val a = ByteArray(4); cin.readFully(a); InetAddress.getByAddress(a).hostAddress!! }
            0x03 -> { val len = cin.read(); val d = ByteArray(len); cin.readFully(d); String(d, Charsets.UTF_8) }
            0x04 -> { val a = ByteArray(16); cin.readFully(a); InetAddress.getByAddress(a).hostAddress!! }
            else -> return null
        }
        val p1 = cin.read(); val p2 = cin.read()
        val port = (p1 shl 8) or p2
        return Target(host, port)
    }

    private data class Target(val host: String, val port: Int)
}
