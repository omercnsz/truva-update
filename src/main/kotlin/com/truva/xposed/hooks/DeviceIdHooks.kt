package com.truva.xposed.hooks

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.truva.xposed.SpoofConfig

/**
 * DeviceIdHooks — Cihaz Kimliği Spoofing
 *
 * Hook edilen API'ler:
 * - Settings.Secure.getString("android_id")  → sahte Android ID
 * - TelephonyManager.getDeviceId()           → sahte IMEI
 * - TelephonyManager.getImei()               → sahte IMEI (API 26+)
 * - TelephonyManager.getMeid()               → sahte MEID
 * - TelephonyManager.getSubscriberId()       → sahte IMSI
 * - TelephonyManager.getLine1Number()        → sahte telefon numarası
 * - Build.SERIAL                             → sahte seri no
 * - Build.getSerial()                        → sahte seri no (API 26+)
 *
 * Her yamalı uygulama benzersiz ama tutarlı sahte kimlikler görür.
 * Aynı profil seçili olduğu sürece değerler değişmez.
 */
object DeviceIdHooks {

    private const val TAG = "TruvaHook.DevID"

    fun install(lpparam: XC_LoadPackage.LoadPackageParam, config: SpoofConfig) {
        val cl = lpparam.classLoader

        // ── Settings.Secure.getString — android_id ──
        val androidId = config.getString("sys.androidId")
        if (androidId.isNotEmpty()) {
            XposedHelpers.findAndHookMethod(
                "android.provider.Settings\$Secure", cl,
                "getString",
                "android.content.ContentResolver", String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val key = param.args[1] as? String
                        if (key == "android_id") {
                            param.result = androidId
                        }
                    }
                }
            )
            XposedBridge.log("[$TAG] android_id → $androidId")
        }

        // ── TelephonyManager.getDeviceId() ──
        val deviceId = config.getString("sys.deviceId")
        if (deviceId.isNotEmpty()) {
            // Parametresiz versiyon
            try {
                XposedHelpers.findAndHookMethod(
                    "android.telephony.TelephonyManager", cl,
                    "getDeviceId",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            param.result = deviceId
                        }
                    }
                )
            } catch (_: Throwable) {}

            // Slot parametreli versiyon
            try {
                XposedHelpers.findAndHookMethod(
                    "android.telephony.TelephonyManager", cl,
                    "getDeviceId",
                    Int::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            param.result = deviceId
                        }
                    }
                )
            } catch (_: Throwable) {}

            XposedBridge.log("[$TAG] deviceId → $deviceId")
        }

        // ── TelephonyManager.getImei() (API 26+) ──
        val imei = config.getString("sys.imei")
        if (imei.isNotEmpty()) {
            try {
                XposedHelpers.findAndHookMethod(
                    "android.telephony.TelephonyManager", cl,
                    "getImei",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            param.result = imei
                        }
                    }
                )
            } catch (_: Throwable) {}

            try {
                XposedHelpers.findAndHookMethod(
                    "android.telephony.TelephonyManager", cl,
                    "getImei",
                    Int::class.java,
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            param.result = imei
                        }
                    }
                )
            } catch (_: Throwable) {}

            XposedBridge.log("[$TAG] IMEI → $imei")
        }

        // ── TelephonyManager.getMeid() ──
        try {
            XposedHelpers.findAndHookMethod(
                "android.telephony.TelephonyManager", cl,
                "getMeid",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (imei.isNotEmpty()) param.result = imei
                    }
                }
            )
        } catch (_: Throwable) {}

        // ── TelephonyManager.getSubscriberId() (IMSI) ──
        val mcc = config.getString("sim.mcc")
        val mnc = config.getString("sim.mnc")
        if (mcc.isNotEmpty() && mnc.isNotEmpty()) {
            try {
                XposedHelpers.findAndHookMethod(
                    "android.telephony.TelephonyManager", cl,
                    "getSubscriberId",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            // IMSI: MCC(3) + MNC(2-3) + MSIN(9-10)
                            param.result = "${mcc}${mnc}0123456789"
                        }
                    }
                )
            } catch (_: Throwable) {}
        }

        // ── TelephonyManager.getLine1Number() ──
        try {
            XposedHelpers.findAndHookMethod(
                "android.telephony.TelephonyManager", cl,
                "getLine1Number",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = "" // Numara gösterme
                    }
                }
            )
        } catch (_: Throwable) {}

        // ── Build.SERIAL ──
        val serial = config.getString("sys.serial")
        if (serial.isNotEmpty()) {
            try {
                val buildClass = XposedHelpers.findClass("android.os.Build", cl)
                XposedHelpers.setStaticObjectField(buildClass, "SERIAL", serial)
                XposedBridge.log("[$TAG] Build.SERIAL → $serial")
            } catch (_: Throwable) {}

            // Build.getSerial() (API 26+)
            try {
                XposedHelpers.findAndHookMethod(
                    "android.os.Build", cl,
                    "getSerial",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            param.result = serial
                        }
                    }
                )
            } catch (_: Throwable) {}
        }

        XposedBridge.log("[$TAG] Cihaz kimliği hook'ları kuruldu")
    }
}
