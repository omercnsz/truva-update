package com.truva.nidg

import android.content.Context
import android.os.Build
import android.telephony.CellSignalStrengthLte
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Sinyal Telemetri Birimi
 *
 * TelephonyManager üzerinden anlık sinyal kalitesi verisi toplar
 * ve paket kaybıyla zamansal korelasyona sokar.
 *
 * Metrikler:
 *   - RSRP (Referans Sinyal Alım Gücü) — dBm
 *   - RSRQ (Referans Sinyal Kalitesi) — dB
 *
 * Korelasyon Mantığı:
 *   İyi sinyal + paket kaybı → Congestion / Yazılımsal müdahale
 *   Kötü sinyal + paket kaybı → Fiziksel ortam sorunu
 */
class SignalTelemetry(private val context: Context) {

    companion object {
        private const val TAG = "NidgSignal"
        private const val POLL_INTERVAL_MS = 5000L  // 5 saniyede bir
        private const val HISTORY_MAX = 60          // Son 5 dakika (5s × 60 = 300s)
    }

    // ── Sinyal verisi ──
    data class SignalSample(
        val rsrp: Int,              // dBm (-140 ile -44 arası, -140 = çok kötü)
        val rsrq: Int,              // dB (-20 ile -3 arası)
        val timestampMs: Long = System.currentTimeMillis(),
        val networkType: String = "UNKNOWN"
    ) {
        val quality: SignalQuality get() = when {
            rsrp >= -80 -> SignalQuality.EXCELLENT
            rsrp >= -90 -> SignalQuality.GOOD
            rsrp >= -100 -> SignalQuality.FAIR
            rsrp >= -110 -> SignalQuality.POOR
            else -> SignalQuality.NO_SIGNAL
        }
    }

    enum class SignalQuality(val label: String) {
        EXCELLENT("Mükemmel"),
        GOOD("İyi"),
        FAIR("Orta"),
        POOR("Zayıf"),
        NO_SIGNAL("Sinyal Yok")
    }

    data class SignalCorrelation(
        val currentSample: SignalSample?,
        val avgRsrp: Int,
        val avgRsrq: Int,
        val quality: SignalQuality,
        val isStable: Boolean,          // Son 5 ölçümde sapma < 10 dBm
        val sampleCount: Int
    )

    // ── Durum ──
    private val history = CopyOnWriteArrayList<SignalSample>()
    private var pollingJob: Job? = null

    private val _correlation = MutableStateFlow(
        SignalCorrelation(null, -999, -999, SignalQuality.NO_SIGNAL, false, 0)
    )
    val correlation: StateFlow<SignalCorrelation> = _correlation.asStateFlow()

    /**
     * Sinyal yoklamasını başlatır.
     */
    fun start(scope: CoroutineScope) {
        stop()
        pollingJob = scope.launch(Dispatchers.IO) {
            Log.i(TAG, "Sinyal telemetri başladı")
            while (isActive) {
                try {
                    val sample = pollSignal()
                    if (sample != null) {
                        history.add(sample)
                        // Geçmiş limitini koru
                        while (history.size > HISTORY_MAX) history.removeAt(0)
                        updateCorrelation(sample)
                    }
                } catch (e: SecurityException) {
                    Log.w(TAG, "Sinyal izni yok: ${e.message}")
                } catch (e: Exception) {
                    Log.w(TAG, "Sinyal okuma hatası: ${e.message}")
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
    }

    /** Son 5 dakikalık sinyal geçmişini döner */
    fun getHistory(): List<SignalSample> = history.toList()

    /** Belirli bir zaman damgası etrafındaki sinyal kalitesini bulur */
    fun getSignalAt(timestampMs: Long, windowMs: Long = 10_000): SignalSample? {
        return history.minByOrNull {
            kotlin.math.abs(it.timestampMs - timestampMs)
        }?.takeIf {
            kotlin.math.abs(it.timestampMs - timestampMs) <= windowMs
        }
    }

    private fun pollSignal(): SignalSample? {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return null

        try {
            val cellInfoList = tm.allCellInfo ?: return null
            for (cellInfo in cellInfoList) {
                if (!cellInfo.isRegistered) continue
                val css = cellInfo.cellSignalStrength

                // LTE RSRP/RSRQ
                if (css is CellSignalStrengthLte) {
                    val rsrp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        css.rsrp
                    } else {
                        css.dbm  // Fallback
                    }
                    val rsrq = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        css.rsrq
                    } else {
                        -10  // Default
                    }

                    val networkType = when (tm.dataNetworkType) {
                        TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
                        TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
                        TelephonyManager.NETWORK_TYPE_HSDPA,
                        TelephonyManager.NETWORK_TYPE_HSUPA,
                        TelephonyManager.NETWORK_TYPE_HSPA,
                        TelephonyManager.NETWORK_TYPE_HSPAP -> "3G HSPA+"
                        TelephonyManager.NETWORK_TYPE_UMTS -> "3G UMTS"
                        TelephonyManager.NETWORK_TYPE_EDGE -> "2G EDGE"
                        else -> "Diğer"
                    }

                    return SignalSample(rsrp = rsrp, rsrq = rsrq, networkType = networkType)
                }
            }
        } catch (e: SecurityException) {
            // READ_PHONE_STATE izni yok
            Log.w(TAG, "Sinyal okuma izni yok")
        }
        return null
    }

    private fun updateCorrelation(latestSample: SignalSample) {
        val recentSamples = history.takeLast(5)
        val avgRsrp = if (recentSamples.isNotEmpty()) recentSamples.map { it.rsrp }.average().toInt() else -999
        val avgRsrq = if (recentSamples.isNotEmpty()) recentSamples.map { it.rsrq }.average().toInt() else -999

        // Stabilite: Son 5 ölçümde RSRP sapması < 10 dBm
        val isStable = if (recentSamples.size >= 3) {
            val maxRsrp = recentSamples.maxOf { it.rsrp }
            val minRsrp = recentSamples.minOf { it.rsrp }
            (maxRsrp - minRsrp) < 10
        } else false

        _correlation.value = SignalCorrelation(
            currentSample = latestSample,
            avgRsrp = avgRsrp,
            avgRsrq = avgRsrq,
            quality = latestSample.quality,
            isStable = isStable,
            sampleCount = history.size
        )
    }

    fun reset() {
        history.clear()
        _correlation.value = SignalCorrelation(null, -999, -999, SignalQuality.NO_SIGNAL, false, 0)
    }
}
