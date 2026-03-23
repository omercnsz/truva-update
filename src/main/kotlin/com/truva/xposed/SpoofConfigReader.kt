package com.truva.xposed

import android.content.ContentResolver
import android.net.Uri
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * SpoofConfigReader — ContentProvider Üzerinden Konfigürasyon Okuyucu
 *
 * Truva'nın SpoofConfigProvider'ından spoofing değerlerini okur. URI:
 * content://com.truva.spoofconfig/values
 *
 * Bu sınıf hedef uygulama process'inde çalışır (LSPatch tarafından enjekte edilir).
 */
object SpoofConfigReader {

    private const val AUTHORITY = "com.truva.spoofconfig"
    private val VALUES_URI = Uri.parse("content://$AUTHORITY/values")
    private val ENABLED_URI = Uri.parse("content://$AUTHORITY/enabled")
    private val ANTI_DETECTION_URI = Uri.parse("content://$AUTHORITY/antidetection")

    /**
     * Truva'dan spoofing konfigürasyonunu oku. ContentProvider üzerinden cross-process iletişim
     * sağlar. Eğer ContentProvider'a ulaşılamazsa (Android 11 Package Visibility veya İş Profili),
     * ApkBuilder tarafından gömülen lokal asset'i okur.
     */
    fun readConfig(lpparam: XC_LoadPackage.LoadPackageParam): SpoofConfig {
        try {
            // Context'i reflection ile al (hook process içinde Android context mevcut)
            val contextClass = lpparam.classLoader.loadClass("android.app.ActivityThread")
            val currentThread = contextClass.getMethod("currentActivityThread").invoke(null)
            val appContext =
                    contextClass.getMethod("getApplication").invoke(currentThread)
                            ?: return SpoofConfig.EMPTY

            val context = appContext as android.content.Context
            val resolver = context.contentResolver

            // 1. Spoofing etkin mi? (Önce ContentProvider'ı dene)
            val enabledInfo = readEnabled(resolver)
            if (enabledInfo != null && enabledInfo.isEnabled) {
                // ContentProvider başarılı!
                val values = readValues(resolver)
                val antiDetection = readAntiDetection(resolver)
                XposedBridge.log("[TruvaConfig] Config ContentProvider üzerinden başarıyla okundu.")
                return SpoofConfig(
                        isEnabled = true,
                        profileName = enabledInfo.profileName,
                        profileId = enabledInfo.profileId,
                        values = values,
                        hideRoot = antiDetection.hideRoot,
                        hideMock = antiDetection.hideMock,
                        hideVpn = antiDetection.hideVpn,
                        hideHook = antiDetection.hideHook
                )
            }

            // 2. Fallback: ContentProvider başarısız olduysa veya erişilemiyorsa (İş profili /
            // Android 11+ Package Visibility)
            // APK içine gömülü (embedded) spoof_values.json dosyasını oku
            val embeddedValues = readEmbeddedConfig(context)
            if (embeddedValues != null && embeddedValues.isNotEmpty()) {
                XposedBridge.log(
                        "[TruvaConfig] ContentProvider okunamadı, gömülü asset (spoof_values.json) başarıyla kullanılıyor."
                )
                return SpoofConfig(
                        isEnabled = true,
                        profileName = "Embedded Local Profile",
                        profileId = "embedded",
                        values = embeddedValues,
                        hideRoot = true, // Anti-detection varsayılan olarak açık
                        hideMock = true,
                        hideVpn = true,
                        hideHook = true
                )
            }

            XposedBridge.log(
                    "[TruvaConfig] Hiçbir spoofing konfigürasyonu bulunamadı (Provider başarısız, Asset yok)."
            )
            return SpoofConfig.EMPTY
        } catch (e: Throwable) {
            XposedBridge.log("[TruvaConfig] Konfigürasyon okuma kritik hatası: ${e.message}")
            return SpoofConfig.EMPTY
        }
    }

