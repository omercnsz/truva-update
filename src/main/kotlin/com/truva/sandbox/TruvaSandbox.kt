package com.truva.sandbox

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.util.Log

/** Sandbox durumu */
enum class SandboxState {
    IDLE, // Hazır
    PREPARING, // Hazırlanıyor
    RUNNING, // Aktif
    ERROR // Hata
}

/**
 * TruvaSandbox — İş Profili Köprüsü
 *
 * Bu sınıf, Truva'nın "İş Profili" (Work Profile) özelliklerini yönetir. Shizuku tabanlı modern
 * mimari ile "APK Yamalama" tamamen kaldırılmıştır.
 */
class TruvaSandbox private constructor(private val context: Context) {

    private val tag = "TruvaSandbox"

    /** Bu Truva kopyasının İş Profilinde çalışıp çalışmadığını kontrol eder */
    val isProfileOwner: Boolean
        get() {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            return dpm.isProfileOwnerApp(context.packageName)
        }

    val workProfileManager = WorkProfileManager(context)

    @Volatile private var _state: SandboxState = SandboxState.IDLE
    val state: SandboxState
        get() = _state

    init {
        // İş profilinde isek cross-profile filtreleri yenile
        refreshCrossProfileFiltersIfNeeded()
    }

    /** İş profilinde profil sahibi isek cross-profile intent filtrelerini yenile ve güvenlik kilidini uygula */
    private fun refreshCrossProfileFiltersIfNeeded() {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            if (dpm.isProfileOwnerApp(context.packageName)) {
                Log.i(tag, "İş profilindeyiz — cross-profile filtreleri yenileniyor...")
                TruvaDeviceAdmin.setupCrossProfileFilters(context)

                // Her başlatmada güvenlik kilitlemesini yeniden uygula
                // (yeni kurulan uygulamaları da kapsar)
                Log.i(tag, "Güvenlik kilitlemesi yeniden uygulanıyor...")
                TruvaDeviceAdmin.applySecurityLockdown(context)
            }
        } catch (e: Exception) {
            Log.d(tag, "Cross-profile / güvenlik kilitleme atlandı: ${e.message}")
        }
    }

    /** Gizlilik motoru durumu */
    fun isEngineReady(): Boolean = true

    /** Sandbox durumu özetini döndür */
    fun getStatusSummary(): SandboxStatus {
        return SandboxStatus(
                isEngineReady = true,
                isWorkProfileActive = workProfileManager.isWorkProfileActive(),
                canCreateWorkProfile = workProfileManager.canCreateWorkProfile(),
                isDeviceCapable = workProfileManager.isDeviceCapable(),
                state = _state
        )
    }

    companion object {
        @Volatile private var INSTANCE: TruvaSandbox? = null

        fun getInstance(context: Context): TruvaSandbox {
            return INSTANCE
                    ?: synchronized(this) {
                        INSTANCE ?: TruvaSandbox(context.applicationContext).also { INSTANCE = it }
                    }
        }
    }
}

/** UI durum bilgisi */
data class SandboxStatus(
        val isEngineReady: Boolean,
        val isWorkProfileActive: Boolean,
        val canCreateWorkProfile: Boolean,
        val isDeviceCapable: Boolean,
        val state: SandboxState
)
