package com.truva.spoofing

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Log
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * System Spoofing Manager — Timezone, Locale, Device ID Manipülasyonu
 *
 * Sandbox içindeki uygulamaların gördüğü sistem parametrelerini bölge profiliyle senkronize eder:
 * - Saat Dilimi (Timezone)
 * - Sistem Dili (Locale)
 * - Cihaz Kimliği (Android ID / IMEI benzeri)
 *
 * NOT: Bu manipülasyonlar sadece sandbox'taki uygulamalar içindir. Cihazın gerçek ayarları
 * değiştirilmez.
 */
class SystemSpoofManager(private val context: Context) {

    private val tag = "TruvaSysSpoof"

    @Volatile private var activeProfile: RegionProfile? = null

    // Sandbox-scope sahte cihaz kimlikleri (oturum başına üretilir)
    private var fakeAndroidId: String = generateFakeAndroidId()
    private var fakeDeviceId: String = generateFakeDeviceId()
    private var fakeBuildSerial: String = generateFakeBuildSerial()

    /** Aktif bölge profilini ayarla */
    fun setProfile(profile: RegionProfile?) {
        activeProfile = profile
        if (profile != null) {
            Log.i(tag, "System Spoofing aktif: TZ=${profile.timezone}, Locale=${profile.locale}")
        } else {
            Log.i(tag, "System Spoofing devre dışı")
        }
    }

    fun getProfile(): RegionProfile? = activeProfile

    /** Sahte cihaz kimliklerini yenile. Her sandbox oturumu başlatıldığında çağrılmalı. */
    fun regenerateDeviceIds() {
        fakeAndroidId = generateFakeAndroidId()
        fakeDeviceId = generateFakeDeviceId()
        fakeBuildSerial = generateFakeBuildSerial()
        Log.i(tag, "Cihaz kimlikleri yenilendi: AID=$fakeAndroidId")
    }

    // ────────────────────────────────────────────
    // Timezone Spoofing
    // ────────────────────────────────────────────

    /** Profil timezone'u döndürür; yoksa sistem timezone'u */
    fun getSpoofedTimezone(): String {
        return activeProfile?.timezone ?: TimeZone.getDefault().id
    }

    fun getSpoofedTimeZoneObject(): TimeZone {
        val tzId = getSpoofedTimezone()
        return TimeZone.getTimeZone(tzId)
    }

    /** UTC offset (milisaniye) */
    fun getSpoofedUtcOffset(): Int {
        return getSpoofedTimeZoneObject().rawOffset
    }

    // ────────────────────────────────────────────
    // Locale Spoofing
    // ────────────────────────────────────────────

    fun getSpoofedLocale(): Locale {
        val profile = activeProfile ?: return Locale.getDefault()
        return Locale(profile.language, profile.country)
    }

    fun getSpoofedLanguage(): String {
        return activeProfile?.language ?: Locale.getDefault().language
    }

    fun getSpoofedCountry(): String {
        return activeProfile?.country ?: Locale.getDefault().country
    }

    fun getSpoofedLocaleTag(): String {
        return activeProfile?.locale ?: Locale.getDefault().toLanguageTag()
    }

    // ────────────────────────────────────────────
    // Device ID Spoofing — Sahte kimlikler
    // ────────────────────────────────────────────

    /** Sandbox için sahte Android ID */
    fun getSpoofedAndroidId(): String = fakeAndroidId

    /** Sandbox için sahte Device ID (IMEI benzeri 15 hane) */
    fun getSpoofedDeviceId(): String = fakeDeviceId

    /** Sandbox için sahte Build.SERIAL */
    fun getSpoofedBuildSerial(): String = fakeBuildSerial

    /** Sandbox için sahte IMEI (GSM cihaz kimliği) */
    fun getSpoofedImei(): String = fakeDeviceId

    /** Sandbox uygulaması Settings.Secure.ANDROID_ID sorguladığında döndürülecek sahte değer. */
    fun getSpoofedSecureSetting(key: String): String? {
        return when (key) {
            Settings.Secure.ANDROID_ID -> fakeAndroidId
            else -> null // Diğer ayarlar gerçek değeri kullanır
        }
    }

    // ────────────────────────────────────────────
    // Build Property Spoofing
    // ────────────────────────────────────────────

    /** Build sınıfı değerlerini override etmek için kullanılır. Sandbox hook'ları bu map'i okur. */
    fun getSpoofedBuildProperties(): Map<String, String> {
        val profile = activeProfile ?: return emptyMap()
        return mapOf(
                "ro.build.display.id" to "RP1A.200720.011",
                "ro.product.locale" to profile.locale,
                "persist.sys.language" to profile.language,
                "persist.sys.country" to profile.country,
                "persist.sys.timezone" to profile.timezone,
                "gsm.operator.numeric" to profile.mccMnc,
                "gsm.operator.alpha" to profile.simOperatorName,
                "gsm.operator.iso-country" to profile.simCountryIso,
                "gsm.sim.operator.numeric" to profile.mccMnc,
                "gsm.sim.operator.alpha" to profile.simOperatorName,
                "gsm.sim.operator.iso-country" to profile.simCountryIso,
                "ro.serialno" to fakeBuildSerial
        )
    }

    // ────────────────────────────────────────────
    // Gerçek sistem değerleri (referans)
    // ────────────────────────────────────────────

    @SuppressLint("HardwareIds")
    fun getRealAndroidId(): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    // ────────────────────────────────────────────
    // Sahte kimlik üretici yardımcıları
    // ────────────────────────────────────────────

    private companion object {
        /** 16 karakter hex Android ID üret */
        fun generateFakeAndroidId(): String {
            return UUID.randomUUID().toString().replace("-", "").take(16)
        }

        /** 15 haneli IMEI benzeri Device ID üret */
        fun generateFakeDeviceId(): String {
            val base = (100000000000000L..999999999999999L).random()
            val digits = base.toString().take(14)
            // Luhn checksum hesapla
            var sum = 0
            digits.reversed().forEachIndexed { index, c ->
                var n = c.digitToInt()
                if (index % 2 == 0) {
                    n *= 2
                    if (n > 9) n -= 9
                }
                sum += n
            }
            val check = (10 - (sum % 10)) % 10
            return digits + check.toString()
        }

        /** Rastgele Build serial üret */
        fun generateFakeBuildSerial(): String {
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            return (1..12).map { chars.random() }.joinToString("")
        }
    }

    /** Spoofing durumu özet bilgisi */
    fun getStatusSummary(): Map<String, String> {
        val profile = activeProfile ?: return mapOf("durum" to "Kapalı")
        return mapOf(
                "durum" to "Aktif",
                "timezone" to profile.timezone,
                "locale" to profile.locale,
                "androidId" to "${fakeAndroidId.take(6)}...",
                "deviceId" to "${fakeDeviceId.take(6)}..."
        )
    }
}
