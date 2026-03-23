package com.truva.spoofing

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log
import java.lang.reflect.Proxy

/**
 * SIM Spoofing Manager — TelephonyManager API Proxy
 *
 * Sandbox içindeki uygulamalara sahte SIM/Operatör bilgisi sağlar. Root gerektirmez; Content
 * Provider ve Reflection tabanlı çalışır.
 *
 * Manipüle edilen alanlar:
 * - SIM Country ISO (getSimCountryIso)
 * - SIM Operator Name (getSimOperatorName)
 * - SIM Operator MCC/MNC (getSimOperator)
 * - Network Country ISO (getNetworkCountryIso)
 * - Network Operator Name (getNetworkOperatorName)
 * - Network Operator MCC/MNC (getNetworkOperator)
 */
class SimSpoofManager(private val context: Context) {

    private val tag = "TruvaSimSpoof"

    @Volatile private var activeProfile: RegionProfile? = null

    /** Aktif bölge profilini ayarla; null = spoofing kapalı */
    fun setProfile(profile: RegionProfile?) {
        activeProfile = profile
        if (profile != null) {
            Log.i(tag, "SIM Spoofing aktif: ${profile.simOperatorName} (${profile.mccMnc})")
        } else {
            Log.i(tag, "SIM Spoofing devre dışı")
        }
    }

    fun getProfile(): RegionProfile? = activeProfile

    // ────────────────────────────────────────────
    // Spoofed Getter'lar — Sandbox hook'ları bunları çağırır
    // ────────────────────────────────────────────

    fun getSpoofedSimCountryIso(): String {
        return activeProfile?.simCountryIso ?: getRealSimCountryIso()
    }

    fun getSpoofedSimOperatorName(): String {
        return activeProfile?.simOperatorName ?: getRealSimOperatorName()
    }

    fun getSpoofedSimOperator(): String {
        return activeProfile?.mccMnc ?: getRealSimOperator()
    }

    fun getSpoofedNetworkCountryIso(): String {
        return activeProfile?.networkCountryIso ?: getRealNetworkCountryIso()
    }

    fun getSpoofedNetworkOperatorName(): String {
        return activeProfile?.networkOperatorName ?: getRealNetworkOperatorName()
    }

    fun getSpoofedNetworkOperator(): String {
        return activeProfile?.networkOperatorMccMnc ?: getRealNetworkOperator()
    }

    fun getSpoofedMcc(): String {
        return activeProfile?.simOperatorMcc ?: ""
    }

    fun getSpoofedMnc(): String {
        return activeProfile?.simOperatorMnc ?: ""
    }

    // ────────────────────────────────────────────
    // Gerçek değerler (spoofing kapalıyken fallback)
    // ────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun getTelephonyManager(): TelephonyManager? {
        return try {
            context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        } catch (e: Exception) {
            Log.w(tag, "TelephonyManager erişim hatası", e)
            null
        }
    }

    fun getRealSimCountryIso(): String = getTelephonyManager()?.simCountryIso ?: ""

    fun getRealSimOperatorName(): String = getTelephonyManager()?.simOperatorName ?: ""

    fun getRealSimOperator(): String = getTelephonyManager()?.simOperator ?: ""

    fun getRealNetworkCountryIso(): String = getTelephonyManager()?.networkCountryIso ?: ""

    fun getRealNetworkOperatorName(): String = getTelephonyManager()?.networkOperatorName ?: ""

    fun getRealNetworkOperator(): String = getTelephonyManager()?.networkOperator ?: ""

    // UI Compatibility Aliases
    fun getRealOperatorName(): String = getRealSimOperatorName()
    fun getRealCountryIso(): String = getRealSimCountryIso()

    // ────────────────────────────────────────────
    // API Proxy — TelephonyManager için dinamik proxy
    // ────────────────────────────────────────────

    /**
     * TelephonyManager çağrılarını intercept eden proxy oluşturur. Sandbox'taki uygulama bu proxy'e
     * yönlendirilir.
     */
    fun createTelephonyProxy(): Any {
        val real =
                getTelephonyManager() ?: throw IllegalStateException("TelephonyManager bulunamadı")
        val interfaces = real.javaClass.interfaces

        return Proxy.newProxyInstance(
                real.javaClass.classLoader,
                if (interfaces.isEmpty()) arrayOf(TelephonyManager::class.java) else interfaces
        ) { _, method, args ->
            val profile = activeProfile
            if (profile != null) {
                when (method.name) {
                    "getSimCountryIso" -> return@newProxyInstance profile.simCountryIso
                    "getSimOperatorName" -> return@newProxyInstance profile.simOperatorName
                    "getSimOperator" -> return@newProxyInstance profile.mccMnc
                    "getNetworkCountryIso" -> return@newProxyInstance profile.networkCountryIso
                    "getNetworkOperatorName" -> return@newProxyInstance profile.networkOperatorName
                    "getNetworkOperator" -> return@newProxyInstance profile.networkOperatorMccMnc
                    "getPhoneType" -> return@newProxyInstance TelephonyManager.PHONE_TYPE_GSM
                    "getSimState" -> return@newProxyInstance TelephonyManager.SIM_STATE_READY
                    "getDataState" -> return@newProxyInstance TelephonyManager.DATA_CONNECTED
                    "getNetworkType" -> return@newProxyInstance 13 // LTE
                    "isNetworkRoaming" -> return@newProxyInstance false
                    "hasIccCard" -> return@newProxyInstance true
                }
            }
            // Proxy'den geçmeyen çağrılar gerçek TelephonyManager'a iletilir
            try {
                if (args != null) method.invoke(real, *args) else method.invoke(real)
            } catch (e: Exception) {
                Log.w(tag, "TelephonyManager proxy fallback: ${method.name}", e)
                null
            }
        }
    }

    /** Spoofing durumu özet bilgisi döndürür */
    fun getStatusSummary(): Map<String, String> {
        val profile = activeProfile ?: return mapOf("durum" to "Kapalı")
        return mapOf(
                "durum" to "Aktif",
                "ülke" to profile.simCountryIso.uppercase(),
                "operatör" to profile.simOperatorName,
                "MCC/MNC" to profile.mccMnc
        )
    }
}