    private fun readEmbeddedConfig(context: android.content.Context): Map<String, String>? {
        return try {
            val inputStream = context.assets.open("truva/spoof_values.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }

            // JSON'u map'e çevir
            val jsonObject = org.json.JSONObject(jsonString)
            val map = mutableMapOf<String, String>()
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = jsonObject.getString(key)
            }
            map
        } catch (e: Exception) {
            null // Dosya yoksa veya json parse hatasıysa hata basmana gerek yok, normal fallback
            // senaryosu
        }
    }

    private data class EnabledInfo(
            val isEnabled: Boolean,
            val profileName: String,
            val profileId: String
    )

    private fun readEnabled(resolver: ContentResolver): EnabledInfo? {
        return try {
            resolver.query(ENABLED_URI, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    EnabledInfo(
                            isEnabled =
                                    cursor.getString(cursor.getColumnIndexOrThrow("enabled")) ==
                                            "1",
                            profileName =
                                    cursor.getString(cursor.getColumnIndexOrThrow("profile_name"))
                                            ?: "",
                            profileId = cursor.getString(cursor.getColumnIndexOrThrow("profile_id"))
                                            ?: ""
                    )
                } else null
            }
        } catch (e: Exception) {
            XposedBridge.log("[TruvaConfig] Enabled okuma hatası: ${e.message}")
            null
        }
    }

    private fun readValues(resolver: ContentResolver): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            resolver.query(VALUES_URI, null, null, null, null)?.use { cursor ->
                val keyIdx = cursor.getColumnIndexOrThrow("key")
                val valIdx = cursor.getColumnIndexOrThrow("value")
                while (cursor.moveToNext()) {
                    val key = cursor.getString(keyIdx) ?: continue
                    val value = cursor.getString(valIdx) ?: ""
                    result[key] = value
                }
            }
        } catch (e: Exception) {
            XposedBridge.log("[TruvaConfig] Values okuma hatası: ${e.message}")
        }
        return result
    }

    private data class AntiDetectFlags(
            val hideRoot: Boolean,
            val hideMock: Boolean,
            val hideVpn: Boolean,
            val hideHook: Boolean
    )

    private fun readAntiDetection(resolver: ContentResolver): AntiDetectFlags {
        return try {
            resolver.query(ANTI_DETECTION_URI, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    AntiDetectFlags(
                            hideRoot =
                                    cursor.getString(cursor.getColumnIndexOrThrow("hide_root")) ==
                                            "1",
                            hideMock =
                                    cursor.getString(cursor.getColumnIndexOrThrow("hide_mock")) ==
                                            "1",
                            hideVpn =
                                    cursor.getString(cursor.getColumnIndexOrThrow("hide_vpn")) ==
                                            "1",
                            hideHook =
                                    cursor.getString(cursor.getColumnIndexOrThrow("hide_hook")) ==
                                            "1"
                    )
                } else AntiDetectFlags(true, true, true, true)
            }
                    ?: AntiDetectFlags(true, true, true, true)
        } catch (e: Exception) {
            XposedBridge.log("[TruvaConfig] AntiDetection okuma hatası: ${e.message}")
            AntiDetectFlags(true, true, true, true)
        }
    }
}

/** Spoofing konfigürasyon veri sınıfı. Tüm hook modülleri bu configden değerleri alır. */
data class SpoofConfig(
        val isEnabled: Boolean,
        val profileName: String,
        val profileId: String,
        val values: Map<String, String>,
        val hideRoot: Boolean,
        val hideMock: Boolean,
        val hideVpn: Boolean,
        val hideHook: Boolean
) {
    // Yardımcı accessor'lar — hook'lar bunları kullanır
    fun getString(key: String, default: String = ""): String = values[key] ?: default
    fun getDouble(key: String, default: Double = 0.0): Double =
            values[key]?.toDoubleOrNull() ?: default
    fun getInt(key: String, default: Int = 0): Int = values[key]?.toIntOrNull() ?: default

    companion object {
        val EMPTY =
                SpoofConfig(
                        isEnabled = false,
                        profileName = "",
                        profileId = "",
                        values = emptyMap(),
                        hideRoot = true,
                        hideMock = true,
                        hideVpn = true,
                        hideHook = true
                )
    }
}
