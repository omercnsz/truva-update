package com.truva.spoofing

import android.content.Context
import android.util.Log

/**
 * Spoofing Coordinator — Tüm Spoofing Katmanlarını Senkronize Eder
 *
 * Tek koordinasyon noktası: Bir bölge profili seçildiğinde IP (VPN) / SIM / GPS / Timezone / Locale
 * / Device ID hepsi otomatik olarak aynı bölgeye ayarlanır.
 *
 * Bu sınıf, Truva'nın "Senkronize Bölge Kimliği" felsefesinin uygulandığı merkez noktadır.
 */
class SpoofingCoordinator private constructor(context: Context) {

    private val tag = "TruvaSpoofCoord"

    val simManager = SimSpoofManager(context)
    val gpsManager = GpsSpoofManager(context)
    val systemManager = SystemSpoofManager(context)

    @Volatile private var _currentProfile: RegionProfile? = null
    val currentProfile: RegionProfile?
        get() = _currentProfile

    @Volatile private var _isActive: Boolean = false
    val isActive: Boolean
        get() = _isActive

    /**
     * Bölge profilini tüm spoofing katmanlarına senkronize olarak uygula.
     * Bireysel toggle flag'leri kontrol edilir (settings üzerinden).
     *
     * @param profile Hedef bölge profili. null = tüm spoofing'i kapat.
     * @param simEnabled SIM spoofing aktif mi
     * @param gpsEnabled GPS spoofing aktif mi
     * @param timezoneEnabled Timezone spoofing aktif mi
     * @param localeEnabled Locale spoofing aktif mi
     * @param deviceIdEnabled Device ID spoofing aktif mi
     */
    fun applyProfile(
        profile: RegionProfile?,
        simEnabled: Boolean = true,
        gpsEnabled: Boolean = true,
        timezoneEnabled: Boolean = true,
        localeEnabled: Boolean = true,
        deviceIdEnabled: Boolean = true
    ) {
        _currentProfile = profile

        if (profile == null) {
            deactivate()
            return
        }

        Log.i(tag, "═══════════════════════════════════════════")
        Log.i(tag, "Bölge Profili Uygulanıyor: ${profile.displayName}")
        Log.i(tag, "  SIM=${simEnabled} GPS=${gpsEnabled} TZ=${timezoneEnabled} Locale=${localeEnabled} DevID=${deviceIdEnabled}")
        Log.i(tag, "═══════════════════════════════════════════")

        // 1. SIM Spoofing
        if (simEnabled) {
            simManager.setProfile(profile)
        } else {
            simManager.setProfile(null)
        }

        // 2. GPS Spoofing
        if (gpsEnabled) {
            gpsManager.setProfile(profile)
            gpsManager.startMocking()
        } else {
            gpsManager.stopMocking()
            gpsManager.setProfile(null)
        }

        // 3. System Spoofing (Timezone, Locale, Device ID)
        systemManager.setProfile(profile)
        if (timezoneEnabled) {
            try {
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(profile.timezone))
                Log.i(tag, "  Timezone uygulandı: ${profile.timezone}")
            } catch (e: Exception) {
                Log.w(tag, "  Timezone uygulanamadı: ${e.message}")
            }
        }
        if (localeEnabled) {
            try {
                val locale = java.util.Locale(profile.language, profile.country)
                java.util.Locale.setDefault(locale)
                Log.i(tag, "  Locale uygulandı: ${profile.locale}")
            } catch (e: Exception) {
                Log.w(tag, "  Locale uygulanamadı: ${e.message}")
            }
        }
        if (deviceIdEnabled) {
            systemManager.regenerateDeviceIds()
        }

