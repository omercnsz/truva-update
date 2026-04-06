package com.truva.nidg

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Kalibrasyon Motoru
 *
 * Operatörün kendi sayacıyla kıyaslama yapabilmek için
 * Kalibrasyon Katsayısı (K) hesaplar.
 *
 * K = OperatörTüketim / BizimÖlçüm
 *
 * Anomali Tespiti:
 *   K katsayısı sabit kalması gerekir (VPN overhead sabit).
 *   Haftalık K değerinde anlamlı sapma → Operatör sayacında değişiklik uyarısı.
 */
class CalibrationEngine {

    companion object {
        private const val TAG = "NidgCalibration"
        private const val CALIBRATION_DOWNLOAD_BYTES = 10 * 1024 * 1024L  // 10 MB
        private const val K_ANOMALY_THRESHOLD = 0.15  // %15 sapma = anomali
    }

    data class CalibrationResult(
        val kFactor: Double,                    // Kalibrasyon katsayısı
        val truvaBytes: Long,                   // Truva'nın ölçtüğü byte
        val operatorBytes: Long,                // Operatörün gösterdiği byte
        val timestampMs: Long = System.currentTimeMillis(),
        val isCalibrated: Boolean = false
    )

    data class CalibrationStatus(
        val isCalibrated: Boolean = false,
        val kFactor: Double = 1.0,
        val lastCalibrationMs: Long = 0,
        val kHistory: List<Double> = emptyList(),
        val hasAnomaly: Boolean = false,
        val anomalyMessage: String? = null
    )

    // ── Durum ──
    private val kHistory = mutableListOf<Double>()
    private var currentK = 1.0
    private var isCalibrated = false
    private var lastCalibrationMs = 0L

    private val _status = MutableStateFlow(CalibrationStatus())
    val status: StateFlow<CalibrationStatus> = _status.asStateFlow()

    /**
     * Kalibrasyon Adım 1: Truva'nın ölçtüğü byte miktarını kaydeder.
     * (Kontrollü indirme sonrası tun'daki byte)
     */
    fun recordTruvaBytes(bytes: Long): Long {
        Log.i(TAG, "Truva ölçümü: $bytes bytes")
        return bytes
    }

    /**
     * Kalibrasyon Adım 2: Kullanıcı operatör uygulamasından tüketilen miktarı girer.
     * K katsayısı hesaplanır.
     */
    fun calibrate(truvaBytes: Long, operatorBytes: Long): CalibrationResult {
        if (truvaBytes <= 0) {
            Log.w(TAG, "Geçersiz Truva ölçümü: $truvaBytes")
            return CalibrationResult(1.0, truvaBytes, operatorBytes)
        }

        val k = operatorBytes.toDouble() / truvaBytes.toDouble()
        currentK = k
        isCalibrated = true
        lastCalibrationMs = System.currentTimeMillis()

        kHistory.add(k)
        // Maksimum 52 haftalık geçmiş (1 yıl)
        while (kHistory.size > 52) kHistory.removeAt(0)

        Log.i(TAG, "Kalibrasyon tamamlandı: K=${"%.4f".format(k)} (operator=$operatorBytes / truva=$truvaBytes)")

        // Anomali kontrolü
        checkAnomaly()

        updateStatus()
        return CalibrationResult(k, truvaBytes, operatorBytes, isCalibrated = true)
    }

    /**
     * Ölçülen byte'ı K katsayısıyla düzeltir.
     */
    fun correctBytes(measuredBytes: Long): Long {
        return if (isCalibrated) {
            (measuredBytes * currentK).toLong()
        } else {
            measuredBytes
        }
    }

    /**
     * Haftalık K anomali tespiti.
     * K katsayısı sabit kalması gerekir; anlamlı sapma operatör sayaç değişikliği gösterir.
     */
    private fun checkAnomaly() {
        if (kHistory.size < 3) return  // Minimum 3 ölçüm gerekli

        val recentK = kHistory.takeLast(3)
        val avgK = recentK.average()
        val deviation = recentK.map { kotlin.math.abs(it - avgK) / avgK }.max()

        val hasAnomaly = deviation > K_ANOMALY_THRESHOLD
        if (hasAnomaly) {
            Log.w(TAG, "K anomali tespit edildi! Sapma: ${"%.1f".format(deviation * 100)}%")
        }
    }

    private fun updateStatus() {
        val recentK = kHistory.takeLast(3)
        val hasAnomaly = if (recentK.size >= 3) {
            val avgK = recentK.average()
            val deviation = recentK.map { kotlin.math.abs(it - avgK) / avgK }.max()
            deviation > K_ANOMALY_THRESHOLD
        } else false

        _status.value = CalibrationStatus(
            isCalibrated = isCalibrated,
            kFactor = currentK,
            lastCalibrationMs = lastCalibrationMs,
            kHistory = kHistory.toList(),
            hasAnomaly = hasAnomaly,
            anomalyMessage = if (hasAnomaly) "Operatör sayacında değişiklik tespit edildi" else null
        )
    }

    fun reset() {
        kHistory.clear()
        currentK = 1.0
        isCalibrated = false
        lastCalibrationMs = 0
        _status.value = CalibrationStatus()
    }
}
