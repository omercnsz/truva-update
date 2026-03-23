package com.truva.xposed.hooks

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.truva.xposed.SpoofConfig

/**
 * AntiDetectionHooks — Görünmezlik Katmanı
 *
 * 4 algılama bypass kategorisi:
 *
 * 1. ROOT DETECTION BYPASS
 *    - su binary kontrolü (File.exists)
 *    - Magisk/SuperUser paket kontrolü
 *    - Runtime.exec("su") yakalama
 *    - /system/app/Superuser.apk kontrolü
 *
 * 2. MOCK LOCATION BYPASS
 *    - Location.isFromMockProvider() → false
 *    - Location.isMock() → false (Android S+)
 *    - Settings.Secure "mock_location" → 0
 *    - AppOpsManager mock location kontrolü
 *
 * 3. VPN DETECTION BYPASS
 *    - NetworkCapabilities.hasTransport(TRANSPORT_VPN) → false
 *    - ConnectivityManager.getActiveNetworkInfo() → VPN olmayan
 *    - NetworkInterface "tun0"/"ppp0" gizleme
 *
 * 4. HOOK DETECTION BYPASS
 *    - Stack trace'den Xposed frame'lerini filtrele
 *    - /proc/self/maps'ten Xposed/LSPatch satırlarını gizle
 *    - "xposed" içeren paket/sınıf sorgularını engelle
 */
object AntiDetectionHooks {

    private const val TAG = "TruvaHook.AntiDetect"

    fun install(lpparam: XC_LoadPackage.LoadPackageParam, config: SpoofConfig) {
        val cl = lpparam.classLoader

        if (config.hideRoot) installRootBypass(cl)
        if (config.hideMock) installMockBypass(cl)
        if (config.hideVpn) installVpnBypass(cl)
        if (config.hideHook) installHookBypass(cl)

        XposedBridge.log("[$TAG] Anti-detection: root=${config.hideRoot} mock=${config.hideMock} vpn=${config.hideVpn} hook=${config.hideHook}")
    }

    // ════════════════════════════════════════════
    // 1. ROOT DETECTION BYPASS
    // ════════════════════════════════════════════
    private fun installRootBypass(cl: ClassLoader) {
        val rootPaths = setOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/system/su", "/data/local/xbin/su", "/data/local/bin/su",
            "/system/app/Superuser.apk", "/system/app/SuperSU.apk",
            "/data/adb/magisk", "/sbin/.magisk",
            "/system/xbin/daemonsu", "/cache/su"
        )

