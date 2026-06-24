package com.truva.gamemode

/**
 * TruvaRun masaüstü motorunun ClientHello manipülasyon mantığının Kotlin portu.
 *
 * Kaynak (Rust, davranışsal BİREBİR):
 *   - TruvaRun_Logic/src/windivert/parser.rs :: parse_tls_client_hello
 *   - TruvaRun_Logic/src/windivert/strategy/sni_split.rs   (sni-mid-split)
 *   - TruvaRun_Logic/src/windivert/strategy/tls_rec_split.rs (tls-rec-split)
 *
 * Nitro Oyun (Oyun Modu) manipülasyonu: ClientHello TCP AKIŞI seviyesinde
 * SNI ortasından bölünür (iki ayrı segment) ya da TLS record ikiye ayrılır.
 * DesyncProxy bu mantığı Xray'in çıkış trafiğine uygular.
 */
object TlsClientHello {

    private const val TLS_HANDSHAKE: Int = 0x16
    private const val CLIENT_HELLO: Int = 0x01

    private fun ub(b: ByteArray, i: Int): Int = b[i].toInt() and 0xFF
    private fun u16(b: ByteArray, i: Int): Int = (ub(b, i) shl 8) or ub(b, i + 1)

    /**
     * `parse_tls_client_hello` portu. TLS record payload'ı içinden SNI
     * hostname'ini ve payload içindeki byte offset'ini bulur.
     */
    fun parseClientHello(payload: ByteArray): SniInfo? {
        if (payload.size < 5 + 4) return null
        if (ub(payload, 0) != TLS_HANDSHAKE) return null
        if (ub(payload, 5) != CLIENT_HELLO) return null

        var p = 9
        p += 2 // client_version
        if (payload.size < p + 32) return null
        p += 32 // random

        if (payload.size < p + 1) return null
        val sidLen = ub(payload, p)
        p += 1 + sidLen

        if (payload.size < p + 2) return null
        val csLen = u16(payload, p)
        p += 2 + csLen

        if (payload.size < p + 1) return null
        val cmLen = ub(payload, p)
        p += 1 + cmLen

        if (payload.size < p + 2) return null
        val extTotal = u16(payload, p)
        p += 2
        val extEnd = minOf(p + extTotal, payload.size)

        while (p + 4 <= extEnd) {
            val extType = u16(payload, p)
            val extLen = u16(payload, p + 2)
            p += 4
            if (p + extLen > extEnd) return null
            if (extType == 0x0000 && extLen >= 2) {
                var q = p + 2
                val sniEnd = p + extLen
                while (q + 3 <= sniEnd) {
                    val nameType = ub(payload, q)
                    val nameLen = u16(payload, q + 1)
                    q += 3
                    if (q + nameLen > sniEnd) return null
                    if (nameType == 0x00) {
                        val host = String(payload, q, nameLen, Charsets.UTF_8)
                        return SniInfo(host, q)
                    }
                    q += nameLen
                }
                return null
            }
            p += extLen
        }
        return null
    }

    /**
     * sni-mid-split: payload'ı SNI hostname'inin ortasından bölecek INDEX.
     * Rust: `split = sni_off + host.len() / 2`.
     */
    fun sniMidSplitIndex(payload: ByteArray): Int? {
        val sni = parseClientHello(payload) ?: return null
        val hostByteLen = sni.host.toByteArray(Charsets.UTF_8).size
        val split = sni.offset + hostByteLen / 2
        if (split <= 0 || split >= payload.size) return null
        return split
    }

    /**
     * tls-rec-split: tek TLS record içeren payload'ı İKİ TLS record'a böler.
     * Çıktı: hdr1(5) + body(0..k) + hdr2(5) + body(k..N).
     */
    fun tlsRecSplit(payload: ByteArray, firstRecordLen: Int): ByteArray? {
        if (payload.size < 5) return null
        if (ub(payload, 0) != TLS_HANDSHAKE) return null
        val recLen = u16(payload, 3)
        if (payload.size < 5 + recLen) return null
        val k = firstRecordLen
        if (k <= 0 || k >= recLen) return null

        val version1 = ub(payload, 1)
        val version2 = ub(payload, 2)
        val body = payload.copyOfRange(5, 5 + recLen)

        val out = ByteArray(5 + k + 5 + (recLen - k))
        var o = 0
        out[o++] = TLS_HANDSHAKE.toByte()
        out[o++] = version1.toByte()
        out[o++] = version2.toByte()
        out[o++] = (k ushr 8).toByte()
        out[o++] = (k and 0xFF).toByte()
        System.arraycopy(body, 0, out, o, k); o += k
        val k2 = recLen - k
        out[o++] = TLS_HANDSHAKE.toByte()
        out[o++] = version1.toByte()
        out[o++] = version2.toByte()
        out[o++] = (k2 ushr 8).toByte()
        out[o++] = (k2 and 0xFF).toByte()
        System.arraycopy(body, k, out, o, k2)
        return out
    }

    data class SniInfo(val host: String, val offset: Int)
}
