package com.truva

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.truva.sandbox.TruvaSandbox
import com.truva.spoofing.RegionProfile
import com.truva.spoofing.SpoofingCoordinator
import com.truva.spoofing.SpoofingStatus
import com.truva.ui.IpInfo
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject

/** ADB durumu takibi için veri sınıfı */
data class AdbStatus(val isConnected: Boolean = false)

/** Uygulama koruma bilgisi */
data class AppProtectionInfo(
    val name: String,
    val packageName: String,
    val userId: Int,
    val isProtected: Boolean
)

/**
 * TruvaViewModel — UI durum yönetimi
 *
 * Sorumluluklar:
 * 1. Proxy seçimi (sunucu listesi)
 * 2. VPN bağlantı durumu (VpnStatusManager'dan okur)
 * 3. Ayarlar (settings tablosu)
 * 4. İleri özellikler (spoofing, work profile) — VPN'den BAĞIMSIZ
 */
class TruvaViewModel(
    private val dao: AppDao,
    private val simDao: SimProtectionDao,
    private val app: Application? = null
) : ViewModel() {

    // ═══════════════════════════════════════════
    // KÖPRÜ (Work Profile Sync)
    // ═══════════════════════════════════════════

    /** Bu Truva kopyası İş Profilinde mi çalışıyor? */
    private val _isInWorkProfile = MutableStateFlow(false)
    val isInWorkProfile: StateFlow<Boolean> = _isInWorkProfile.asStateFlow()

    private val _bridgeStatus = MutableStateFlow(false)
    val bridgeStatus: StateFlow<Boolean> = _bridgeStatus.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    // ═══════════════════════════════════════════
    // NAVİGASYON (Sekmeler)
    // ═══════════════════════════════════════════

    enum class TruvaTab { DASHBOARD, GAMING, NITRO_DPI }
    private val _selectedTab = MutableStateFlow(TruvaTab.DASHBOARD)
    val selectedTab: StateFlow<TruvaTab> = _selectedTab.asStateFlow()

    fun selectTab(tab: TruvaTab) {
        _selectedTab.value = tab
    }

    // ═══════════════════════════════════════════
    // PROXY
    // ═══════════════════════════════════════════

    val allProxies: StateFlow<List<ProxyEntity>> =
            dao.getAllProxies()
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectProxy(proxy: ProxyEntity) {
        viewModelScope.launch(Dispatchers.IO) { dao.setActiveProxy(proxy.id) }
    }

    fun deleteProxy(proxy: ProxyEntity) {
        viewModelScope.launch(Dispatchers.IO) { dao.deleteProxy(proxy) }
    }

    // ═══════════════════════════════════════════
    // VPN DURUM
    // ═══════════════════════════════════════════

    val connectionState = VpnStatusManager.status
    val activeServerName = VpnStatusManager.activeServer
    val errorMessage = VpnStatusManager.errorMessage

    // ═══════════════════════════════════════════
    // UYGULAMALAR (per-app tünelleme)
    // ═══════════════════════════════════════════

    val allApps: StateFlow<List<AppEntity>> =
            dao.getAllAppsFlow()
                    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleApp(app: AppEntity) {
        viewModelScope.launch(Dispatchers.IO) { dao.insertApp(app.copy(isActive = !app.isActive)) }
    }

    // --- YENİ: Uygulama Bazlı SIM Koruması ---
    val installedApps: StateFlow<List<AppProtectionInfo>> =
        simDao.getAllProtectedAppsFlow().map { protectedList ->
            val pm = app?.packageManager ?: return@map emptyList<AppProtectionInfo>()
            val apps = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
            
            apps.map { info ->
                val userId = info.uid / 100000
                val isProtected = protectedList.any { it.packageName == info.packageName && it.userId == userId }
                AppProtectionInfo(
                    name = info.loadLabel(pm).toString(),
                    packageName = info.packageName,
                    userId = userId,
                    isProtected = isProtected
                )
            }.sortedBy { it.name }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ═══════════════════════════════════════════
    // OTURUM YÖNETİMİ (Kazık Savar)
    // ═══════════════════════════════════════════

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _remainingTimeFormatted = MutableStateFlow("00:00:00")
    val remainingTimeFormatted: StateFlow<String> = _remainingTimeFormatted.asStateFlow()

    /** 1 saniyelik ticker — oturum süresini sürekli kontrol eder */
    private fun startSessionTicker() {
        viewModelScope.launch(Dispatchers.IO) {
            var bridgePollCounter = 0
            while (true) {
                val settings = dao.getSettingsFlow().firstOrNull() ?: SettingsEntity()
                val active = SessionManager.isSessionActive(settings.sessionExpiryTime)
                _isSessionActive.value = active
                _remainingTimeFormatted.value = SessionManager.formatRemainingTime(settings.sessionExpiryTime)

                // Süre yeni bittiyse sona erme mantığını tetikle
                if (!active && settings.sessionExpiryTime > 0L) {
                    onSessionExpired()
                }

                // İş profilinde oturum YOKKEN: her 3 saniyede bridge'den kontrol et
                if (!active && _isInWorkProfile.value) {
                    bridgePollCounter++
                    if (bridgePollCounter >= 3) {
                        bridgePollCounter = 0
                        try {
                            val (expiry, bridgeActive) = com.truva.sync.BridgeClient.fetchSession()
                            if (bridgeActive && expiry > System.currentTimeMillis()) {
                                // Ana profilde oturum açılmış — hemen senkronize et!
                                updateSetting { it.copy(sessionExpiryTime = expiry) }
                                _isSessionActive.value = true
                                android.util.Log.i("Truva", "İş profili — Bridge'den oturum senkronize edildi: expiry=$expiry")
                                app?.let { context -> applyWorkProfileProtections(context) }
                            }
                        } catch (e: Exception) {
                            // Bridge erişilemezse sessizce devam et
                        }
                    }
                } else {
                    bridgePollCounter = 0
                }

                kotlinx.coroutines.delay(1000)
            }
        }
    }

    /** Kazık Savar'dan deep link ile çağrılır — 3 saat ekler (Süre varsa üzerine ekler) */
    fun activateSession() {
        viewModelScope.launch(Dispatchers.IO) {
            val settings = currentSettings()
            val newExpiry = SessionManager.calculateNewExpiryTime(settings.sessionExpiryTime)
            updateSetting { it.copy(sessionExpiryTime = newExpiry) }
            _isSessionActive.value = true
            android.util.Log.i("Truva", "Oturum aktive edildi! Bitiş: $newExpiry")

            app?.let { context ->
                // 1. Bekleyen SIM korumalarını uygula (ADB üzerinden)
                applyPendingProtections(context)

                // 2. Aktif bölge profili varsa spoofing katmanlarını yeniden uygula
                val profileId = settings.activeRegionProfileId
                if (profileId != null && settings.isSpoofingEnabled) {
                    val profile = com.truva.spoofing.RegionProfile.findById(profileId)
                    if (profile != null) {
                        val coord = com.truva.spoofing.SpoofingCoordinator.getInstance(context)
                        coord.applyProfile(
                            profile,
                            simEnabled = settings.isSimSpoofEnabled,
                            gpsEnabled = settings.isGpsSpoofEnabled,
                            timezoneEnabled = settings.isTimezoneSpoofEnabled,
                            localeEnabled = settings.isLocaleSpoofEnabled,
                            deviceIdEnabled = settings.isDeviceIdSpoofEnabled
                        )
                        _spoofingStatus.value = coord.getFullStatusSummary()
                        android.util.Log.i("Truva", "Spoofing yeniden uygulandı: ${profile.displayName}")
                    }
                }

                // 3. İş profilindeyse: VPN kilidi + tüm uygulamaların SIM izinlerini toplu kapat
                applyWorkProfileProtections(context)
            }
        }
    }

    /** İş profili korumalarını (VPN Lockdown ve Toplu SIM İzni İptali) aktif eder */
    private fun applyWorkProfileProtections(context: android.content.Context) {
        try {
            val sandbox = com.truva.sandbox.TruvaSandbox.getInstance(context)
            if (sandbox.isProfileOwner) {
                // VPN Lockdown: Sadece Truva VPN üzerinden internet
                sandbox.workProfileManager.setVpnLockdown(true)
                android.util.Log.i("Truva", "Oturum açıldı/senkronize edildi — VPN Lockdown AKTİF")

                // Tüm uygulamaların SIM izinlerini toplu kapat
                val count = sandbox.workProfileManager.lockdownAllAppsSimPermissions()
                android.util.Log.i("Truva", "Oturum açıldı/senkronize edildi — $count uygulama SIM kilidi uygulandı")
            }
        } catch (e: Exception) {
            android.util.Log.w("Truva", "Toplu kilitleme hatası: ${e.message}")
        }
    }

    /** Oturum süresi dolduğunda çağrılır */
    private fun onSessionExpired() {
        viewModelScope.launch(Dispatchers.IO) {
            android.util.Log.i("Truva", "Oturum süresi doldu! Karartma aktif.")

            // 1. VPN'i durdur (ama VPN Lockdown kalır — VPN olmadan internet YOK)
            app?.let { context ->
                context.startService(
                    android.content.Intent(context, MyVpnService::class.java).apply {
                        // Hem normal VPN'i hem Oyun Modunu kapatır
                        action = MyVpnService.ACTION_DISCONNECT
                    }
                )

                // NOT: VPN Lockdown (setAlwaysOnVpnPackage) KALDIRILMAZ.
                // İş profilinde internet sadece Truva VPN üzerinden çalışmalı.
                // Oturum süresi ne olursa olsun bu kural değişmez.
                android.util.Log.i("Truva", "Süre doldu — VPN kesildi ama Lockdown AKTİF kalıyor")

                // 2. Spoofing katmanlarını (GPS, Timezone, Locale vb.) kapat
                try {
                    com.truva.spoofing.SpoofingCoordinator.getInstance(context).deactivate()
                    android.util.Log.i("Truva", "Spoofing katmanları devre dışı bırakıldı.")
                } catch (e: Exception) {
                    android.util.Log.w("Truva", "Spoofing kapatılamadı: ${e.message}")
                }
            }

            // 3. SIM korumalarını geçici olarak geri al
            batchRevertProtections()

            // 4. sessionExpiryTime'ı sıfırla (tekrar tetiklemeyi önle)
            updateSetting { it.copy(sessionExpiryTime = 0L) }
        }
    }

    /** Tüm SIM korumalarını allow moduna çevirir */
    private suspend fun batchRevertProtections() {
        val context = app ?: return
        val allProtected = simDao.getAllProtectedAppsFlow().firstOrNull() ?: return
        val active = allProtected.filter { it.isProtected }
        if (active.isEmpty()) return

        android.util.Log.i("Truva", "Süre doldu — ${active.size} koruma geri alınıyor...")
        active.forEach { entry ->
            val ops = listOf("READ_PHONE_STATE", "READ_PHONE_NUMBERS", "READ_DEVICE_IDENTIFIERS")
            ops.forEach { op ->
                com.truva.sandbox.adb.TruvaAdbClient.runAdbCommand(
                    context, "shell", "appops", "set", "--user", entry.userId.toString(), entry.packageName, op, "allow"
                )
            }
        }
    }

    // ═══════════════════════════════════════════
    // OYUN MODU & UI STATE
    // ═══════════════════════════════════════════

    fun forceConnectingState() {
        VpnStatusManager.update(VpnState.CONNECTING, "İzin Bekleniyor...")
    }

    fun forceIdleState() {
        VpnStatusManager.update(VpnState.IDLE)
    }

    fun connectGameMode() {
        if (!isSessionActive.value) return
        
        app?.let { context ->
            val intent = android.content.Intent(context, MyVpnService::class.java).apply {
                action = MyVpnService.ACTION_GAME_MODE_CONNECT
            }
            context.startService(intent)
        }
    }

    fun disconnectGameMode() {
        app?.let { context ->
            val intent = android.content.Intent(context, MyVpnService::class.java).apply {
                action = MyVpnService.ACTION_GAME_MODE_DISCONNECT
            }
            context.startService(intent)
        }
    }

    // --- NITRO DPI (Yerel Paket Manipülasyonu) ---
    fun connectNitroDpi() {
        if (!isSessionActive.value) return
        
        app?.let { context ->
            val intent = android.content.Intent(context, MyVpnService::class.java).apply {
                action = MyVpnService.ACTION_NITRO_DPI_CONNECT
            }
            context.startService(intent)
        }
    }

    fun disconnectNitroDpi() {
        app?.let { context ->
            val intent = android.content.Intent(context, MyVpnService::class.java).apply {
                action = MyVpnService.ACTION_NITRO_DPI_DISCONNECT
            }
            context.startService(intent)
        }
    }


    // ═══════════════════════════════════════════
    // RADAR (On-device ADB)
    // ═══════════════════════════════════════════

    // ═══════════════════════════════════════════
    // AYARLAR
    // ═══════════════════════════════════════════

    val settings: StateFlow<SettingsEntity> =
            dao.getSettingsFlow()
                    .map { it ?: SettingsEntity() }
                    .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsEntity())

    private suspend fun currentSettings(): SettingsEntity =
            dao.getSettingsFlow().firstOrNull() ?: SettingsEntity()

    fun toggleKillSwitch(enabled: Boolean) = updateSetting {
        it.copy(isKillSwitchEnabled = enabled)
    }
    fun toggleGamingMode(enabled: Boolean) = updateSetting {
        it.copy(isGamingModeEnabled = enabled)
    }
    fun toggleVideoOptimization(enabled: Boolean) = updateSetting {
        it.copy(isVideoOptimizationEnabled = enabled)
    }
    fun toggleSmartRouting(enabled: Boolean) = updateSetting {
        it.copy(isSmartRoutingEnabled = enabled)
    }
    fun toggleUdpDirectBypass(enabled: Boolean) = updateSetting {
        it.copy(isUdpDirectBypass = enabled)
    }
    fun toggleAutoSyncRegion(enabled: Boolean) = updateSetting {
        it.copy(isAutoSyncRegion = enabled)
    }
    fun toggleAntiDetection(enabled: Boolean) = updateSetting {
        it.copy(isAntiDetectionEnabled = enabled)
    }
    fun setRoutingMode(mode: String) = updateSetting { it.copy(routingMode = mode) }
    fun setNitroDpiAppMode(mode: String) = updateSetting { it.copy(nitroDpiAppMode = mode) }
    fun setNitroDpiApps(apps: String) = updateSetting { it.copy(nitroDpiApps = apps) }

    private fun updateSetting(transform: (SettingsEntity) -> SettingsEntity) {
        viewModelScope.launch(Dispatchers.IO) { dao.updateSettings(transform(currentSettings())) }
    }

    // ═══════════════════════════════════════════
    // SPOOFİNG (VPN'den bağımsız)
    // ═══════════════════════════════════════════

    val regionProfiles: List<RegionProfile> = RegionProfile.PROFILES

    private val _selectedRegionProfile = MutableStateFlow<RegionProfile?>(null)
    val selectedRegionProfile: StateFlow<RegionProfile?> = _selectedRegionProfile.asStateFlow()

    private val _spoofingStatus =
            MutableStateFlow(SpoofingStatus(false, null, null, emptyMap(), emptyMap(), emptyMap()))
    val spoofingStatus: StateFlow<SpoofingStatus> = _spoofingStatus.asStateFlow()

    val simSpoofManager: com.truva.spoofing.SimSpoofManager
        get() = com.truva.spoofing.SpoofingCoordinator.getInstance(app!!).simManager

    val gpsSpoofManager: com.truva.spoofing.GpsSpoofManager
        get() = com.truva.spoofing.SpoofingCoordinator.getInstance(app!!).gpsManager

    val systemSpoofManager: com.truva.spoofing.SystemSpoofManager
        get() = com.truva.spoofing.SpoofingCoordinator.getInstance(app!!).systemManager

    fun selectRegionProfile(profile: RegionProfile) {
        viewModelScope.launch(Dispatchers.IO) {
            _selectedRegionProfile.value = profile
            dao.setActiveRegionProfile(profile.id)
            dao.insertRegionProfile(
                    RegionProfileEntity(
                            profileId = profile.id,
                            displayName = profile.displayName,
                            isSelected = true,
                            lastUsedAt = System.currentTimeMillis()
                    )
            )
            updateSetting { it.copy(activeRegionProfileId = profile.id) }
            app?.let {
                val settings = currentSettings()
                val coord = SpoofingCoordinator.getInstance(it)
                coord.applyProfile(
                        profile,
                        simEnabled = settings.isSimSpoofEnabled,
                        gpsEnabled = settings.isGpsSpoofEnabled,
                        timezoneEnabled = settings.isTimezoneSpoofEnabled,
                        localeEnabled = settings.isLocaleSpoofEnabled,
                        deviceIdEnabled = settings.isDeviceIdSpoofEnabled
                )
                _spoofingStatus.value = coord.getFullStatusSummary()
            }
        }
    }

    fun toggleSpoofing(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            updateSetting { it.copy(isSpoofingEnabled = enabled) }
            app?.let {
                val coord = SpoofingCoordinator.getInstance(it)
                if (enabled) {
                    val settings = currentSettings()
                    _selectedRegionProfile.value?.let { p ->
                        coord.applyProfile(
                                p,
                                simEnabled = settings.isSimSpoofEnabled,
                                gpsEnabled = settings.isGpsSpoofEnabled,
                                timezoneEnabled = settings.isTimezoneSpoofEnabled,
                                localeEnabled = settings.isLocaleSpoofEnabled,
                                deviceIdEnabled = settings.isDeviceIdSpoofEnabled
                        )
                    }
                } else {
                    coord.deactivate()
                }
                _spoofingStatus.value = coord.getFullStatusSummary()
            }
        }
    }

    fun toggleSimSpoof(enabled: Boolean) = updateSetting { it.copy(isSimSpoofEnabled = enabled) }
    fun toggleGpsSpoof(enabled: Boolean) = updateSetting { it.copy(isGpsSpoofEnabled = enabled) }
    fun toggleTimezoneSpoof(enabled: Boolean) = updateSetting {
        it.copy(isTimezoneSpoofEnabled = enabled)
    }
    fun toggleLocaleSpoof(enabled: Boolean) = updateSetting {
        it.copy(isLocaleSpoofEnabled = enabled)
    }
    fun toggleDeviceIdSpoof(enabled: Boolean) = updateSetting {
        it.copy(isDeviceIdSpoofEnabled = enabled)
    }

    fun setSimProtection(packageName: String, enable: Boolean, userId: Int = 0) {
        viewModelScope.launch(Dispatchers.IO) {
            // Her zaman DB'ye kaydet (ADB bağlı olmasa bile)
            simDao.insertProtectedApp(SimProtectionEntity(packageName, userId, enable))
            
            // ADB bağlıysa komutu hemen gönder
            if (_adbStatus.value) {
                app?.let { context ->
                    val mode = if (enable) "ignore" else "allow"
                    val ops = listOf("READ_PHONE_STATE", "READ_PHONE_NUMBERS", "READ_DEVICE_IDENTIFIERS")
                    ops.forEach { op ->
                        com.truva.sandbox.adb.TruvaAdbClient.runAdbCommand(
                            context, "shell", "appops", "set", "--user", userId.toString(), packageName, op, mode
                        )
                    }
                }
            } else {
                android.util.Log.i("Truva", "ADB bağlı değil, $packageName DB'ye kaydedildi. Bağlanınca uygulanacak.")
            }
        }
    }

    fun refreshSpoofingStatus() {
        app?.let {
            _spoofingStatus.value = SpoofingCoordinator.getInstance(it).getFullStatusSummary()
        }
    }

    // ═══════════════════════════════════════════
    // BAĞLANTI DURUMU (IP Lokasyon)
    // ═══════════════════════════════════════════

    private val _ipInfo = MutableStateFlow(IpInfo())
    val ipInfo: StateFlow<IpInfo> = _ipInfo.asStateFlow()

    fun checkCurrentLocation() {
        viewModelScope.launch(Dispatchers.IO) {
            _ipInfo.value = IpInfo(status = "loading")
            try {
                val url =
                        URL(
                                "http://ip-api.com/json/?fields=status,message,country,city,isp,query,lat,lon"
                        )

                val currentVpnState = VpnStatusManager.status.value
                val connection =
                        if (currentVpnState == VpnState.CONNECTED ||
                                        currentVpnState == VpnState.CONNECTING ||
                                        currentVpnState == VpnState.GAMING ||
                                        currentVpnState == VpnState.NITRO_DPI
                        ) {
                            val proxy =
                                    Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", 10808))
                            url.openConnection(proxy) as HttpURLConnection
                        } else {
                            url.openConnection() as HttpURLConnection
                        }

                connection.requestMethod = "GET"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)

                    val status = json.optString("status", "fail")
                    if (status == "success") {
                        _ipInfo.value =
                                IpInfo(
                                        query = json.optString("query", ""),
                                        country = json.optString("country", ""),
                                        city = json.optString("city", ""),
                                        isp = json.optString("isp", ""),
                                        lat = json.optDouble("lat", 0.0),
                                        lon = json.optDouble("lon", 0.0),
                                        status = "success"
                                )
                    } else {
                        _ipInfo.value =
                                IpInfo(
                                        query = json.optString("message", "Sorgu başarısız"),
                                        status = "fail"
                                )
                    }
                } else {
                    _ipInfo.value = IpInfo(query = "HTTP hatası: $responseCode", status = "fail")
                }
                connection.disconnect()
            } catch (e: java.net.SocketTimeoutException) {
                _ipInfo.value =
                        IpInfo(query = "İnternet bağlantısı zaman aşımına uğradı.", status = "fail")
            } catch (e: java.net.UnknownHostException) {
                _ipInfo.value = IpInfo(query = "İnternet bağlantısı yok.", status = "fail")
            } catch (e: Exception) {
                _ipInfo.value =
                        IpInfo(query = "Hata: ${e.message ?: "Bilinmeyen"}", status = "fail")
            }
        }
    }

    // ═══════════════════════════════════════════
    // GÖREVLENDİRME & İZOLASYON (Temizlendi)
    // ═══════════════════════════════════════════

    /** Sandbox durum bilgisi — UI kartları bu flow'dan beslenir */
    data class SandboxUIStatus(
            val isWorkProfileActive: Boolean = false,
            val canCreateWorkProfile: Boolean = false,
            val isDeviceCapable: Boolean = false
    )

    private val _sandboxStatus = MutableStateFlow(SandboxUIStatus())
    val sandboxStatus: StateFlow<SandboxUIStatus> = _sandboxStatus.asStateFlow()

    private val _adbStatus = MutableStateFlow(false)
    val adbStatus: StateFlow<Boolean> = _adbStatus.asStateFlow()

    /** Sandbox durumunu güncelle */
    fun refreshSandboxStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            app?.let {
                val sandbox = TruvaSandbox.getInstance(it)
                val status = sandbox.getStatusSummary()
                _sandboxStatus.value =
                        SandboxUIStatus(
                                isWorkProfileActive = status.isWorkProfileActive,
                                canCreateWorkProfile = status.canCreateWorkProfile,
                                isDeviceCapable = status.isDeviceCapable
                        )
            }
        }
    }

    /** Hibrit izin kısıtlaması uygula */
    fun applyInstantRestriction(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            app?.let { ctx ->
                val sandbox = TruvaSandbox.getInstance(ctx)

                // 1. ÖNCELİK: İş Profili (Sessiz ve tam otomatik)
                var success =
                        sandbox.workProfileManager.restrictPermissionAutomatically(
                                packageName,
                                android.Manifest.permission.READ_PHONE_STATE
                        )

                // 2. ÖNCELİK: On-Device ADB (Eğer İş Profili yoksa ama ADB bağlıysa)
                if (!success && _adbStatus.value) {
                    success =
                            com.truva.sandbox.shizuku.TruvaPermissionManager.restrictViaAdb(
                                    ctx,
                                    packageName,
                                    "READ_PHONE_STATE",
                                    "ignore"
                            )
                }

                // 3. YOL: Manuel (Hiçbir gelişmiş yetki yoksa Ayarlar'ı aç)
                if (!success) {
                    viewModelScope.launch(Dispatchers.Main) {
                        com.truva.sandbox.shizuku.TruvaPermissionManager.openAppSettings(
                                ctx,
                                packageName
                        )
                    }
                }
            }
        }
    }

    fun toggleSandbox(enabled: Boolean) {
        updateSetting { it.copy(isSandboxEnabled = enabled) }
        if (enabled) {
            refreshSandboxStatus()
        }
    }

    /** İş Profili oluşturma intent'i */
    private val _provisioningIntent = MutableStateFlow<android.content.Intent?>(null)
    val provisioningIntent: StateFlow<android.content.Intent?> = _provisioningIntent.asStateFlow()

    fun createWorkProfile() {
        viewModelScope.launch(Dispatchers.Main) {
            app?.let {
                val sandbox = TruvaSandbox.getInstance(it)
                val intent = sandbox.workProfileManager.getProvisioningIntent()
                _provisioningIntent.value = intent
            }
        }
    }

    fun consumeProvisioningIntent() {
        _provisioningIntent.value = null
    }

    fun onProvisioningResult(success: Boolean) {
        if (success) {
            refreshSandboxStatus()
        }
    }

    /** İş Profili kaldırma */
    fun removeWorkProfile() {
        viewModelScope.launch(Dispatchers.Main) {
            app?.let {
                TruvaSandbox.getInstance(it).workProfileManager.removeWorkProfile()
                refreshSandboxStatus()
            }
        }
    }

    // ═══════════════════════════════════════════
    // LATENCY TEST
    // ═══════════════════════════════════════════

    fun testAllProxies() {
        viewModelScope.launch(Dispatchers.IO) {
            val current = dao.getAllProxies().firstOrNull() ?: return@launch
            current.forEach { proxy ->
                val ms =
                        try {
                            kotlinx.coroutines.withTimeout(2000) {
                                val start = System.currentTimeMillis()
                                val socket = java.net.Socket()
                                socket.connect(
                                        java.net.InetSocketAddress(proxy.ip, proxy.port),
                                        2000
                                )
                                socket.close()
                                System.currentTimeMillis() - start
                            }
                        } catch (_: Exception) {
                            -1L
                        }
                dao.insertProxy(proxy.copy(latency = ms))
            }
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val settings = dao.getSettingsFlow().firstOrNull() ?: SettingsEntity()
            val profileId = settings.activeRegionProfileId
            if (profileId != null && settings.isSpoofingEnabled) {
                val profile = RegionProfile.findById(profileId)
                if (profile != null) {
                    _selectedRegionProfile.value = profile
                    app?.let {
                        val coord = SpoofingCoordinator.getInstance(it)
                        coord.applyProfile(
                                profile,
                                simEnabled = settings.isSimSpoofEnabled,
                                gpsEnabled = settings.isGpsSpoofEnabled,
                                timezoneEnabled = settings.isTimezoneSpoofEnabled,
                                localeEnabled = settings.isLocaleSpoofEnabled,
                                deviceIdEnabled = settings.isDeviceIdSpoofEnabled
                        )
                        _spoofingStatus.value = coord.getFullStatusSummary()
                    }
                }
            } else if (profileId != null) {
                _selectedRegionProfile.value = RegionProfile.findById(profileId)
            }

            if (settings.isSandboxEnabled) {
                refreshSandboxStatus()
            }

            // ADB Otomatik Yeniden Bağlanma
            autoConnectADB()

            // Radar port bulduğunda otomatik bağlanmayı dene
            com.truva.sandbox.adb.AdbScanner.onConnectPortFound = { port ->
                refreshAdbStatus(port)
            }

            // Uygulama açılışında radarı otomatik başlat
            app?.let { context ->
                com.truva.sandbox.adb.AdbScanner.startScanning(context) {
                    // İki port da bulunduğunda bildirime gerek yok; refreshAdbStatus zaten tetiklendi
                    android.util.Log.i("Truva", "Radar: Her iki port da otomatik bulundu.")
                }
            }

            // Profil tespiti: İş profilinde miyiz?
            app?.let { context ->
                val dpm = context.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
                val inWorkProfile = dpm.isProfileOwnerApp(context.packageName)
                _isInWorkProfile.value = inWorkProfile

                if (!inWorkProfile) {
                    // ANA PROFİL: Köprü sunucusunu başlat (veri sağlayıcı)
                    com.truva.sync.LocalhostBridge.start(dao)
                    _bridgeStatus.value = true
                    android.util.Log.i("Truva", "Ana profil — Localhost köprüsü başlatıldı.")
                } else {
                    // İŞ PROFİLİ: Köprü istemcisi olarak çalış
                    android.util.Log.i("Truva", "İş profili — Köprü istemci modu.")

                    // Otomatik oturum senkronizasyonu: Ana profilden oturum bilgisini çek
                    try {
                        val bridgeReachable = com.truva.sync.BridgeClient.checkBridge()
                        _bridgeStatus.value = bridgeReachable
                        if (bridgeReachable) {
                            android.util.Log.i("Truva", "İş profili — Köprü erişilebilir, oturum senkronize ediliyor...")

                            // 1. Oturum bilgisini çek ve uygula
                            val (expiry, isActive) = com.truva.sync.BridgeClient.fetchSession()
                            if (isActive && expiry > System.currentTimeMillis()) {
                                updateSetting { it.copy(sessionExpiryTime = expiry) }
                                _isSessionActive.value = true
                                android.util.Log.i("Truva", "İş profili — Oturum otomatik senkronize edildi: expiry=$expiry")
                                // Korumaları aktif et
                                applyWorkProfileProtections(context)
                            }

                            // 2. Sunucu listesini de otomatik çek (ilk kurulumda faydalı)
                            val servers = com.truva.sync.BridgeClient.fetchServers()
                            if (servers.isNotEmpty()) {
                                val existing = dao.getAllProxiesList()
                                if (existing.isEmpty()) {
                                    // Sadece iş profilinde hiç sunucu yoksa otomatik aktar
                                    servers.forEach { proxy ->
                                        dao.insertProxy(proxy.copy(id = 0))
                                    }
                                    android.util.Log.i("Truva", "İş profili — ${servers.size} sunucu otomatik aktarıldı.")
                                }
                            }
                            Unit // Kotlin expression fix
                        } else {
                            android.util.Log.w("Truva", "İş profili — Köprüye erişilemiyor. Ana Truva açık mı?")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("Truva", "İş profili — Otomatik senkronizasyon hatası", e)
                    }
                }
            }

            // Oturum süresini takip etmeye başla (profil tespitinden SONRA)
            startSessionTicker()
        }
    }

    // ═══════════════════════════════════════════
    // SYNC (Work Profile Senkronizasyon)
    // ═══════════════════════════════════════════

    /** Ana profildeki sunucuları iş profiline aktar (sadece iş profilinden çağrılmalı) */
    fun syncServersFromBridge() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!_isInWorkProfile.value) {
                _syncMessage.value = "⚠ Bu buton sadece İş Profili içinden çalışır."
                return@launch
            }
            _syncMessage.value = "Sunucular alınıyor..."
            try {
                val servers = com.truva.sync.BridgeClient.fetchServers()
                if (servers.isNotEmpty()) {
                    servers.forEach { proxy ->
                        dao.insertProxy(proxy.copy(id = 0)) // Yeni ID ile kaydet
                    }
                    _syncMessage.value = "✅ ${servers.size} sunucu aktarıldı!"
                } else {
                    _syncMessage.value = "⚠ Ana profilde sunucu bulunamadı."
                }
            } catch (e: Exception) {
                _syncMessage.value = "❌ Aktarım hatası: ${e.message}"
            }
        }
    }

    /** Ana profildeki oturum süresini iş profiline aktar (sadece iş profilinden çağrılmalı) */
    fun syncSessionFromBridge() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!_isInWorkProfile.value) {
                _syncMessage.value = "⚠ Bu buton sadece İş Profili içinden çalışır."
                return@launch
            }
            _syncMessage.value = "Oturum bilgisi alınıyor..."
            try {
                val (expiry, isActive) = com.truva.sync.BridgeClient.fetchSession()
                if (isActive && expiry > System.currentTimeMillis()) {
                    updateSetting { it.copy(sessionExpiryTime = expiry) }
                    _isSessionActive.value = true
                    _syncMessage.value = "✅ Oturum senkronize edildi!"
                    app?.let { context -> applyWorkProfileProtections(context) }
                } else {
                    _syncMessage.value = "⚠ Ana profilde aktif oturum yok."
                }
            } catch (e: Exception) {
                _syncMessage.value = "❌ Oturum aktarım hatası: ${e.message}"
            }
        }
    }

    /** Köprü bağlantısını kontrol et */
    fun checkBridgeConnection() {
        viewModelScope.launch(Dispatchers.IO) {
            val reachable = com.truva.sync.BridgeClient.checkBridge()
            _bridgeStatus.value = reachable
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    /** Uygulama açılışında veya kullanıcı tetiklediğinde ADB'ye bağlanmayı dener */
    fun autoConnectADB() {
        viewModelScope.launch(Dispatchers.IO) {
            app?.let { context ->
                // 1. Önce mevcut bir bağlantı var mı kontrol et
                val alreadyConnected = com.truva.sandbox.adb.TruvaAdbClient.checkIsConnected(context)
                if (alreadyConnected) {
                    _adbStatus.value = true
                    android.util.Log.i("Truva", "Mevcut ADB bağlantısı tespit edildi!")
                    applyPendingProtections(context)
                    return@launch
                }
                
                // 2. Mevcut bağlantı yoksa kayıtlı port ile dene
                val settings = dao.getSettingsFlow().firstOrNull() ?: return@launch
                val savedPort = settings.adbConnectionPort
                if (savedPort > 0) {
                    val success = com.truva.sandbox.adb.TruvaAdbClient.connect(context, savedPort)
                    if (success) {
                        _adbStatus.value = true
                        android.util.Log.i("Truva", "Kayıtlı porttan bağlantı başarılı: $savedPort")
                        applyPendingProtections(context)
                        return@launch
                    }
                }
                
                // 3. Bağlantı başarısızsa veya port yoksa taramayı yeniden başlat
                _adbStatus.value = false
                android.util.Log.w("Truva", "ADB bağlantısı başarısız. Radar (mDNS) yeniden başlatılıyor...")
                com.truva.sandbox.adb.AdbScanner.stopScanning() // Temizlik
                com.truva.sandbox.adb.AdbScanner.startScanning(context) {
                    android.util.Log.i("Truva", "Radar: Yeni portlar bulundu.")
                }
            }
        }
    }

    /** Radar port bulduğunda çağrılır */
    fun refreshAdbStatus(connectionPort: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            app?.let { context ->
                val success = com.truva.sandbox.adb.TruvaAdbClient.connect(context, connectionPort)
                _adbStatus.value = success
                if (success) {
                    android.util.Log.i("Truva", "Otomatik bağlantı başarılı: $connectionPort")
                    updateSetting { it.copy(adbConnectionPort = connectionPort) }
                    applyPendingProtections(context)
                } else {
                    android.util.Log.w("Truva", "ADB bağlantısı reddedildi, eşleştirme gerekebilir.")
                    if (com.truva.sandbox.adb.AdbScanner.pairingPort != null) {
                        try {
                            com.truva.sandbox.adb.AdbPairingReceiver.showNotification(context)
                        } catch (e: Exception) {
                            android.util.Log.e("Truva", "Bildirim gösterilemedi", e)
                        }
                    }
                }
            }
        }
    }

    /** DB'de işaretli ama henüz uygulanmamış korumaları ADB üzerinden gönder */
    private suspend fun applyPendingProtections(context: android.content.Context) {
        val allProtected = simDao.getAllProtectedAppsFlow().firstOrNull() ?: return
        val pending = allProtected.filter { it.isProtected }
        if (pending.isEmpty()) return
        android.util.Log.i("Truva", "${pending.size} bekleyen SIM koruması uygulanıyor...")
        pending.forEach { entry ->
            val ops = listOf("READ_PHONE_STATE", "READ_PHONE_NUMBERS", "READ_DEVICE_IDENTIFIERS")
            ops.forEach { op ->
                com.truva.sandbox.adb.TruvaAdbClient.runAdbCommand(
                    context, "shell", "appops", "set", "--user", entry.userId.toString(), entry.packageName, op, "ignore"
                )
            }
        }
    }

    fun pairAdb(pairingPort: String, connectionPort: String, pairingCode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pairPortInt =
                        pairingPort.toIntOrNull()
                                ?: throw IllegalArgumentException("Geçersiz Eşleştirme Portu")
                val connPortInt =
                        connectionPort.toIntOrNull()
                                ?: throw IllegalArgumentException("Geçersiz Bağlantı Portu")

                app?.let { context ->
                    // Yeni pairAndConnect fonksiyonunu çağırıyoruz
                    val success =
                            com.truva.sandbox.adb.TruvaAdbClient.pairAndConnect(
                                    context,
                                    pairPortInt,
                                    connPortInt,
                                    pairingCode
                            )
                    if (success) {
                        updateSetting { it.copy(adbConnectionPort = connPortInt) }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("TruvaVM", "Eşleştirme başarısız", e)
                _adbStatus.value = false
            }
        }
    }
}
