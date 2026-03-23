package com.truva.xposed.hooks

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.truva.xposed.SpoofConfig
import java.util.Locale
import java.util.TimeZone

/**
 * SystemPropHooks — Saat Dilimi, Dil/Bölge ve Build Properties Spoofing
 *
 * Hook edilen API'ler:
 * - TimeZone.getDefault()            → sahte timezone
 * - Locale.getDefault()              → sahte locale
 * - Build.MODEL, MANUFACTURER, etc.  → sahte build bilgileri
 * - SystemProperties.get()           → sahte sistem özellikleri
 *
 * Bu hook'lar in-process setDefault() ile birlikte çalışır.
 * Fark: setDefault() sadece Truva process'inde etkili.
 * Xposed hook'ları hedef uygulamanın process'inde de etkili.
 */
object SystemPropHooks {

    private const val TAG = "TruvaHook.Sys"

    fun install(lpparam: XC_LoadPackage.LoadPackageParam, config: SpoofConfig) {
        val cl = lpparam.classLoader

        installTimezoneHook(cl, config)
        installLocaleHook(cl, config)
        installBuildHooks(cl, config)
        installSystemPropertiesHook(cl, config)
    }

    // ── Saat Dilimi ──
    private fun installTimezoneHook(cl: ClassLoader, config: SpoofConfig) {
        val tz = config.getString("sys.timezone")
        if (tz.isEmpty()) return

        val fakeTimezone = TimeZone.getTimeZone(tz)

        XposedHelpers.findAndHookMethod(
            "java.util.TimeZone", cl,
            "getDefault",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    param.result = fakeTimezone
                }
            }
        )

        // Calendar default timezone da etkilensin
        try {
            XposedHelpers.findAndHookMethod(
                "java.util.Calendar", cl,
                "getInstance",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        TimeZone.setDefault(fakeTimezone)
                    }
                }
            )
        } catch (_: Throwable) {}

        XposedBridge.log("[$TAG] Timezone → $tz")
    }

    // ── Dil ve Bölge ──
    private fun installLocaleHook(cl: ClassLoader, config: SpoofConfig) {
        val language = config.getString("sys.language")
        val country = config.getString("sys.country")
        if (language.isEmpty() || country.isEmpty()) return

        val fakeLocale = Locale(language, country)

        XposedHelpers.findAndHookMethod(
            "java.util.Locale", cl,
            "getDefault",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    param.result = fakeLocale
                }
            }
        )

        // Locale.getDefault(Category) — API 24+
        try {
            val categoryClass = XposedHelpers.findClass("java.util.Locale\$Category", cl)
            @Suppress("UNCHECKED_CAST")
            XposedHelpers.findAndHookMethod(
                "java.util.Locale", cl,
                "getDefault",
                categoryClass,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = fakeLocale
                    }
                }
            )
        } catch (_: Throwable) {}

        // Resources.getConfiguration().locale
        try {
            XposedHelpers.findAndHookMethod(
                "android.content.res.Configuration", cl,
                "getLocales",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val localeList = Class.forName("android.os.LocaleList")
                                .getConstructor(Array<Locale>::class.java)
                                .newInstance(arrayOf(fakeLocale))
                            param.result = localeList
                        } catch (_: Throwable) {}
                    }
                }
            )
        } catch (_: Throwable) {}

        XposedBridge.log("[$TAG] Locale → ${language}_$country")
    }

    // ── Build Properties ──
    private fun installBuildHooks(cl: ClassLoader, config: SpoofConfig) {
        try {
            val buildClass = XposedHelpers.findClass("android.os.Build", cl)

            // Build alanlarını map'ten oku ve değiştir
            val buildFields = mapOf(
                "build.model" to "MODEL",
                "build.manufacturer" to "MANUFACTURER",
                "build.brand" to "BRAND",
                "build.device" to "DEVICE",
                "build.product" to "PRODUCT",
                "build.hardware" to "HARDWARE",
                "build.board" to "BOARD",
                "build.display" to "DISPLAY",
                "build.fingerprint" to "FINGERPRINT"
            )

            for ((configKey, fieldName) in buildFields) {
                val value = config.getString(configKey)
                if (value.isNotEmpty()) {
                    try {
                        XposedHelpers.setStaticObjectField(buildClass, fieldName, value)
                        XposedBridge.log("[$TAG] Build.$fieldName → $value")
                    } catch (_: Throwable) {}
                }
            }
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] Build hooks hatası: ${e.message}")
        }
    }

    // ── SystemProperties.get() ──
    private fun installSystemPropertiesHook(cl: ClassLoader, config: SpoofConfig) {
        val tz = config.getString("sys.timezone")
        val locale = config.getString("sys.locale")

        if (tz.isEmpty() && locale.isEmpty()) return

        try {
            XposedHelpers.findAndHookMethod(
                "android.os.SystemProperties", cl,
                "get",
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val key = param.args[0] as? String ?: return
                        when (key) {
                            "persist.sys.timezone" -> if (tz.isNotEmpty()) param.result = tz
                            "persist.sys.language" -> {
                                val lang = config.getString("sys.language")
                                if (lang.isNotEmpty()) param.result = lang
                            }
                            "persist.sys.country", "persist.sys.localevar" -> {
                                val country = config.getString("sys.country")
                                if (country.isNotEmpty()) param.result = country
                            }
                            "ro.product.model" -> {
                                val v = config.getString("build.model")
                                if (v.isNotEmpty()) param.result = v
                            }
                            "ro.product.manufacturer" -> {
                                val v = config.getString("build.manufacturer")
                                if (v.isNotEmpty()) param.result = v
                            }
                            "ro.product.brand" -> {
                                val v = config.getString("build.brand")
                                if (v.isNotEmpty()) param.result = v
                            }
                        }
                    }
                }
            )
        } catch (_: Throwable) {
            XposedBridge.log("[$TAG] SystemProperties hook uyarısı (opsiyonel)")
        }

        // İki parametreli versiyon (default value ile)
        try {
            XposedHelpers.findAndHookMethod(
                "android.os.SystemProperties", cl,
                "get",
                String::class.java, String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val key = param.args[0] as? String ?: return
                        when (key) {
                            "persist.sys.timezone" -> if (tz.isNotEmpty()) param.result = tz
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        XposedBridge.log("[$TAG] System properties hook'ları kuruldu")
    }
}
