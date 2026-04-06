package com.truva.nidg

import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Kara Kutu Loglama Sistemi
 *
 * Son 5 dakika sürekli loglanır, spike anında log dondurulur.
 * Kayıp anı ile sinyal dalgalanması arasındaki Zaman Kayması (Time Offset) hesaplanır.
 *
 * Her 30 saniyede bir periyodik snapshot alınır.
 * Packet Loss Spike veya TCP Retransmission Burst yakalandığında
 * son 5 dakikalık log "freeze" edilir.
 */
class BlackBoxLogger {

    companion object {
        private const val TAG = "NidgBlackBox"
        private const val MAX_ENTRIES = 600       // 5 dakika × 2 entry/sn (sinyal + paket)
        private const val SNAPSHOT_INTERVAL = 30  // 30 saniyede bir snapshot
        private const val RETRANSMISSION_SPIKE_THRESHOLD = 0.05  // %5 retransmission = spike
    }

    data class BlackBoxEntry(
        val timestampMs: Long,
        val type: EntryType,
        val nerPercent: Double = 100.0,
        val retransmissionRate: Double = 0.0,
        val rsrp: Int = -999,
        val rsrq: Int = -999,
        val totalPackets: Long = 0,
        val retransmittedPackets: Long = 0,
        val droppedPackets: Long = 0,
        val note: String = ""
    )

    enum class EntryType {
        PERIODIC,       // 30s periyodik snapshot
        SPIKE_FREEZE,   // Retransmission burst yakandı
        SIGNAL_DROP,    // Sinyal düşüşü yakalandı
        MANUAL          // Manuel tetikleme
    }

    data class FrozenLog(
        val triggerTimestampMs: Long,
        val triggerReason: String,
        val entries: List<BlackBoxEntry>,
        val timeOffsetMs: Long   // Spike anı ile en yakın sinyal düşüşü arasındaki fark
    )

    // ── Durum ──
    private val entries = CopyOnWriteArrayList<BlackBoxEntry>()
    private val frozenLogs = CopyOnWriteArrayList<FrozenLog>()
    private var lastSnapshotTime = 0L

    /**
     * Periyodik snapshot kaydeder.
     * NidgEngine tarafından her REPORT_UPDATE çağrısında tetiklenir.
     */
    fun recordSnapshot(
        nerPercent: Double,
        retransmissionRate: Double,
        rsrp: Int,
        rsrq: Int,
        totalPackets: Long,
        retransmittedPackets: Long,
        droppedPackets: Long
    ) {
        val now = System.currentTimeMillis()
        if (now - lastSnapshotTime < SNAPSHOT_INTERVAL * 1000) return

        lastSnapshotTime = now

        val entry = BlackBoxEntry(
            timestampMs = now,
            type = EntryType.PERIODIC,
            nerPercent = nerPercent,
            retransmissionRate = retransmissionRate,
            rsrp = rsrp,
            rsrq = rsrq,
            totalPackets = totalPackets,
            retransmittedPackets = retransmittedPackets,
            droppedPackets = droppedPackets
        )

        entries.add(entry)
        trimEntries()

        // Otomatik spike tespiti
        if (retransmissionRate > RETRANSMISSION_SPIKE_THRESHOLD) {
            freezeLog("Retransmission spike: %.1f%%".format(retransmissionRate * 100))
        }
    }

    /**
     * Sinyal düşüşü kaydı — SignalTelemetry tarafından tetiklenir.
     */
    fun recordSignalDrop(rsrp: Int, rsrq: Int, previousRsrp: Int) {
        val drop = previousRsrp - rsrp
        if (drop > 15) {  // 15 dBm'den fazla düşüş
            val entry = BlackBoxEntry(
                timestampMs = System.currentTimeMillis(),
                type = EntryType.SIGNAL_DROP,
                rsrp = rsrp,
                rsrq = rsrq,
                note = "Sinyal düşüşü: $previousRsrp → $rsrp dBm (Δ$drop)"
            )
            entries.add(entry)
            trimEntries()

            Log.i(TAG, "Sinyal düşüşü kaydedildi: Δ${drop} dBm")
        }
    }

    /**
     * Spike anında son 5 dakikalık logu dondurur.
     */
    fun freezeLog(reason: String) {
        val now = System.currentTimeMillis()
        val fiveMinAgo = now - 5 * 60 * 1000
        val recentEntries = entries.filter { it.timestampMs >= fiveMinAgo }

        if (recentEntries.isEmpty()) return

        // Time Offset: Spike anı ile en yakın sinyal düşüşü arasındaki fark
        val signalDrops = recentEntries.filter { it.type == EntryType.SIGNAL_DROP }
        val timeOffset = if (signalDrops.isNotEmpty()) {
            val nearestDrop = signalDrops.minByOrNull { kotlin.math.abs(it.timestampMs - now) }!!
            now - nearestDrop.timestampMs
        } else 0L

        val frozen = FrozenLog(
            triggerTimestampMs = now,
            triggerReason = reason,
            entries = recentEntries.toList(),
            timeOffsetMs = timeOffset
        )

        frozenLogs.add(frozen)
        // Maksimum 10 frozen log tut
        while (frozenLogs.size > 10) frozenLogs.removeAt(0)

        Log.i(TAG, "Log donduruldu: $reason (${recentEntries.size} girdi, offset=${timeOffset}ms)")
    }

    /** Son dondurulan logları döner */
    fun getFrozenLogs(): List<FrozenLog> = frozenLogs.toList()

    /** Güncel kara kutu girdilerini döner */
    fun getEntries(): List<BlackBoxEntry> = entries.toList()

    /** Kara kutuyu sıfırla */
    fun reset() {
        entries.clear()
        frozenLogs.clear()
        lastSnapshotTime = 0
    }

    private fun trimEntries() {
        while (entries.size > MAX_ENTRIES) entries.removeAt(0)
    }
}
