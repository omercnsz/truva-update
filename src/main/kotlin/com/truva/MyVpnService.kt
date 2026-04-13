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
import com.truva.nidg.NidgEngine
import com.truva.nidg.NidgVpnManager
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
    private var isNidgMode = false  // NIDG analiz modunda mı?

    // ═══════════════════════════════════════════════════════════
    // Android Service Yaşam Döngüsü
    // ═══════════════════════════════════════════════════════════

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT         -> connect()
            ACTION_DISCONNECT      -> disconnect()
            ACTION_NIDG_CONNECT    -> connectNidg()
            ACTION_NIDG_DISCONNECT -> disconnectNidg()
            ACTION_GAME_MODE_CONNECT    -> connectGameMode()
            ACTION_GAME_MODE_DISCONNECT -> disconnectGameMode()
            ACTION_NITRO_DPI_CONNECT    -> connectNitroDpi()
            ACTION_NITRO_DPI_DISCONNECT -> disconnectNitroDpi()
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
                // NIDG: Analiz motorunu durdur
                try { NidgEngine.stop() } catch (_: Exception) {}
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
            stopSelf()
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

                    // ── NIDG: Ağ analiz motorunu başlat ──
                    try {
                        val nidgSettings = dao.getSettingsFlow().firstOrNull() ?: SettingsEntity()
                        if (nidgSettings.isNidgEnabled) {
                            NidgEngine.start(tun.fd)
                            Log.i(TAG, "  📊 NIDG Engine başlatıldı")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "  NIDG başlatılamadı: ${e.message}")
                    }

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
                // NIDG: Analiz motorunu durdur
                try { NidgEngine.stop() } catch (_: Exception) {}

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
                // VPN veya Oyun Modu kapandığında İş Profilinde internet yasağını geri aç
                try {
                    val sandbox = com.truva.sandbox.TruvaSandbox.getInstance(this@MyVpnService)
                    if (sandbox.isProfileOwner) {
                        sandbox.workProfileManager.setVpnLockdown(true)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "VPN Lockdown geri açılamadı: ${e.message}")
                }

                VpnStatusManager.update(VpnState.IDLE)
                isDisconnecting = false
                stopSelf()
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

    /**
     * UNIFIED TUN — Tüm modlar (Normal, Oyun, NIDG) bu ana metod üzerinden TUN oluşturur.
     * Bu sayede İş Profili / Lockdown politikaları ile %100 uyum sağlanır.
     */
    private fun createTun(allowedApps: List<String>? = null): ParcelFileDescriptor? {
        return try {
            val builder = Builder()
                .setSession("Truva VPN") // Tüm modlarda AYNI oturum adını kullan
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0) // Full-Tunnel (Work Profile Lockdown uyumluluğu için şart)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")
                .addRoute("1.1.1.1", 32) // DNS trafiğini tünel içine zorla (Android resolver bypass'ı önler)
                .addRoute("8.8.8.8", 32)
                .addAddress("fd00::1", 128) // IPv6 Sızıntısını önlemek için sanal adres
                .addRoute("::", 0) // Tüm IPv6 trafiğini tünel içine çek
                .setMtu(1200) // TT DPI tamponlarını şaşırtmak için MTU düşürüldü (DPI Desync)

            if (!allowedApps.isNullOrEmpty()) {
                for (app in allowedApps) {
                    if (app.isNotBlank()) {
                        try {
                            builder.addAllowedApplication(app.trim())
                        } catch (e: Exception) {
                            Log.w(TAG, "İzin verilen uygulama eklenemedi: $app")
                        }
                    }
                }
            } else {
                builder.addDisallowedApplication(packageName)
            }

            builder.establish()
        } catch (e: Exception) {
            Log.e(TAG, "TUN oluşturma hatası: ${e.message}", e)
            null
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Minimal Xray Config
    // ═══════════════════════════════════════════════════════════

    private fun buildMinimalConfig(proxy: ProxyEntity): String {
        val config = JSONObject()
        config.put("log", JSONObject().put("loglevel", "warning"))
        config.put("dns", JSONObject()
            .put("servers", getDnsServers(true)))

        val sniffing = JSONObject()
            .put("enabled", true)
            .put("destOverride", JSONArray().put("http").put("tls").put("quic"))

        val inbound = JSONObject()
            .put("tag", "socks-in")
            .put("port", 10808)
            .put("protocol", "socks")
            .put("listen", "127.0.0.1")
            .put("settings", JSONObject().put("auth", "noauth").put("udp", true))
            .put("sniffing", sniffing)
        config.put("inbounds", JSONArray().put(inbound))

        val proxyOutbound = buildProxyOutbound(proxy)
        config.put("outbounds", JSONArray().put(proxyOutbound))

        config.put("routing", JSONObject()
            .put("domainStrategy", "IPIfNonMatch")
            .put("rules", JSONArray()
                .put(JSONObject()
                    .put("type", "field")
                    .put("network", "tcp,udp")
                    .put("outboundTag", "proxy"))))

        return config.toString()
    }

    private fun buildProxyOutbound(proxy: ProxyEntity): JSONObject {
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
                if (proxy.password.isNotBlank()) realitySettings.put("password", proxy.password)
                streamSettings.put("realitySettings", realitySettings)
            }
            "tls" -> {
                val tlsSettings = JSONObject()
                    .put("serverName", proxy.sni)
                    .put("fingerprint", proxy.fingerprint)
                    .put("allowInsecure", false)
                streamSettings.put("tlsSettings", tlsSettings)
            }
        }

        if (proxy.network == "ws") {
            val wsSettings = JSONObject().put("path", proxy.path)
            if (proxy.sni.isNotBlank()) wsSettings.put("headers", JSONObject().put("Host", proxy.sni))
            streamSettings.put("wsSettings", wsSettings)
        } else if (proxy.network == "grpc") {
            streamSettings.put("grpcSettings", JSONObject().put("serviceName", proxy.path))
        }

        return JSONObject()
            .put("tag", "proxy")
            .put("protocol", "vless")
            .put("settings", JSONObject()
                .put("vnext", JSONArray().put(vnext))
                .put("udp", true))
            .put("streamSettings", streamSettings)
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
        const val ACTION_NIDG_CONNECT = "com.truva.nidg.CONNECT"
        const val ACTION_NIDG_DISCONNECT = "com.truva.nidg.DISCONNECT"
        const val ACTION_GAME_MODE_CONNECT = "com.truva.game.CONNECT"
        const val ACTION_GAME_MODE_DISCONNECT = "com.truva.game.DISCONNECT"
        const val ACTION_NITRO_DPI_CONNECT = "com.truva.nitrodpi.CONNECT"
        const val ACTION_NITRO_DPI_DISCONNECT = "com.truva.nitrodpi.DISCONNECT"
        private const val CHANNEL_ID = "truva_vpn_channel"
        private const val NIDG_CHANNEL_ID = "truva_nidg_channel"
        private const val NOTIFICATION_ID = 1
        private const val NIDG_NOTIFICATION_ID = 1 // UNIFIED ID (Android 14+ uyumu için)
        private const val GAME_MODE_NOTIFICATION_ID = 1 // UNIFIED ID (Android 14+ uyumu için)
    }

    // ═══════════════════════════════════════════════════════════
    // NIDG Analiz VPN — Şeffaf Mod (Freedom Outbound)
    // ═══════════════════════════════════════════════════════════

    private fun connectNidg() {
        // Ana VPN aktifse önce kes
        if (vpnInterface != null && !isNidgMode) {
            Log.w(TAG, "Ana VPN aktif — NIDG moduna geçilemez")
            NidgVpnManager.updateState(NidgVpnManager.NidgVpnState.MAIN_VPN_ACTIVE)
            return
        }

        NidgVpnManager.updateState(NidgVpnManager.NidgVpnState.CONNECTING)
        isNidgMode = true
        connectJob?.cancel()
        startForeground(NIDG_NOTIFICATION_ID, buildNidgNotification("Bağlanıyor..."))

        connectJob = serviceScope.launch(Dispatchers.IO) {
            try {
                Log.i(TAG, "━━━ NIDG Analiz VPN başlatılıyor ━━━")

                // Xray'i freedom config ile başlat
                val config = buildNidgTransparentConfig()
                val initResult = Xray.Init(config)
                if (initResult != 0) {
                    Log.e(TAG, "NIDG Xray init hatası: ${Xray.lastError}")
                    NidgVpnManager.updateState(NidgVpnManager.NidgVpnState.IDLE)
                    return@launch
                }

                // TUN oluştur
                try { vpnInterface?.detachFd() } catch (_: Exception) {}
                vpnInterface = null
                val tun = createTun()
                if (tun == null) {
                    Log.e(TAG, "NIDG TUN oluşturulamadı")
                    try { Xray.Stop() } catch (_: Exception) {}
                    NidgVpnManager.updateState(NidgVpnManager.NidgVpnState.IDLE)
                    return@launch
                }
                vpnInterface = tun

                // TUN → Netstack
                val tunResult = Xray.SetTunFD(tun.fd)
                if (tunResult != 0) {
                    Log.e(TAG, "NIDG SetTunFD hatası: $tunResult")
                    try { Xray.Stop() } catch (_: Exception) {}
                    NidgVpnManager.updateState(NidgVpnManager.NidgVpnState.IDLE)
                    return@launch
                }

                // NIDG motorunu başlat
                NidgEngine.start(tun.fd)

                Log.i(TAG, "━━━ NIDG Analiz VPN AKTİF ━━━")
                NidgVpnManager.updateState(NidgVpnManager.NidgVpnState.ACTIVE)

                // Bildirimi güncelle
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NIDG_NOTIFICATION_ID, buildNidgNotification("Ağ analizi aktif — Mobil Veri"))
                }

            } catch (e: Exception) {
                Log.e(TAG, "NIDG connect hatası: ${e.message}", e)
                NidgVpnManager.updateState(NidgVpnManager.NidgVpnState.IDLE)
            }
        }
    }

    private fun disconnectNidg() {
        if (!isNidgMode) return
        Log.i(TAG, "NIDG Analiz VPN durduruluyor...")

        connectJob?.cancel()
        connectJob = null

        serviceScope.launch(Dispatchers.IO) {
            try {
                NidgEngine.stop()
                try { vpnInterface?.detachFd() } catch (_: Exception) {}
                vpnInterface = null
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
                Log.e(TAG, "NIDG disconnect hatası: ${e.message}", e)
            } finally {
                isNidgMode = false
                NidgVpnManager.updateState(NidgVpnManager.NidgVpnState.IDLE)
                Log.i(TAG, "NIDG Analiz VPN durduruldu")
            }
        }
    }

    /**
     * NIDG Şeffaf Config — Tüm trafik freedom (direkt internet) üzerinden geçer.
     * Proxy yok. Sadece TUN'dan okuma için.
     */
    private fun buildNidgTransparentConfig(): String {
        val config = JSONObject()
        config.put("log", JSONObject().put("loglevel", "warning"))
        config.put("dns", JSONObject()
            .put("servers", getDnsServers(true)))

        // Inbound: SOCKS5
        val sniffing = JSONObject()
            .put("enabled", true)
            .put("destOverride", JSONArray().put("http").put("tls").put("quic"))
        val inbound = JSONObject()
            .put("tag", "socks-in")
            .put("port", 10808)
            .put("protocol", "socks")
            .put("listen", "127.0.0.1")
            .put("settings", JSONObject().put("auth", "noauth").put("udp", true))
            .put("sniffing", sniffing)
        config.put("inbounds", JSONArray().put(inbound))

        // Outbound: SADECE freedom (direkt internet, proxy yok)
        val freedomOutbound = JSONObject()
            .put("tag", "direct")
            .put("protocol", "freedom")
            .put("settings", JSONObject().put("domainStrategy", "UseIP"))
        config.put("outbounds", JSONArray().put(freedomOutbound))

        // Routing: Tüm trafiği freedom'a yönlendir
        config.put("routing", JSONObject()
            .put("domainStrategy", "IPIfNonMatch")
            .put("rules", JSONArray()
                .put(JSONObject()
                    .put("type", "field")
                    .put("network", "tcp,udp")
                    .put("outboundTag", "direct"))))

        return config.toString()
    }

    /**
     * NIDG sabit bildirimi — başlat/durdur aksiyonları ile.
     */
    private fun buildNidgNotification(text: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NIDG_CHANNEL_ID, "Truva Ağ Analizi", NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                description = "NIDG ağ analiz motoru bildirimleri"
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, MyVpnService::class.java).apply {
            action = ACTION_NIDG_DISCONNECT
        }
        val stopPending = PendingIntent.getService(
            this, 100, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Uygulama açma intent'i
        val openIntent = packageManager.getLaunchIntentForPackage(packageName)
        val openPending = if (openIntent != null) {
            PendingIntent.getActivity(this, 101, openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        } else null

        val builder = NotificationCompat.Builder(this, NIDG_CHANNEL_ID)
            .setContentTitle("📊 Ağ Analizi")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "DURDUR", stopPending)
            .setOngoing(true)

        if (openPending != null) {
            builder.setContentIntent(openPending)
        }

        return builder.build()
    }

    // ═══════════════════════════════════════════════════════════
    // Oyun Modu — Düşük Ping (Freedom Outbound + Google DNS)
    // ═══════════════════════════════════════════════════════════

    private fun connectGameMode() {
        if (vpnInterface != null) {
            Log.w(TAG, "VPN zaten aktif — Oyun Modu başlatılamaz")
            return
        }

        VpnStatusManager.update(VpnState.CONNECTING, "Oyun Modu Hazırlanıyor...")
        connectJob?.cancel()
        startForeground(NOTIFICATION_ID, buildGameModeNotification("Bağlanıyor..."))

        connectJob = serviceScope.launch(Dispatchers.IO) {
            try {
                // SİSTEM GECİKMESİ: Önceki oturumun temizlenmesi ve sistemin yeni TUN'u kabul etmesi için bekleme
                kotlinx.coroutines.delay(1800)
                Log.i(TAG, "━━━ Oyun Modu başlatılıyor ━━━")

                // ADIM 1: Proxy'yi bul (DNS'i proxy üzerinden geçirmek için)
                val dao = AppDatabase.getDatabase(this@MyVpnService).appDao()
                val activeProxy = dao.getAllProxiesList().find { it.isSelected }
                    ?: dao.getAllProxiesList().firstOrNull()

                // Xray'i hibrit (freedom + dns-via-proxy) config ile başlat
                val config = buildGameModeConfig(activeProxy)
                val initResult = Xray.Init(config)
                if (initResult != 0) {
                    fail("Oyun Modu Xray init hatası: ${Xray.lastError}")
                    return@launch
                }

                // TUN oluştur
                try { vpnInterface?.detachFd() } catch (_: Exception) {}
                vpnInterface = null
                
                val tun: ParcelFileDescriptor?
                try {
                    tun = createTun()
                } catch (e: Exception) {
                    fail("Oyun Modu TUN Hatası: ${e.message}")
                    return@launch
                }
                
                if (tun == null) {
                    fail("TUN null: VPN izni eksik veya sistem bağlamadı")
                    return@launch
                }
                vpnInterface = tun

                // TUN → Netstack
                val tunResult = Xray.SetTunFD(tun.fd)
                if (tunResult != 0) {
                    fail("Oyun Modu SetTunFD hatası: $tunResult")
                    return@launch
                }

                Log.i(TAG, "━━━ Oyun Modu AKTİF ━━━")
                VpnStatusManager.update(VpnState.GAMING, "Nitro Geçit Aktif")

                // Bildirimi güncelle
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, buildGameModeNotification("Oyun Modu Aktif — Düşük Ping"))
                }

            } catch (e: Exception) {
                fail("Oyun Modu bağlantı hatası: ${e.message}")
            }
        }
    }

    private fun disconnectGameMode() {
        Log.i(TAG, "Oyun Modu durduruluyor...")
        disconnect() // Mevcut temizleme mantığını kullan
    }

    private fun buildGameModeConfig(proxy: ProxyEntity?): String {
        val config = JSONObject()
        config.put("log", JSONObject().put("loglevel", "warning"))
        
        // DNS: Google & Cloudflare DoH (Xray içinden Proxy'ye yönlendirilecek)
        val dao = AppDatabase.getDatabase(this@MyVpnService).appDao()
        val isDoh = try {
            val s = kotlinx.coroutines.runBlocking { dao.getSettingsFlow().firstOrNull() }
            s?.isDohEnabled ?: true
        } catch (_: Exception) { true }

        config.put("dns", JSONObject()
            .put("servers", getDnsServers(isDoh)))

        // Inbound: SOCKS5
        val sniffing = JSONObject()
            .put("enabled", true)
            .put("destOverride", JSONArray().put("http").put("tls").put("quic"))
        val inbound = JSONObject()
            .put("tag", "socks-in")
            .put("port", 10808)
            .put("protocol", "socks")
            .put("listen", "127.0.0.1")
            .put("settings", JSONObject().put("auth", "noauth").put("udp", true))
            .put("sniffing", sniffing)
        config.put("inbounds", JSONArray().put(inbound))

        val outbounds = JSONArray()
        
        // Outbound 1: Freedom (Direkt internet, en düşük ping)
        val freedomOutbound = JSONObject()
            .put("tag", "direct")
            .put("protocol", "freedom")
            .put("settings", JSONObject().put("domainStrategy", "UseIP"))
        outbounds.put(freedomOutbound)

        // Outbound 2: Proxy (Sadece DNS sorguları için)
        if (proxy != null) {
            try {
                val proxyJson = buildProxyOutbound(proxy)
                outbounds.put(proxyJson)
            } catch (e: Exception) {
                Log.w(TAG, "Oyun Modu Proxy eklenemedi: ${e.message}")
            }
        }
        
        config.put("outbounds", outbounds)

        // Routing Rules
        val rules = JSONArray()
        
        // Kural 1: DNS trafiğini (Port 53) Proxy üzerinden gönder (sansür aşımı için)
        if (proxy != null) {
            rules.put(JSONObject()
                .put("type", "field")
                .put("port", 53)
                .put("outboundTag", "proxy"))
        }

        // Kural 2: Geri kalan tüm trafiği doğrudan çıkışa yönlendir
        rules.put(JSONObject()
            .put("type", "field")
            .put("network", "tcp,udp")
            .put("outboundTag", "direct"))

        config.put("routing", JSONObject()
            .put("domainStrategy", "IPIfNonMatch")
            .put("rules", rules))

        return config.toString()
    }

    private fun buildGameModeNotification(text: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Truva VPN", NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, MyVpnService::class.java).apply {
            action = ACTION_GAME_MODE_DISCONNECT
        }
        val stopPending = PendingIntent.getService(
            this, 200, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🎮 Truva Oyun Modu")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "KAPAT", stopPending)
            .setOngoing(true)
            .build()
    }
    
    // ═══════════════════════════════════════════════════════════
    // NİTRO OYUN — MİNİMUM PİNG (LOKAL DPI BYPASS)
    // ═══════════════════════════════════════════════════════════

    private fun connectNitroDpi() {
        if (vpnInterface != null) {
            Log.w(TAG, "VPN zaten aktif — Nitro Oyun başlatılamaz")
            return
        }

        VpnStatusManager.update(VpnState.CONNECTING, "Nitro Oyun Hazırlanıyor...")
        connectJob?.cancel()
        startForeground(NOTIFICATION_ID, buildNitroDpiNotification("Bağlanıyor..."))

        connectJob = serviceScope.launch(Dispatchers.IO) {
            try {
                kotlinx.coroutines.delay(1800)
                Log.i(TAG, "━━━ Nitro Oyun (Lokal DPI) başlatılıyor ━━━")

                val dao = AppDatabase.getDatabase(this@MyVpnService).appDao()
                val settings = dao.getSettingsFlow().firstOrNull() ?: SettingsEntity()

                val config = buildNitroDpiConfig(settings.isDohEnabled)
                val initResult = Xray.Init(config)
                if (initResult != 0) {
                    fail("Nitro Oyun Xray init hatası: ${Xray.lastError}")
                    return@launch
                }

                try { vpnInterface?.detachFd() } catch (_: Exception) {}
                vpnInterface = null
                
                val allowedApps = if (settings.nitroDpiAppMode == "selected" && settings.nitroDpiApps.isNotBlank()) {
                    settings.nitroDpiApps.split(",").map { it.trim() }
                } else null

                val tun: ParcelFileDescriptor?
                try {
                    tun = createTun(allowedApps)
                } catch (e: Exception) {
                    fail("Nitro Oyun TUN Hatası: ${e.message}")
                    return@launch
                }
                
                if (tun == null) {
                    fail("TUN null: VPN izni eksik")
                    return@launch
                }
                vpnInterface = tun

                val tunResult = Xray.SetTunFD(tun.fd)
                if (tunResult != 0) {
                    fail("Nitro Oyun SetTunFD hatası: $tunResult")
                    return@launch
                }

                Log.i(TAG, "━━━ Nitro Oyun (Lokal DPI) AKTİF ━━━")
                VpnStatusManager.update(VpnState.NITRO_DPI, "Nitro Oyun (0 Ping) Aktif")

                // Bildirimi güncelle
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, buildNitroDpiNotification("Nitro Oyun Aktif — Kesintisiz Bağlantı"))
                }

            } catch (e: Exception) {
                fail("Nitro Oyun bağlantı hatası: ${e.message}")
            }
        }
    }

    private fun disconnectNitroDpi() {
        Log.i(TAG, "Nitro Oyun durduruluyor...")
        disconnect()
    }

    private fun buildNitroDpiConfig(isDoh: Boolean): String {
        val config = JSONObject()
        config.put("log", JSONObject().put("loglevel", "warning"))
        
        config.put("dns", JSONObject().apply {
            put("servers", getDnsServers(isDoh).apply {
                put("fakedns")
            })
            put("queryStrategy", "UseIPv4")
        })

        val sniffing = JSONObject()
            .put("enabled", true)
            .put("destOverride", JSONArray().put("http").put("tls").put("quic"))
            .put("metadataOnly", false)
            .put("routeOnly", true)

        val inbound = JSONObject()
            .put("tag", "socks-in")
            .put("port", 10808)
            .put("protocol", "socks")
            .put("listen", "127.0.0.1")
            .put("settings", JSONObject().put("auth", "noauth").put("udp", true))
            .put("sniffing", sniffing)
        config.put("inbounds", JSONArray().put(inbound))

        val outbounds = JSONArray()
        
        // Outbound 1: Fragmented Direct (TCP)
        val freedomOutbound = JSONObject()
            .put("tag", "direct-fragment")
            .put("protocol", "freedom")
            .put("settings", JSONObject()
                .put("domainStrategy", "UseIP")
                .put("fragment", JSONObject()
                    .put("packets", "all")
                    .put("length", "1-10")
                    .put("interval", "5-30")
                )
            )
        outbounds.put(freedomOutbound)

        // Outbound 2: Block (UDP 443 / QUIC)
        val blockOutbound = JSONObject()
            .put("tag", "block")
            .put("protocol", "blackhole")
        outbounds.put(blockOutbound)

        config.put("outbounds", outbounds)
        
        val rules = JSONArray()
        
        // Kural 1: QUIC Engelle (UDP 443) -> Uygulamayı TCP'ye zorla
        rules.put(JSONObject()
            .put("type", "field")
            .put("port", 443)
            .put("network", "udp")
            .put("outboundTag", "block"))

        // Kural 2: Geri kalan her şeyi parçalayarak gönder
        rules.put(JSONObject()
            .put("type", "field")
            .put("network", "tcp,udp")
            .put("outboundTag", "direct-fragment"))

        config.put("routing", JSONObject()
            .put("domainStrategy", "IPIfNonMatch")
            .put("rules", rules))

        return config.toString()
    }

    private fun buildNitroDpiNotification(text: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Truva VPN", NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }

        val stopIntent = Intent(this, MyVpnService::class.java).apply {
            action = ACTION_NITRO_DPI_DISCONNECT
        }
        val stopPending = PendingIntent.getService(
            this, 300, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⚡ Nitro Oyun")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "KAPAT", stopPending)
            .setOngoing(true)
            .build()
    }

    private fun getDnsServers(isDoh: Boolean): JSONArray {
        return JSONArray().apply {
            if (isDoh) {
                put("https://1.1.1.1/dns-query")
                put("https://8.8.8.8/dns-query")
                put("https://9.9.9.9/dns-query")
                put("https://dns.nextdns.io/dns-query")
            }
            put("1.1.1.1")
            put("8.8.8.8")
            put("9.9.9.9")
        }
    }
}
