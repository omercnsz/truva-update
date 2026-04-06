package com.truva.nidg

import java.nio.ByteBuffer

/**
 * L4 Header Parser
 *
 * IP/TCP/UDP header'ları parse eder. DPI (Deep Packet Inspection) yapılmaz,
 * sadece header alanları okunur — kullanıcı gizliliği korunur.
 *
 * Desteklenen:
 *   - IPv4 (version 4)
 *   - TCP (protocol 6): Seq, ACK, Flags, payload uzunluğu
 *   - UDP (protocol 17): Port, uzunluk, QUIC tespiti
 *
 * IPv6 desteği Sprint 2 kapsamında.
 */
object L4HeaderParser {

    private const val IP_VERSION_MASK = 0xF0
    private const val IP_IHL_MASK = 0x0F
    private const val IP_MIN_HEADER = 20
    private const val TCP_MIN_HEADER = 20

    // TCP Flag bitmask'leri
    const val TCP_FIN = 0x01
    const val TCP_SYN = 0x02
    const val TCP_RST = 0x04
    const val TCP_PSH = 0x08
    const val TCP_ACK = 0x10

    /**
     * Ham IP paketini parse eder.
     * @param packet Tam IP paketi (TUN'dan okunan ham veri)
     * @return ParsedPacket veya null (geçersiz/desteklenmeyen paket)
     */
    fun parse(packet: ByteArray): ParsedPacket? {
        if (packet.size < IP_MIN_HEADER) return null

        val buf = ByteBuffer.wrap(packet)

        // ── IPv4 Header ──
        val versionIhl = buf.get(0).toInt() and 0xFF
        val version = (versionIhl and IP_VERSION_MASK) ushr 4
        if (version != 4) return null  // Sadece IPv4

        val ihl = (versionIhl and IP_IHL_MASK) * 4
        if (ihl < IP_MIN_HEADER || packet.size < ihl) return null

        val totalLength = buf.getShort(2).toInt() and 0xFFFF
        val protocol = buf.get(9).toInt() and 0xFF
        val srcIp = buf.getInt(12)
        val dstIp = buf.getInt(16)

        return when (protocol) {
            ParsedPacket.PROTO_TCP -> parseTcp(packet, buf, ihl, totalLength, srcIp, dstIp)
            ParsedPacket.PROTO_UDP -> parseUdp(packet, buf, ihl, totalLength, srcIp, dstIp)
            else -> null  // ICMP ve diğerleri Sprint 2
        }
    }

    private fun parseTcp(
        packet: ByteArray,
        buf: ByteBuffer,
        ipHeaderLen: Int,
        totalLength: Int,
        srcIp: Int,
        dstIp: Int
    ): ParsedPacket? {
        val tcpOffset = ipHeaderLen
        if (packet.size < tcpOffset + TCP_MIN_HEADER) return null

        val srcPort = buf.getShort(tcpOffset).toInt() and 0xFFFF
        val dstPort = buf.getShort(tcpOffset + 2).toInt() and 0xFFFF

        // Seq & ACK — unsigned 32-bit olarak oku
        val seqNumber = buf.getInt(tcpOffset + 4).toLong() and 0xFFFFFFFFL
        val ackNumber = buf.getInt(tcpOffset + 8).toLong() and 0xFFFFFFFFL

        // Data Offset (TCP header uzunluğu)
        val dataOffset = ((buf.get(tcpOffset + 12).toInt() and 0xF0) ushr 4) * 4
        if (dataOffset < TCP_MIN_HEADER) return null

        // Flags
        val flags = buf.get(tcpOffset + 13).toInt() and 0xFF

        val headerLength = ipHeaderLen + dataOffset
        val payloadLength = totalLength - headerLength

        return ParsedPacket(
            protocol = ParsedPacket.PROTO_TCP,
            srcIp = srcIp,
            dstIp = dstIp,
            srcPort = srcPort,
            dstPort = dstPort,
            totalLength = totalLength,
            headerLength = headerLength,
            payloadLength = maxOf(0, payloadLength),
            seqNumber = seqNumber,
            ackNumber = ackNumber,
            tcpFlags = flags,
            isSyn = flags and TCP_SYN != 0,
            isFin = flags and TCP_FIN != 0,
            isRst = flags and TCP_RST != 0,
            isPsh = flags and TCP_PSH != 0,
            isAck = flags and TCP_ACK != 0
        )
    }

    private fun parseUdp(
        packet: ByteArray,
        buf: ByteBuffer,
        ipHeaderLen: Int,
        totalLength: Int,
        srcIp: Int,
        dstIp: Int
    ): ParsedPacket? {
        val udpOffset = ipHeaderLen
        if (packet.size < udpOffset + 8) return null  // UDP header = 8 byte

        val srcPort = buf.getShort(udpOffset).toInt() and 0xFFFF
        val dstPort = buf.getShort(udpOffset + 2).toInt() and 0xFFFF
        val udpLength = buf.getShort(udpOffset + 4).toInt() and 0xFFFF

        val headerLength = ipHeaderLen + 8
        val payloadLength = udpLength - 8

        // QUIC tespiti: Port 443 veya 80 üzerinden UDP
        val isQuic = dstPort == 443 || dstPort == 80 || srcPort == 443 || srcPort == 80

        return ParsedPacket(
            protocol = ParsedPacket.PROTO_UDP,
            srcIp = srcIp,
            dstIp = dstIp,
            srcPort = srcPort,
            dstPort = dstPort,
            totalLength = totalLength,
            headerLength = headerLength,
            payloadLength = maxOf(0, payloadLength),
            isQuic = isQuic
        )
    }
}
