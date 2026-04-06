package com.truva.nidg

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.truva.MyVpnService
import com.truva.VpnState
import com.truva.VpnStatusManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * NIDG VPN Yöneticisi
 *
 * NIDG sayfasındaki bağımsız "analiz VPN" bağlantısını yönetir.
 * Bu VPN herhangi bir proxy sunucuya bağlanmaz — sadece trafiği
 * TUN'dan geçirerek NIDG motorunun analiz etmesini sağlar.
 *
 * Kurallar:
 *   - Sadece mobil veride çalışır (WiFi engellenir)
 *   - Ana VPN aktifken başlatılamaz (Android tek VPN kısıtı)
 *   - Sabit bildirimden kontrol edilebilir
 */
object NidgVpnManager {

    private const val TAG = "NidgVpnManager"

    enum class NidgVpnState {
        IDLE,           // Bağlı değil
        CONNECTING,     // Bağlanıyor
        ACTIVE,         // Analiz VPN aktif
        WIFI_BLOCKED,   // WiFi'da — başlatılamaz
        MAIN_VPN_ACTIVE // Ana VPN çalışıyor — çakışma
    }

    private val _state = MutableStateFlow(NidgVpnState.IDLE)
    val state: StateFlow<NidgVpnState> = _state.asStateFlow()

    /**
     * NIDG analiz VPN'ini başlatır.
     * Ön kontroller: WiFi engeli, ana VPN çakışması.
     */
    fun connect(context: Context): Boolean {
        // WiFi kontrolü
        if (!isMobileData(context)) {
            _state.value = NidgVpnState.WIFI_BLOCKED
            Log.w(TAG, "NIDG VPN başlatılamadı: WiFi aktif, sadece mobil veride çalışır")
            return false
        }

        // Ana VPN kontrolü
        val mainVpnState = VpnStatusManager.status.value
        if (mainVpnState == VpnState.CONNECTED || mainVpnState == VpnState.CONNECTING) {
            _state.value = NidgVpnState.MAIN_VPN_ACTIVE
            Log.w(TAG, "NIDG VPN başlatılamadı: Ana VPN aktif")
            return false
        }

        _state.value = NidgVpnState.CONNECTING
        Log.i(TAG, "NIDG VPN başlatılıyor...")

        val intent = Intent(context, MyVpnService::class.java).apply {
            action = MyVpnService.ACTION_NIDG_CONNECT
        }
        context.startService(intent)
        return true
    }

    /**
     * NIDG analiz VPN'ini durdurur.
     */
    fun disconnect(context: Context) {
        Log.i(TAG, "NIDG VPN durduruluyor...")
        val intent = Intent(context, MyVpnService::class.java).apply {
            action = MyVpnService.ACTION_NIDG_DISCONNECT
        }
        context.startService(intent)
        _state.value = NidgVpnState.IDLE
    }

    /** Durumu günceller (MyVpnService'den çağrılır) */
    fun updateState(newState: NidgVpnState) {
        _state.value = newState
    }

    /** Mobil veri bağlantısı var mı? */
    fun isMobileData(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        } catch (_: Exception) {
            false
        }
    }
}
