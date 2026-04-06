package com.truva.nidg

import android.util.Log
import androidx.collection.LruCache
import java.util.concurrent.atomic.AtomicLong

/**
 * Retransmission Analyzer
 *
 * Her TCP akışı (srcIP:port → dstIP:port) bağımsız bir FlowTracker ile izlenir.
 * LruCache(256) ile maksimum 256 eş zamanlı akış — OOM riski yoktur.
 *
 * Retransmission tespiti:
 *   Aynı FlowKey için aynı Seq Number 3000ms içinde tekrar görülürse
 *   → retransmission olarak sayılır.
 *
 * SYN-only ve boş ACK'lar NER hesabından hariç tutulur.
 */
class RetransmissionAnalyzer {

    companion object {
        private const val TAG = "NidgRetrAnalyzer"
        private const val MAX_FLOWS = 256
        private const val RETRANSMISSION_WINDOW_MS = 3000L  // RFC 6298 minimum RTO
        private const val SNAPSHOT_INTERVAL = 100           // Her 100 pakette bir snapshot
    }

    // ── Akış Takipçisi ──
    private data class SeqRecord(val seqNumber: Long, val payloadLen: Int, val timestampMs: Long)

    private inner class FlowTracker {
        // Son görülen seq number'ları ve zamanları
        private val recentSeqs = ArrayDeque<SeqRecord>(64)

        /**
         * Paketi analiz eder.
         * @return retransmission ise payload byte sayısı, değilse 0
         */
        fun analyze(packet: ParsedPacket): Int {
            val now = packet.timestampMs

            // Eski kayıtları temizle (pencere dışı)
            while (recentSeqs.isNotEmpty() &&
                   now - recentSeqs.first().timestampMs > RETRANSMISSION_WINDOW_MS) {
                recentSeqs.removeFirst()
            }

            // SYN-only paketleri filtrele (bağlantı kurma)
            if (packet.isSyn && !packet.isAck) return 0

            // Boş ACK'ları filtrele (keepalive)
            if (!packet.hasPayload && packet.isAck && !packet.isSyn && !packet.isFin) return 0

            // Sadece data taşıyan paketleri analiz et
            if (!packet.hasPayload) return 0

            // Bu seq number daha önce görüldü mü?
            val isRetransmission = recentSeqs.any {
                it.seqNumber == packet.seqNumber && it.payloadLen == packet.payloadLength
            }

            // Kaydet
            if (recentSeqs.size >= 64) recentSeqs.removeFirst()
            recentSeqs.addLast(
                SeqRecord(packet.seqNumber, packet.payloadLength, now)
            )

            return if (isRetransmission) packet.payloadLength else 0
        }
    }

    // ── Ana Durum ──
    private val flowCache = LruCache<FlowKey, FlowTracker>(MAX_FLOWS)

    // Sayaçlar (thread-safe)
    private val _totalTcpPackets = AtomicLong(0)
    private val _totalTcpBytes = AtomicLong(0)
    private val _retransmittedPackets = AtomicLong(0)
    private val _retransmittedBytes = AtomicLong(0)
    private val _totalUdpPackets = AtomicLong(0)
    private val _totalUdpBytes = AtomicLong(0)
    private val _quicPackets = AtomicLong(0)
    private val _packetCounter = AtomicLong(0)

    /** Son NerSnapshot (her 100 pakette güncellenir) */
    @Volatile
    var lastSnapshot: NerSnapshot? = null
        private set

    /**
     * Paketi analiz et ve sayaçları güncelle.
     * @return NerSnapshot (her SNAPSHOT_INTERVAL pakette) veya null
     */
    fun analyze(packet: ParsedPacket): NerSnapshot? {
        when {
            packet.isTcp -> analyzeTcp(packet)
            packet.isUdp -> analyzeUdp(packet)
        }

        val count = _packetCounter.incrementAndGet()
        return if (count % SNAPSHOT_INTERVAL == 0L) {
            createSnapshot()
        } else null
    }

    private fun analyzeTcp(packet: ParsedPacket) {
        _totalTcpPackets.incrementAndGet()
        _totalTcpBytes.addAndGet(packet.totalLength.toLong())

        val flowKey = FlowKey(packet.srcIp, packet.srcPort, packet.dstIp, packet.dstPort)
        val tracker = synchronized(flowCache) {
            flowCache.get(flowKey) ?: FlowTracker().also { flowCache.put(flowKey, it) }
        }

        val retransmittedBytes = tracker.analyze(packet)
        if (retransmittedBytes > 0) {
            _retransmittedPackets.incrementAndGet()
            _retransmittedBytes.addAndGet(retransmittedBytes.toLong())
        }
    }

    private fun analyzeUdp(packet: ParsedPacket) {
        _totalUdpPackets.incrementAndGet()
        _totalUdpBytes.addAndGet(packet.totalLength.toLong())
        if (packet.isQuic) {
            _quicPackets.incrementAndGet()
        }
    }

    private fun createSnapshot(): NerSnapshot {
        val tcpTotal = _totalTcpPackets.get()
        val tcpBytes = _totalTcpBytes.get()
        val retrPackets = _retransmittedPackets.get()
        val retrBytes = _retransmittedBytes.get()
        val udpPackets = _totalUdpPackets.get()
        val udpBytes = _totalUdpBytes.get()

        // NER = (1 - retransmission_ratio) × 100
        val tcpNer = if (tcpTotal > 0) {
            (1.0 - retrPackets.toDouble() / tcpTotal) * 100.0
        } else 100.0

        val snapshot = NerSnapshot(
            totalTcpPackets = tcpTotal,
            retransmittedPackets = retrPackets,
            retransmittedBytes = retrBytes,
            totalTcpBytes = tcpBytes,
            totalUdpPackets = udpPackets,
            totalUdpBytes = udpBytes,
            nerPercent = tcpNer.coerceIn(0.0, 100.0)
        )
        lastSnapshot = snapshot
        return snapshot
    }

    /** Aktif akış sayısını döner */
    fun activeFlowCount(): Int = synchronized(flowCache) { flowCache.size() }

    /** Tüm sayaçları ve akış cache'ini sıfırla */
    fun reset() {
        _totalTcpPackets.set(0)
        _totalTcpBytes.set(0)
        _retransmittedPackets.set(0)
        _retransmittedBytes.set(0)
        _totalUdpPackets.set(0)
        _totalUdpBytes.set(0)
        _quicPackets.set(0)
        _packetCounter.set(0)
        lastSnapshot = null
        synchronized(flowCache) { flowCache.evictAll() }
    }

    /** Anlık rapor verisi üret */
    fun getReportData(): ReportData {
        return ReportData(
            totalTcpPackets = _totalTcpPackets.get(),
            totalTcpBytes = _totalTcpBytes.get(),
            retransmittedPackets = _retransmittedPackets.get(),
            retransmittedBytes = _retransmittedBytes.get(),
            totalUdpPackets = _totalUdpPackets.get(),
            totalUdpBytes = _totalUdpBytes.get(),
            quicPackets = _quicPackets.get()
        )
    }

    data class ReportData(
        val totalTcpPackets: Long,
        val totalTcpBytes: Long,
        val retransmittedPackets: Long,
        val retransmittedBytes: Long,
        val totalUdpPackets: Long,
        val totalUdpBytes: Long,
        val quicPackets: Long
    )
}
