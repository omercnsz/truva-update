package com.truva.xposed

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.truva.xposed.hooks.*

/**
 * TruvaHookModule — Ana Xposed Modül Giriş Noktası
 *
 * LSPatch ile yamalanmış uygulamalar bu modülü yükler.
 * assets/xposed_init dosyası bu sınıfı gösterir.
 *
 * Akış:
 * 1. LSPatch hedef APK'yı yamar
 * 2. Uygulama başlatılınca LSPatch, Truva modülünü yükler
 * 3. handleLoadPackage() çağrılır
 * 4. SpoofConfig okunur (ContentProvider üzerinden)
 * 5. Tüm hook'lar aktif edilir
 *
 * Desteklenen Hook Kategorileri:
 * ┌─────────────────────────────────────────┐
 * │ 1. SIM Spoofing (TelephonyManager)      │
 * │ 2. GPS Spoofing (LocationManager)        │
 * │ 3. Device ID (Settings.Secure, IMEI)     │
 * │ 4. System Props (TZ, Locale, Build.*)    │
 * │ 5. Anti-Detection (Root/Mock/VPN/Hook)   │
 * └─────────────────────────────────────────┘
 */
class TruvaHookModule : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Truva'nın kendi paketini hook'lama
        if (lpparam.packageName == "com.truva") return

        val tag = "TruvaHook"
        XposedBridge.log("[$tag] Modül yükleniyor: ${lpparam.packageName}")

        // 1. Spoofing konfigürasyonunu oku
        val config = SpoofConfigReader.readConfig(lpparam)

        if (!config.isEnabled) {
            XposedBridge.log("[$tag] Spoofing devre dışı, hook'lar atlanıyor")
            return
        }

        XposedBridge.log("[$tag] Profil: ${config.profileName} | Değer sayısı: ${config.values.size}")

        // 2. Hook kategorilerini sırayla etkinleştir
        val hookResults = mutableListOf<Pair<String, Boolean>>()

        hookResults.add("SIM" to safeHook(tag, "SIM") {
            TelephonyHooks.install(lpparam, config)
        })

        hookResults.add("GPS" to safeHook(tag, "GPS") {
            LocationHooks.install(lpparam, config)
        })

        hookResults.add("DeviceID" to safeHook(tag, "DeviceID") {
            DeviceIdHooks.install(lpparam, config)
        })

        hookResults.add("System" to safeHook(tag, "System") {
            SystemPropHooks.install(lpparam, config)
        })

        hookResults.add("AntiDetect" to safeHook(tag, "AntiDetect") {
            AntiDetectionHooks.install(lpparam, config)
        })

        // 3. Sonuç raporu
        val success = hookResults.count { it.second }
        val failed = hookResults.count { !it.second }
        XposedBridge.log("[$tag] Hook sonucu: $success başarılı, $failed başarısız")

        if (failed > 0) {
            val failedNames = hookResults.filter { !it.second }.joinToString { it.first }
            XposedBridge.log("[$tag] Başarısız hook'lar: $failedNames")
        }
    }

    private fun safeHook(tag: String, name: String, block: () -> Unit): Boolean {
        return try {
            block()
            XposedBridge.log("[$tag] ✓ $name hook'ları kuruldu")
            true
        } catch (e: Throwable) {
            XposedBridge.log("[$tag] ✗ $name hook hatası: ${e.message}")
            false
        }
    }
}