        _isActive = true
        Log.i(tag, "Spoofing katmanları senkronize edildi ✓")
    }

    /**
     * Proxy bölge kodundan otomatik profil eşleşmesi. Kullanıcı proxy değiştirdiğinde bu çağrılır.
     */
    fun applyProfileForRegion(regionCode: String): RegionProfile? {
        val profile = RegionProfile.findByRegionCode(regionCode)
        if (profile != null) {
            applyProfile(profile)
        } else {
            Log.w(tag, "Bölge kodu '$regionCode' için profil bulunamadı")
        }
        return profile
    }

    /** Tüm spoofing'i devre dışı bırak. GPS mock durur, tüm manager'lar sıfırlanır. */
    fun deactivate() {
        Log.i(tag, "Tüm spoofing devre dışı bırakılıyor...")

        gpsManager.stopMocking()
        simManager.setProfile(null)
        gpsManager.setProfile(null)
        systemManager.setProfile(null)

        // Timezone ve Locale'i sisteme geri al
        try { java.util.TimeZone.setDefault(null) } catch (_: Exception) {}
        try { java.util.Locale.setDefault(java.util.Locale.getDefault()) } catch (_: Exception) {}

        _currentProfile = null
        _isActive = false

        Log.i(tag, "Spoofing devre dışı ✓")
    }

    /** Tüm spoofing katmanlarının durumunu özetler. UI'da gösterilmek üzere. */
    fun getFullStatusSummary(): SpoofingStatus {
        val profile = _currentProfile
        return SpoofingStatus(
                isActive = _isActive,
                regionName = profile?.displayName,
                flagEmoji = profile?.flagEmoji,
                sim = simManager.getStatusSummary(),
                gps = gpsManager.getStatusSummary(),
                system = systemManager.getStatusSummary()
        )
    }

    /** Tüm spoofing verilerini tek bir map olarak döndür. C++ hooking katmanı bu verileri okur. */
    fun getAllSpoofedValues(): Map<String, Any> {
        _currentProfile ?: return emptyMap()
        val result = mutableMapOf<String, Any>()
        // SIM
        result["sim.countryIso"] = simManager.getSpoofedSimCountryIso()
        result["sim.operatorName"] = simManager.getSpoofedSimOperatorName()
        result["sim.operator"] = simManager.getSpoofedSimOperator()
        result["network.countryIso"] = simManager.getSpoofedNetworkCountryIso()
        result["network.operatorName"] = simManager.getSpoofedNetworkOperatorName()
        result["network.operator"] = simManager.getSpoofedNetworkOperator()
        result["sim.mcc"] = simManager.getSpoofedMcc()
        result["sim.mnc"] = simManager.getSpoofedMnc()

        // GPS
        result["gps.latitude"] = gpsManager.getSpoofedLatitude()
        result["gps.longitude"] = gpsManager.getSpoofedLongitude()

        // System
        result["sys.timezone"] = systemManager.getSpoofedTimezone()
        result["sys.locale"] = systemManager.getSpoofedLocaleTag()
        result["sys.language"] = systemManager.getSpoofedLanguage()
        result["sys.country"] = systemManager.getSpoofedCountry()
        result["sys.androidId"] = systemManager.getSpoofedAndroidId()
        result["sys.deviceId"] = systemManager.getSpoofedDeviceId()
        result["sys.serial"] = systemManager.getSpoofedBuildSerial()
        result["sys.imei"] = systemManager.getSpoofedImei()

        // Build properties
        result.putAll(systemManager.getSpoofedBuildProperties())
        return result
    }

    companion object {
        @Volatile private var INSTANCE: SpoofingCoordinator? = null

        fun getInstance(context: Context): SpoofingCoordinator {
            return INSTANCE
                    ?: synchronized(this) {
                        INSTANCE
                                ?: SpoofingCoordinator(context.applicationContext).also {
                                    INSTANCE = it
                                }
                    }
        }
    }
}

/** UI'da gösterilecek spoofing durum özeti */
data class SpoofingStatus(
        val isActive: Boolean,
        val regionName: String?,
        val flagEmoji: String?,
        val sim: Map<String, String>,
        val gps: Map<String, String>,
        val system: Map<String, String>
)
