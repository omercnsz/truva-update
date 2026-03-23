package com.truva

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Truva VPN Servisi
 *
 * Yaşam döngüsü (her adım logcat'te izlenebilir):
 *
 *   BAĞLAN komutu gelir
 *     │
 *     ├─ ADIM 1: Veritabanından seçili proxy'yi al
 *     ├─ ADIM 2: Xray JSON config oluştur (minimal: SOCKS5 in + VLESS out)
 *     ├─ ADIM 3: Xray motorunu başlat (Go: xray-core + gVisor netstack)
 *     ├─ ADIM 4: TUN arayüzü oluştur (addDisallowedApplication ile döngü koruması)
 *     ├─ ADIM 5: TUN fd'yi Go netstack'e bağla
 *     └─ ✅ CONNECTED
 *
 *   KES komutu gelir
 *     │
 *     ├─ TUN kapat
 *     ├─ Foreground durdur
 *     └─ ✅ IDLE
 */
class MyVpnService : VpnService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var vpnInterface: ParcelFileDescriptor? = null
    private var connectJob: Job? = null
    private var isDisconnecting = false

    // ═══════════════════════════════════════════════════════════
    // Android Service Yaşam Döngüsü
    // ═══════════════════════════════════════════════════════════

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT    -> connect()
            ACTION_DISCONNECT -> disconnect()
        }
        return START_STICKY
    }

    /**
     * Android, VPN TUN kapatıldığında onRevoke() çağırır.
     * Default implementasyon stopSelf() çağırır → onDestroy() → süreç ölür.
     * Bunu boş bırakarak süreci canlı tutuyoruz.
     */
    override fun onRevoke() {
        Log.i(TAG, "onRevoke() — VPN iptal edildi")
        if (!isDisconnecting) {
            isDisconnecting = true
            try {
                connectJob?.cancel()
                connectJob = null
                // FD sahipliğini Java'dan ayır, Go kapatacak
                try { vpnInterface?.detachFd() } catch (_: Exception) {}
                vpnInterface = null
                try { Xray.Stop() } catch (_: Exception) {}
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            } catch (_: Exception) {}
            VpnStatusManager.update(VpnState.IDLE)
            isDisconnecting = false
        }
    }

    override fun onDestroy() {
        if (!isDisconnecting) {
            // FD sahipliğini Java'dan ayır, Go kapatacak
            try { vpnInterface?.detachFd() } catch (_: Exception) {}
            vpnInterface = null
            try { Xray.Stop() } catch (_: Exception) {}
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    // ═══════════════════════════════════════════════════════════
    // BAĞLAN — 5 adımlı modüler başlatma
    // ═══════════════════════════════════════════════════════════

    private fun connect() {
        VpnStatusManager.update(VpnState.CONNECTING)
        connectJob?.cancel()
        startForeground(NOTIFICATION_ID, buildNotification("Bağlanıyor..."))

        connectJob = serviceScope.launch(Dispatchers.IO) {
            try {
                // ── ADIM 1: Proxy listesi ve sıralama ──
                Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Log.i(TAG, "▸ ADIM 1/5: Proxy seçimi")
                val dao = AppDatabase.getDatabase(this@MyVpnService).appDao()
                val allProxies = dao.getAllProxiesList()
                if (allProxies.isEmpty()) {
                    fail("Sunucu yok — deep link ile sunucu ekleyin")
                    return@launch
                }

                // Settings'den routing modunu al
                val settings = dao.getSettingsFlow().firstOrNull() ?: SettingsEntity()
                val useSmartRouting = settings.isSmartRoutingEnabled && settings.routingMode != "standard"
                if (useSmartRouting) {
                    Log.i(TAG, "  \uD83D\uDDE7 Akıllı yönlendirme aktif: mod=${settings.routingMode}")
                } else {
                    Log.i(TAG, "  Standart mod (tüm trafik proxy)")
                }

                // Seçili olanı başa koy, geri kalanları arkasına ekle
                val selected = allProxies.find { it.isSelected }
                val tryOrder = if (selected != null) {
                    listOf(selected) + allProxies.filter { it.id != selected.id }
                } else {
                    allProxies
                }
                Log.i(TAG, "  ${tryOrder.size} sunucu denenecek")

                var lastError = ""

                for ((index, proxy) in tryOrder.withIndex()) {
                    // Placeholder sunucuları atla
                    if (proxy.ip.isBlank() || proxy.uuid.isBlank() || proxy.uuid.startsWith("your-uuid")) {
                        Log.w(TAG, "  ⏭ Atlandı (geçersiz): ${proxy.name}")
                        continue
                    }

                    Log.i(TAG, "━━━ Deneniyor [${index + 1}/${tryOrder.size}]: ${proxy.name} (${proxy.ip}:${proxy.port}) ━━━")
                    VpnStatusManager.update(VpnState.CONNECTING, "${proxy.name} deneniyor… [${index + 1}/${tryOrder.size}]")

                    // Önceki deneme varsa Xray'i temizle
                    if (index > 0) {
                        // FD sahipliğini Java'dan ayır (double-close engeli)
                        try { vpnInterface?.detachFd() } catch (_: Exception) {}
                        vpnInterface = null
                        try { Xray.Stop() } catch (_: Exception) {}
                        kotlinx.coroutines.delay(200)
                    }

                    // ── ADIM 2: Config ──
                    val config = if (useSmartRouting) {
                        Log.i(TAG, "  Config: SmartRouter.buildFullConfig (${settings.routingMode})")
                        com.truva.routing.SmartRouter.buildFullConfig(proxy, settings)
                    } else {
                        buildMinimalConfig(proxy)
                    }

                    // ── ADIM 3: Xray motoru ──
                    val initResult = Xray.Init(config)
                    if (initResult != 0) {
                        lastError = "Xray init: ${Xray.lastError ?: "bilinmeyen"}"
                        Log.w(TAG, "  ✗ $lastError")
                        continue
                    }

                    // TCP erişilebilirlik
                    val tcpOk = try {
                        kotlinx.coroutines.withTimeout(3000) {
                            val sock = java.net.Socket()
                            sock.connect(java.net.InetSocketAddress(proxy.ip, proxy.port), 3000)
                            sock.close()
                            true
                        }
                    } catch (e: Exception) {
                        lastError = "TCP bağlantı: ${e.message}"
                        Log.w(TAG, "  ✗ $lastError")
                        false
                    }
                    if (!tcpOk) continue

                    // Pipeline testi
                    val preTest = Xray.TestConnection()
                    if (!preTest.startsWith("OK:")) {
                        lastError = preTest.removePrefix("FAIL:")
                        Log.w(TAG, "  ✗ Pipeline: $lastError")
                        continue
                    }
                    Log.i(TAG, "  ✅ Pipeline OK")

                    // ── ADIM 4: TUN ──
                    // Önceki TUN varsa FD'yi ayır (Go zaten kapattı)
                    try { vpnInterface?.detachFd() } catch (_: Exception) {}
                    vpnInterface = null
                    val tun = createTun()
                    if (tun == null) {
                        fail("TUN oluşturulamadı — VPN izni verilmemiş olabilir")
                        return@launch
                    }
                    vpnInterface = tun

                    // ── ADIM 5: TUN → Netstack ──
                    val tunResult = Xray.SetTunFD(tun.fd)
                    if (tunResult != 0) {
                        fail("SetTunFD başarısız (kod: $tunResult)")
                        return@launch
                    }

                    // ── BAŞARI ──
                    // Bu sunucuyu seçili yap
                    dao.setActiveProxy(proxy.id)

                    Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    Log.i(TAG, "✅ VPN BAĞLANDI: ${proxy.name}")
                    Log.i(TAG, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                    VpnStatusManager.update(VpnState.CONNECTED, proxy.name)
                    return@launch  // Başarılı — döngüden çık
                }

                // Hiçbir sunucu çalışmadı
                fail("Tüm sunucular başarısız — son hata: $lastError")

            } catch (e: Exception) {
                fail("Beklenmeyen hata: ${e.javaClass.simpleName}: ${e.message}")
                Log.e(TAG, "connect() exception", e)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // KES — Temiz kapatma (IO thread'de çalışır)
    // ═══════════════════════════════════════════════════════════

    private fun disconnect() {
        if (isDisconnecting) return
        isDisconnecting = true
        VpnStatusManager.update(VpnState.DISCONNECTING)
        connectJob?.cancel()
        connectJob = null

        // Ağır işleri (Go Stop) IO thread'e taşı — main thread'i bloklamaz
        serviceScope.launch(Dispatchers.IO) {
            try {
                // FD sahipliğini Java'dan ayır — Go kapatacak
                try { vpnInterface?.detachFd() } catch (_: Exception) {}
                vpnInterface = null

                // Go: goroutine'ler durdur → cleanup (tunFile, linkEndpoint, ipStack, xray)
                try { Xray.Stop() } catch (_: Exception) {}

                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "disconnect() hatası: ${e.message}", e)
            } finally {
                VpnStatusManager.update(VpnState.IDLE)
                isDisconnecting = false
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // TUN Oluşturucu
    //
    // KRİTİK: addDisallowedApplication(packageName)
    // Bu olmadan Xray'in çıkış bağlantıları VPN TUN'dan geçer
    // → sonsuz döngü → bağlantı hatası
    // ═══════════════════════════════════════════════════════════

    private fun createTun(): ParcelFileDescriptor? {
        return try {
            Builder()
                .setSession("Truva VPN")
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0) // Tüm IPv4 trafiği
                // IPv6 satırları silindi!
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .addRoute("1.1.1.1", 32) // DNS rotası eklendi
                .addRoute("8.8.8.8", 32) // DNS rotası eklendi
                .setMtu(1280) // 1350'den 1280'e düşürüldü
                .addDisallowedApplication(packageName)
                .establish()
        } catch (e: Exception) {
            Log.e(TAG, "TUN oluşturma hatası: ${e.message}", e)
            null
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Minimal Xray Config
    //
    // En basit çalışan config:
    //   - Inbound:  SOCKS5 (127.0.0.1:10808) + sniffing
    //   - Outbound: VLESS (security/flow/network proxy'den gelir)
    //   - DNS:      1.1.1.1 / 8.8.8.8
    //   - Desteklenen: Reality, TLS, None / TCP, WS, gRPC
    //   - Mux:      YOK (xtls-rprx-vision ile uyumsuz)
    // ═══════════════════════════════════════════════════════════

    private fun buildMinimalConfig(proxy: ProxyEntity): String {
        val config = JSONObject()

        // Log
        config.put("log", JSONObject().put("loglevel", "warning"))

        // DNS — Xray'in sniffing ile tespit ettiği domain'leri çözmesi için
        config.put("dns", JSONObject()
            .put("servers", JSONArray()
                .put("1.1.1.1")
                .put("8.8.8.8")))

        // Inbound: SOCKS5 + sniffing (TLS SNI / HTTP Host tespiti)
        val sniffing = JSONObject()
            .put("enabled", true)
            .put("destOverride", JSONArray().put("http").put("tls").put("quic"))

        val inbound = JSONObject()
        inbound.put("tag", "socks-in")
        inbound.put("port", 10808)
        inbound.put("protocol", "socks")
        inbound.put("listen", "127.0.0.1")
        inbound.put("settings", JSONObject()
            .put("auth", "noauth")
            .put("udp", true))
        inbound.put("sniffing", sniffing)
        config.put("inbounds", JSONArray().put(inbound))

        // Outbound: VLESS (security/flow/network proxy'den gelir)
        val user = JSONObject()
            .put("id", proxy.uuid)
            .put("encryption", "none")
        if (proxy.flow.isNotBlank()) {
            user.put("flow", proxy.flow)
        }

        val vnext = JSONObject()
            .put("address", proxy.ip)
            .put("port", proxy.port)
            .put("users", JSONArray().put(user))

        val streamSettings = JSONObject()
            .put("network", proxy.network)
            .put("security", proxy.security)

        when (proxy.security) {
            "reality" -> {
                val realitySettings = JSONObject()
                    .put("serverName", proxy.sni)
                    .put("publicKey", proxy.publicKey)
                    .put("shortId", proxy.shortId)
                    .put("fingerprint", proxy.fingerprint)
                if (proxy.password.isNotBlank()) {
                    realitySettings.put("password", proxy.password)
                }
                streamSettings.put("realitySettings", realitySettings)
            }
            "tls" -> {
                val tlsSettings = JSONObject()
                    .put("serverName", proxy.sni)
                    .put("fingerprint", proxy.fingerprint)
                    .put("allowInsecure", false)
                streamSettings.put("tlsSettings", tlsSettings)
            }
            // "none" → ek ayar gerekmez
        }

        // WebSocket desteği
        if (proxy.network == "ws") {
            val wsSettings = JSONObject()
                .put("path", proxy.path)
            if (proxy.sni.isNotBlank()) {
                wsSettings.put("headers", JSONObject().put("Host", proxy.sni))
            }
            streamSettings.put("wsSettings", wsSettings)
        } else if (proxy.network == "grpc") {
            streamSettings.put("grpcSettings", JSONObject().put("serviceName", proxy.path))
        }

        val proxyOutbound = JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vless")
            .put("settings", JSONObject()
                .put("vnext", JSONArray().put(vnext))
                .put("udp", true))
            .put("streamSettings", streamSettings)

        val directOutbound = JSONObject()
            .put("tag", "direct")
            .put("protocol", "freedom")

        config.put("outbounds", JSONArray().put(proxyOutbound).put(directOutbound))

        // Routing: tüm trafiği proxy'ye yönlendir
        config.put("routing", JSONObject()
            .put("domainStrategy", "IPIfNonMatch")
            .put("rules", JSONArray()
                .put(JSONObject()
                    .put("type", "field")
                    .put("port", "53")
                    .put("outboundTag", "proxy"))
                .put(JSONObject()
                    .put("type", "field")
                    .put("network", "tcp,udp")
                    .put("outboundTag", "proxy"))))

        return config.toString()
    }

    // ═══════════════════════════════════════════════════════════
    // Yardımcılar
    // ═══════════════════════════════════════════════════════════

    private fun fail(message: String) {
        Log.e(TAG, "  ❌ $message")
        VpnStatusManager.error(message)
    }

    private fun buildNotification(text: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Truva VPN", NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, MyVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val pendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Truva VPN")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "KES", pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "TruvaVPN"
        const val ACTION_CONNECT = "com.truva.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.truva.vpn.DISCONNECT"
        private const val CHANNEL_ID = "truva_vpn_channel"
        private const val NOTIFICATION_ID = 1
    }
}