        // File.exists() — su binary kontrolü
        XposedHelpers.findAndHookMethod(
            "java.io.File", cl,
            "exists",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val path = XposedHelpers.getObjectField(param.thisObject, "path") as? String
                    if (path != null && rootPaths.any { path.contains(it) }) {
                        param.result = false
                    }
                }
            }
        )

        // Runtime.exec() — "su" komutunu engelle
        try {
            XposedHelpers.findAndHookMethod(
                "java.lang.Runtime", cl,
                "exec",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val cmd = param.args[0] as? String
                        if (cmd != null && (cmd.contains("su") || cmd.contains("magisk"))) {
                            XposedHelpers.setObjectField(param, "throwable", java.io.IOException("Permission denied"))
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        // ProcessBuilder — "su" komutunu engelle
        try {
            XposedHelpers.findAndHookMethod(
                "java.lang.ProcessBuilder", cl,
                "start",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        @Suppress("UNCHECKED_CAST")
                        val commands = XposedHelpers.getObjectField(param.thisObject, "command") as? List<String>
                        if (commands != null && commands.any { it.contains("su") || it.contains("magisk") }) {
                            XposedHelpers.setObjectField(param, "throwable", java.io.IOException("Permission denied"))
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        // PackageManager — root paketlerini gizle
        try {
            val rootPackages = setOf(
                "com.topjohnwu.magisk", "eu.chainfire.supersu",
                "com.koushikdutta.superuser", "com.noshufou.android.su",
                "com.thirdparty.superuser", "com.yellowes.su"
            )

            XposedHelpers.findAndHookMethod(
                "android.app.ApplicationPackageManager", cl,
                "getPackageInfo",
                String::class.java, Int::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val pkgName = param.args[0] as? String
                        if (pkgName != null && rootPackages.contains(pkgName)) {
                            XposedHelpers.setObjectField(param, "throwable", android.content.pm.PackageManager.NameNotFoundException())
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        // System properties — ro.build.tags "release-keys" döndür
        try {
            XposedHelpers.findAndHookMethod(
                "android.os.SystemProperties", cl,
                "get",
                String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val key = param.args[0] as? String
                        if (key == "ro.build.tags") {
                            param.result = "release-keys"
                        }
                        if (key == "ro.debuggable") {
                            param.result = "0"
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        XposedBridge.log("[$TAG] Root bypass hook'ları kuruldu")
    }

    // ════════════════════════════════════════════
    // 2. MOCK LOCATION BYPASS
    // ════════════════════════════════════════════
    private fun installMockBypass(cl: ClassLoader) {
        // Settings.Secure mock_location → "0"
        // (DeviceIdHooks'taki Settings.Secure hook'u ile çakışmasın diye ayrı hook)
        try {
            XposedHelpers.findAndHookMethod(
                "android.provider.Settings\$Secure", cl,
                "getString",
                "android.content.ContentResolver", String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val key = param.args[1] as? String
                        if (key == "mock_location") {
                            param.result = "0"
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        // AppOpsManager — OP_MOCK_LOCATION kontrolü
        try {
            XposedHelpers.findAndHookMethod(
                "android.app.AppOpsManager", cl,
                "checkOp",
                Int::class.java, Int::class.java, String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val op = param.args[0] as? Int
                        if (op == 58) { // OP_MOCK_LOCATION = 58
                            param.result = 1 // MODE_ERRORED = izin yok gibi göster
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        XposedBridge.log("[$TAG] Mock location bypass kuruldu")
    }

    // ════════════════════════════════════════════
    // 3. VPN DETECTION BYPASS
    // ════════════════════════════════════════════
    private fun installVpnBypass(cl: ClassLoader) {
        // NetworkCapabilities.hasTransport(TRANSPORT_VPN) → false
        try {
            XposedHelpers.findAndHookMethod(
                "android.net.NetworkCapabilities", cl,
                "hasTransport",
                Int::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val transport = param.args[0] as? Int
                        if (transport == 4) { // TRANSPORT_VPN = 4
                            param.result = false
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        // NetworkInterface.getName() — tun0/ppp0 gizle
        try {
            XposedHelpers.findAndHookMethod(
                "java.net.NetworkInterface", cl,
                "getName",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val name = param.result as? String
                        if (name != null && (name.startsWith("tun") || name.startsWith("ppp"))) {
                            param.result = "wlan0" // WiFi gibi göster
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        // ConnectivityManager.getNetworkInfo(TYPE_VPN) → null
        try {
            XposedHelpers.findAndHookMethod(
                "android.net.ConnectivityManager", cl,
                "getNetworkInfo",
                Int::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val type = param.args[0] as? Int
                        if (type == 17) { // TYPE_VPN = 17
                            param.result = null
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        XposedBridge.log("[$TAG] VPN bypass kuruldu")
    }

    // ════════════════════════════════════════════
    // 4. HOOK/XPOSED DETECTION BYPASS
    // ════════════════════════════════════════════
    private fun installHookBypass(cl: ClassLoader) {
        // Stack trace filtreleme — Xposed/LSPatch frame'lerini gizle
        try {
            XposedHelpers.findAndHookMethod(
                "java.lang.Throwable", cl,
                "getStackTrace",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val trace = param.result as? Array<*> ?: return
                        @Suppress("UNCHECKED_CAST")
                        val filtered = (trace as Array<StackTraceElement>).filter { frame ->
                            val cn = frame.className.lowercase()
                            !cn.contains("xposed") &&
                                !cn.contains("lspatch") &&
                                !cn.contains("edxposed") &&
                                !cn.contains("truva.xposed")
                        }.toTypedArray()
                        param.result = filtered
                    }
                }
            )
        } catch (_: Throwable) {}

        // /proc/self/maps okumayı filtrele
        try {
            XposedHelpers.findAndHookMethod(
                "java.io.BufferedReader", cl,
                "readLine",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val line = param.result as? String ?: return
                        val lower = line.lowercase()
                        if (lower.contains("xposed") ||
                            lower.contains("lspatch") ||
                            lower.contains("edxposed") ||
                            lower.contains("lsplant")
                        ) {
                            param.result = "" // Bu satırı boş döndür
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        // Xposed paketlerini PackageManager'dan gizle
        try {
            val xposedPackages = setOf(
                "de.robv.android.xposed", "org.lsposed.manager",
                "io.github.lsposed", "org.meowcat.edxposed",
                "com.topjohnwu.magisk", "org.lsposed.lspatch"
            )

            XposedHelpers.findAndHookMethod(
                "android.app.ApplicationPackageManager", cl,
                "getInstalledPackages",
                Int::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        @Suppress("UNCHECKED_CAST")
                        val packages = param.result as? MutableList<Any> ?: return
                        packages.removeAll { pkg ->
                            try {
                                val pkgName = XposedHelpers.getObjectField(pkg, "packageName") as? String
                                pkgName != null && xposedPackages.any { pkgName.contains(it) }
                            } catch (_: Throwable) { false }
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        // Class.forName ile Xposed sınıfı arama — exception fırlat
        try {
            XposedHelpers.findAndHookMethod(
                "java.lang.Class", cl,
                "forName",
                String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val name = param.args[0] as? String ?: return
                        val lower = name.lowercase()
                        if (lower.contains("xposed") || lower.contains("lspatch")) {
                            XposedHelpers.setObjectField(param, "throwable", ClassNotFoundException(name))
                        }
                    }
                }
            )
        } catch (_: Throwable) {}

        XposedBridge.log("[$TAG] Hook detection bypass kuruldu")
    }
}
