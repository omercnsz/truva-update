package com.truva.nidg

import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * QUIC / UDP İstatistiksel Akış Analizi
 *
 * QUIC trafiği TLS 1.3 ile tamamen opaktır; paket numaraları okunamaz.
 * Bu nedenle istatistiksel çıkarım modeli kullanılır:
 *
 *   - UDP Jitter: Paketler arası varış zamanı varyasyonu
 *   - Inter-Arrival Time (IAT): Paket çiftleri arası süre
 *   - Connection Drop Rate: Akışın bitmeden kesilme oranı
 *   - Burst Loss Probability: Burst sırasında kayıp olasılığı
 *
 * Önemli: QUIC metrikleri TCP retransmission kadar kesin değildir.
 * Confidence Score hesabında QUIC verisi daha düşük ağırlık taşır.
 */
class QuicJitterAnalyzer {

    companion object {
        private const val TAG = "NidgQuicJitter"
        private const val IAT_HISTORY_SIZE = 200    // Son 200 IAT örneği
        private const val BURST_THRESHOLD_MS = 5    // 5ms'den kısa aralık = burst
        private const val GAP_THRESHOLD_MS = 1000   // 1s'den uzun boşluk = muhtemel drop
        private const val JITTER_SNAPSHOT_INTERVAL = 50  // Her 50 QUIC paketinde snapshot
    }

    data class JitterSnapshot(
        val avgIatMs: Double,           // Ortalama Inter-Arrival Time
        val jitterMs: Double,           // IAT standart sapması
        val burstCount: Long,           // Burst (< 5ms aralık) sayısı
        val gapCount: Long,             // Gap (> 1s boşluk) sayısı
        val connectionDropRate: Double, // Akış kesinti oranı (0-1)
        val burstLossProbability: Double, // Burst sırasında kayıp olasılığı
        val totalQuicPackets: Long,
        val jitterIndex: Double,        // Normalize jitter endeksi (0-1)
        val timestampMs: Long = System.currentTimeMillis()
    )

    // ── IAT Geçmişi ──
    private val iatHistory = CopyOnWriteArrayList<Double>()
    private var lastPacketTimeMs = 0L

    // ── Sayaçlar ──
    private val _totalQuicPackets = AtomicLong(0)
    private val _burstCount = AtomicLong(0)
    private val _gapCount = AtomicLong(0)
    private val _packetCounter = AtomicLong(0)

    @Volatile
    var lastSnapshot: JitterSnapshot? = null
        private set

    /**
     * QUIC (UDP port 443/80) paketini analiz eder.
     * @return JitterSnapshot (her SNAPSHOT_INTERVAL paketinde) veya null
     */
    fun analyze(packet: ParsedPacket): JitterSnapshot? {
        if (!packet.isQuic) return null

        _totalQuicPackets.incrementAndGet()
        val now = packet.timestampMs

        if (lastPacketTimeMs > 0) {
            val iat = (now - lastPacketTimeMs).toDouble()

            // IAT geçmişine ekle
            iatHistory.add(iat)
            while (iatHistory.size > IAT_HISTORY_SIZE) iatHistory.removeAt(0)

            // Burst tespiti
            if (iat < BURST_THRESHOLD_MS) {
                _burstCount.incrementAndGet()
            }

            // Gap tespiti (muhtemel connection drop)
            if (iat > GAP_THRESHOLD_MS) {
                _gapCount.incrementAndGet()
            }
        }
        lastPacketTimeMs = now

        val count = _packetCounter.incrementAndGet()
        return if (count % JITTER_SNAPSHOT_INTERVAL == 0L) {
            createSnapshot()
        } else null
    }

    private fun createSnapshot(): JitterSnapshot {
        val iats = iatHistory.toList()
        if (iats.isEmpty()) {
            return JitterSnapshot(0.0, 0.0, 0, 0, 0.0, 0.0, 0, 0.0)
        }

        val avgIat = iats.average()
        val variance = iats.map { (it - avgIat) * (it - avgIat) }.average()
        val jitter = kotlin.math.sqrt(variance)

        val totalPackets = _totalQuicPackets.get()
        val gaps = _gapCount.get()
        val bursts = _burstCount.get()

        // Connection Drop Rate: Gap'lerin toplam pakete oranı
        val dropRate = if (totalPackets > 0) gaps.toDouble() / totalPackets else 0.0

        // Burst Loss Probability: Burst yoğunluğu ile gap korelasyonu
        val burstLoss = if (bursts > 0 && totalPackets > 0) {
            (gaps.toDouble() / bursts).coerceIn(0.0, 1.0)
        } else 0.0

        // Jitter Index: 0-1 arası normalize (100ms referans)
        val jitterIndex = (jitter / 100.0).coerceIn(0.0, 1.0)

        val snapshot = JitterSnapshot(
            avgIatMs = avgIat,
            jitterMs = jitter,
            burstCount = bursts,
            gapCount = gaps,
            connectionDropRate = dropRate,
            burstLossProbability = burstLoss,
            totalQuicPackets = totalPackets,
            jitterIndex = jitterIndex
        )
        lastSnapshot = snapshot
        return snapshot
    }

    /** Anlık rapor verisi */
    fun getReportData(): JitterSnapshot {
        return lastSnapshot ?: JitterSnapshot(0.0, 0.0, 0, 0, 0.0, 0.0, 0, 0.0)
    }

    fun reset() {
        iatHistory.clear()
        lastPacketTimeMs = 0
        _totalQuicPackets.set(0)
        _burstCount.set(0)
        _gapCount.set(0)
        _packetCounter.set(0)
        lastSnapshot = null
    }
}
