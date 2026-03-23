package com.truva

/**
 * SessionManager — Kazık Savar 3 saatlik oturum yönetimi
 *
 * Oturum süresi SettingsEntity.sessionExpiryTime alanında kalıcı olarak tutulur.
 * Bu sınıf sadece yardımcı hesaplama fonksiyonları sunar.
 */
object SessionManager {

    /** Varsayılan oturum süresi: 3 saat (ms) */
    const val SESSION_DURATION_MS = 3 * 60 * 60 * 1000L  // 3 saat

    /** Oturumun aktif olup olmadığını kontrol eder */
    fun isSessionActive(expiryTime: Long): Boolean {
        if (expiryTime == 0L) return false
        return System.currentTimeMillis() < expiryTime
    }

    /** Kalan süreyi ms cinsinden döndürür */
    fun remainingTimeMs(expiryTime: Long): Long {
        val remaining = expiryTime - System.currentTimeMillis()
        return if (remaining > 0) remaining else 0
    }

    /** Yeni oturum bitiş zamanını hesaplar */
    fun calculateNewExpiryTime(): Long {
        return System.currentTimeMillis() + SESSION_DURATION_MS
    }

    /** Kalan süreyi "SS:DD:SS" formatında döndürür */
    fun formatRemainingTime(expiryTime: Long): String {
        val remaining = remainingTimeMs(expiryTime)
        if (remaining <= 0) return "00:00:00"
        val hours = remaining / (60 * 60 * 1000)
        val minutes = (remaining % (60 * 60 * 1000)) / (60 * 1000)
        val seconds = (remaining % (60 * 1000)) / 1000
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}
