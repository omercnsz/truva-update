package com.truva.xposed.hooks

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.truva.xposed.SpoofConfig

/**
 * TelephonyHooks — SIM & Operatör Spoofing
 *
 * Hook edilen API'ler:
 * - TelephonyManager.getSimCountryIso()     → sahte SIM ülke kodu
 * - TelephonyManager.getSimOperator()        → sahte MCC+MNC
 * - TelephonyManager.getSimOperatorName()    → sahte operatör adı
 * - TelephonyManager.getNetworkCountryIso()  → sahte ağ ülke kodu
 * - TelephonyManager.getNetworkOperator()    → sahte ağ MCC+MNC
 * - TelephonyManager.getNetworkOperatorName() → sahte ağ operatör adı
 * - TelephonyManager.getPhoneType()          → sahte telefon tipi
 */
object TelephonyHooks {

    private const val TAG = "TruvaHook.SIM"

    fun install(lpparam: XC_LoadPackage.LoadPackageParam, config: SpoofConfig) {
        val cl = lpparam.classLoader

        // ── getSimCountryIso ──
        val simCountryIso = config.getString("sim.countryIso")
        if (simCountryIso.isNotEmpty()) {
            XposedHelpers.findAndHookMethod(
                "android.telephony.TelephonyManager", cl,
                "getSimCountryIso",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = simCountryIso
                    }
                }
            )
            XposedBridge.log("[$TAG] getSimCountryIso → $simCountryIso")
        }

        // ── getSimOperator ──
        val simOperator = config.getString("sim.operator")
        if (simOperator.isNotEmpty()) {
            XposedHelpers.findAndHookMethod(
                "android.telephony.TelephonyManager", cl,
                "getSimOperator",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = simOperator
                    }
                }
            )
            XposedBridge.log("[$TAG] getSimOperator → $simOperator")
        }

        // ── getSimOperatorName ──
        val simOperatorName = config.getString("sim.operatorName")
        if (simOperatorName.isNotEmpty()) {
            XposedHelpers.findAndHookMethod(
                "android.telephony.TelephonyManager", cl,
                "getSimOperatorName",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = simOperatorName
                    }
                }
            )
            XposedBridge.log("[$TAG] getSimOperatorName → $simOperatorName")
        }

        // ── getNetworkCountryIso ──
        val networkCountryIso = config.getString("network.countryIso")
        if (networkCountryIso.isNotEmpty()) {
            XposedHelpers.findAndHookMethod(
                "android.telephony.TelephonyManager", cl,
                "getNetworkCountryIso",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = networkCountryIso
                    }
                }
            )
            XposedBridge.log("[$TAG] getNetworkCountryIso → $networkCountryIso")
        }

        // ── getNetworkOperator ──
        val networkOperator = config.getString("network.operator")
        if (networkOperator.isNotEmpty()) {
            XposedHelpers.findAndHookMethod(
                "android.telephony.TelephonyManager", cl,
                "getNetworkOperator",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = networkOperator
                    }
                }
            )
            XposedBridge.log("[$TAG] getNetworkOperator → $networkOperator")
        }

        // ── getNetworkOperatorName ──
        val networkOperatorName = config.getString("network.operatorName")
        if (networkOperatorName.isNotEmpty()) {
            XposedHelpers.findAndHookMethod(
                "android.telephony.TelephonyManager", cl,
                "getNetworkOperatorName",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = networkOperatorName
                    }
                }
            )
            XposedBridge.log("[$TAG] getNetworkOperatorName → $networkOperatorName")
        }

        // ── getPhoneType — ağ tipine göre GSM/CDMA ──
        XposedHelpers.findAndHookMethod(
            "android.telephony.TelephonyManager", cl,
            "getPhoneType",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    // GSM (1) = çoğu dünya ülkesi
                    param.result = 1 // PHONE_TYPE_GSM
                }
            }
        )
    }
}
