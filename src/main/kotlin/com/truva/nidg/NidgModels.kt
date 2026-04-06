package com.truva.nidg

/**
 * NIDG Veri Modelleri — Tam NIDG Rapor Seti
 *
 * Tüm NIDG bileşenleri bu modelleri kullanır.
 * DPI yapılmaz — sadece L3/L4 header bilgileri tutulur.
 */

// ═══════════════════════════════════════════
// Parsed Packet — L4HeaderParser çıktısı
// ═══════════════════════════════════════════

data class ParsedPacket(
    val protocol: Int,          // IP protocol numarası (6=TCP, 17=UDP)
    val srcIp: Int,             // Kaynak IP (network byte order)
    val dstIp: Int,             // Hedef IP (network byte order)
    val srcPort: Int,           // Kaynak port
    val dstPort: Int,           // Hedef port
    val totalLength: Int,       // IP toplam uzunluk (header + payload)
    val headerLength: Int,      // IP + Transport header uzunluğu
    val payloadLength: Int,     // Saf veri uzunluğu (totalLength - headerLength)

    // TCP-only alanlar
    val seqNumber: Long = 0,    // TCP Sequence Number (unsigned 32-bit)
    val ackNumber: Long = 0,    // TCP Acknowledgment Number
    val tcpFlags: Int = 0,      // TCP flag bitmask
    val isSyn: Boolean = false,
    val isFin: Boolean = false,
    val isRst: Boolean = false,
    val isPsh: Boolean = false,
    val isAck: Boolean = false,

    // UDP-only alanlar
    val isQuic: Boolean = false, // Port 443/80 + UDP → muhtemel QUIC

    val timestampMs: Long = System.currentTimeMillis()
) {
    val isTcp: Boolean get() = protocol == PROTO_TCP
    val isUdp: Boolean get() = protocol == PROTO_UDP
    val hasPayload: Boolean get() = payloadLength > 0

    companion object {
        const val PROTO_TCP = 6
        const val PROTO_UDP = 17
    }
}

// ═══════════════════════════════════════════
// Flow Key — TCP akış tanımlayıcı (4-tuple)
// ═══════════════════════════════════════════

data class FlowKey(
    val srcIp: Int,
    val srcPort: Int,
    val dstIp: Int,
    val dstPort: Int
)

// ═══════════════════════════════════════════
// NER Snapshot — Anlık verimlilik ölçümü
// ═══════════════════════════════════════════

data class NerSnapshot(
    val totalTcpPackets: Long,
    val retransmittedPackets: Long,
    val retransmittedBytes: Long,
    val totalTcpBytes: Long,
    val totalUdpPackets: Long,
    val totalUdpBytes: Long,
    val nerPercent: Double,         // Network Efficiency Ratio (0-100)
    val timestampMs: Long = System.currentTimeMillis()
)

// ═══════════════════════════════════════════
// NIDG Report — UI'a sunulacak tam rapor
// ═══════════════════════════════════════════

data class NidgReport(
    // ── Byte Sayaçları ──
    val totalBytes: Long = 0,
    val netUsefulBytes: Long = 0,    // totalBytes - overheadBytes
    val overheadBytes: Long = 0,     // retransmission + protocol header

    // ── TCP Metrikleri ──
    val totalTcpPackets: Long = 0,
    val retransmittedPackets: Long = 0,
    val retransmittedBytes: Long = 0,

    // ── UDP / QUIC Metrikleri ──
    val totalUdpPackets: Long = 0,
    val totalUdpBytes: Long = 0,
    val quicPackets: Long = 0,
    val quicJitterMs: Double = 0.0,
    val quicAvgIatMs: Double = 0.0,
    val quicBurstCount: Long = 0,
    val quicGapCount: Long = 0,
    val quicConnectionDropRate: Double = 0.0,
    val quicJitterIndex: Double = 0.0,

    // ── Verimlilik ──
    val nerPercent: Double = 100.0,  // Network Efficiency Ratio

    // ── Sinyal Telemetri ──
    val signalRsrp: Int = -999,     // dBm
    val signalRsrq: Int = -999,     // dB
    val signalQuality: String = "Bilinmiyor",
    val signalNetworkType: String = "Bilinmiyor",
    val signalIsStable: Boolean = false,
    val signalSampleCount: Int = 0,

    // ── Traceroute / Segment ──
    val tunnelRttMs: Long = 0,
    val bypassRttMs: Long = 0,
    val operatorSegmentMs: Long = 0,
    val tunnelHops: Int = 0,
    val bypassHops: Int = 0,
    val tracerouteAvailable: Boolean = false,

    // ── Confidence Score ──
    val confidenceScore: Double = 0.0,
    val confidenceExplanation: String = "",
    val confidenceFlags: List<String> = emptyList(),

    // ── Kalibrasyon ──
    val isCalibrated: Boolean = false,
    val kFactor: Double = 1.0,
    val hasKAnomaly: Boolean = false,

    // ── CDN Filtreleme ──
    val cdnPackets: Long = 0,
    val nonCdnPackets: Long = 0,
    val isCidrStale: Boolean = false,

    // ── Analiz Kalitesi ──
    val droppedAnalysisPackets: Long = 0,
    val activeFlows: Int = 0,
    val analysisUptimeMs: Long = 0,
    val analysisMode: String = "ACTIVE",

    // ── Black Box ──
    val frozenLogCount: Int = 0,

    // ── Durum ──
    val isActive: Boolean = false
) {
    /** MB cinsinden toplam tüketim */
    val totalMB: Double get() = totalBytes / 1_048_576.0

    /** MB cinsinden kayıpsız kullanım */
    val netUsefulMB: Double get() = netUsefulBytes / 1_048_576.0

    /** MB cinsinden overhead */
    val overheadMB: Double get() = overheadBytes / 1_048_576.0
}
