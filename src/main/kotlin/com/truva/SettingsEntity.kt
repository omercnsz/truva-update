package com.truva

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 0, // Tek bir satır olacak
    val isKillSwitchEnabled: Boolean = false,
    val isGamingModeEnabled: Boolean = false, // Düşük gecikme modu
    val isVideoOptimizationEnabled: Boolean = true, // Video görüşme optimizasyonu

    // ── Spoofing Ayarları ──
    val isSpoofingEnabled: Boolean = false,          // Genel spoofing anahtarı
    val activeRegionProfileId: String? = null,        // Seçili bölge profili ID'si
    val isSimSpoofEnabled: Boolean = true,            // SIM spoofing aktif mi
    val isGpsSpoofEnabled: Boolean = true,            // GPS spoofing aktif mi
    val isTimezoneSpoofEnabled: Boolean = true,       // Timezone spoofing aktif mi
    val isLocaleSpoofEnabled: Boolean = true,         // Locale spoofing aktif mi
    val isDeviceIdSpoofEnabled: Boolean = true,       // Device ID spoofing aktif mi

    // ── Sandbox Ayarları ──
    val isSandboxEnabled: Boolean = false,            // Sandbox motoru aktif mi
    val isAntiDetectionEnabled: Boolean = true,       // Anti-detection modülü
    val isAutoSyncRegion: Boolean = true,             // Proxy değişince otomatik profil eşleşmesi

    // ── Routing Ayarları ──
    val routingMode: String = "standard",             // "standard", "gaming", "streaming", "anti_censorship"
    val isSmartRoutingEnabled: Boolean = true,         // Akıllı yönlendirme
    val isUdpDirectBypass: Boolean = false,             // UDP doğrudan bypass

    // ── ADB & SIM Koruması ──
    val adbConnectionPort: Int = 0,                    // Kayıtlı ADB bağlantı portu
    val isSimMasked: Boolean = false,                  // AppOps tabanlı SIM gizleme

    // ── Oturum Yönetimi (Kazık Savar) ──
    val sessionExpiryTime: Long = 0L,                  // Oturum bitiş zamanı (epoch ms), 0 = aktif oturum yok

    // ── NIDG (Ağ Analizi) ──
    val isNidgEnabled: Boolean = true,                   // Ağ analiz motoru aktif mi

    // ── Nitro Oyun (Lokal DPI Bypass) ──
    val nitroDpiAppMode: String = "all",                 // "all" veya "selected"
    val nitroDpiApps: String = ""                        // Virgülle ayrılmış paket adları
)
