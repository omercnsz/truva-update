package com.truva

import kotlinx.coroutines.flow.asStateFlow

enum class VpnState {
    IDLE,          // Başlangıç durumu
    CONNECTING,    // Bağlantı kuruluyor
    CONNECTED,     // Güvenli tünel aktif
    DISCONNECTING, // Bağlantı kesiliyor
    ERROR,         // Bir hata oluştu
    GAMING,        // Oyun Modu aktif
    NITRO_DPI      // Nitro Oyun (Lokal DPI) aktif
}

// Servis ve UI arasında köprü kuracak Singleton
object VpnStatusManager {
    private val _status = kotlinx.coroutines.flow.MutableStateFlow(VpnState.IDLE)
    val status = _status.asStateFlow()

    private val _activeServer = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val activeServer = _activeServer.asStateFlow()

    private val _errorMessage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    fun update(newState: VpnState, serverName: String? = null) {
        _status.value = newState
        when {
            serverName != null -> _activeServer.value = serverName
            newState == VpnState.IDLE || newState == VpnState.ERROR -> _activeServer.value = null
        }
        // Hata değilse mesajı temizle
        if (newState != VpnState.ERROR) {
            _errorMessage.value = null
        }
    }

    fun error(message: String) {
        _errorMessage.value = message
        _status.value = VpnState.ERROR
        _activeServer.value = null
    }
}