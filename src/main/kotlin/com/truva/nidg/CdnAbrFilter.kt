package com.truva.nidg

import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

/**
 * CDN/ABR De-Noising Engine
 *
 * YouTube, Netflix ve Spotify gibi platformlar Adaptive Bitrate (ABR)
 * kullandığı için 'Burst-and-Silence' paterni oluşturur.
 * Bu pattern operatör manipülasyonu ile karıştırılmaması için filtrelenir.
 *
 * CIDR-Aware Signature Analysis:
 *   - Bilinen CDN IP bloklarıyla karşılaştırma
 *   - CDN trafiğine 'ABR-Aware' etiketi eklenir
 *   - Sadece CDN dışı trafik QUIC jitter analizine dahil edilir
 *
 * CIDR Freshness Strategy:
 *   - Liste yaşı > 30 gün → CS skoruna CIDR_STALE flag eklenir
 */
class CdnAbrFilter {

    companion object {
        private const val TAG = "NidgCdnFilter"
        private const val CIDR_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000  // 30 gün
    }

    /**
     * Bilinen CDN IP blokları (CIDR formatında).
     * Google, Cloudflare, Akamai, Fastly, Amazon CloudFront temel blokları.
     */
    data class CidrRange(
        val network: Long,   // Network adresi (32-bit unsigned)
        val mask: Long,      // Subnet maskesi
        val provider: String
    ) {
        fun contains(ip: Int): Boolean {
            val ipLong = ip.toLong() and 0xFFFFFFFFL
            return (ipLong and mask) == network
        }
    }

    // Temel CDN CIDR blokları (başlangıç seti)
    private val cdnRanges = CopyOnWriteArrayList<CidrRange>().apply {
        // Google (YouTube, Play Store)
        addAll(parseCidrs(listOf(
            "8.8.4.0/24" to "Google",
            "8.8.8.0/24" to "Google",
            "34.64.0.0/10" to "Google Cloud",
            "35.184.0.0/13" to "Google Cloud",
            "142.250.0.0/15" to "Google",
            "172.217.0.0/16" to "Google",
            "216.58.0.0/16" to "Google",
            "74.125.0.0/16" to "Google"
        )))

        // Cloudflare
        addAll(parseCidrs(listOf(
            "104.16.0.0/12" to "Cloudflare",
            "172.64.0.0/13" to "Cloudflare",
            "131.0.72.0/22" to "Cloudflare",
            "1.1.1.0/24" to "Cloudflare"
        )))

        // Akamai
        addAll(parseCidrs(listOf(
            "23.0.0.0/12" to "Akamai",
            "104.64.0.0/10" to "Akamai"
        )))

        // Fastly
        addAll(parseCidrs(listOf(
            "151.101.0.0/16" to "Fastly",
            "199.232.0.0/16" to "Fastly"
        )))

        // Amazon CloudFront
        addAll(parseCidrs(listOf(
            "13.224.0.0/14" to "CloudFront",
            "52.84.0.0/15" to "CloudFront",
            "99.84.0.0/16" to "CloudFront",
            "54.230.0.0/16" to "CloudFront",
            "54.239.128.0/18" to "CloudFront"
        )))

        // Netflix
        addAll(parseCidrs(listOf(
            "45.57.0.0/17" to "Netflix",
            "198.38.96.0/19" to "Netflix",
            "198.45.48.0/20" to "Netflix",
            "185.2.220.0/22" to "Netflix"
        )))

        // Microsoft (Azure CDN)
        addAll(parseCidrs(listOf(
            "13.107.0.0/16" to "Microsoft",
            "204.79.197.0/24" to "Microsoft"
        )))
    }

    private var lastUpdateMs = System.currentTimeMillis()

    /**
     * IP adresinin CDN'e ait olup olmadığını kontrol eder.
     * @return CDN sağlayıcı adı veya null (CDN dışı)
     */
    fun getCdnProvider(ip: Int): String? {
        return cdnRanges.firstOrNull { it.contains(ip) }?.provider
    }

    /**
     * Paketin CDN trafiği olup olmadığını kontrol eder.
     */
    fun isCdnTraffic(packet: ParsedPacket): Boolean {
        return getCdnProvider(packet.dstIp) != null || getCdnProvider(packet.srcIp) != null
    }

    /**
     * Paketin QUIC jitter analizine dahil edilip edilmeyeceğini belirler.
     * CDN trafiği (ABR-Aware) → jitter analizinden hariç tutulur.
     */
    fun shouldIncludeInJitterAnalysis(packet: ParsedPacket): Boolean {
        if (!packet.isQuic) return false
        return !isCdnTraffic(packet)
    }

    /**
     * CIDR listesinin yaşını kontrol eder.
     * @return true = bayat (> 30 gün)
     */
    fun isCidrStale(): Boolean {
        return System.currentTimeMillis() - lastUpdateMs > CIDR_MAX_AGE_MS
    }

    /**
     * CIDR listesini günceller (Remote Config'den çekilecek).
     */
    fun updateCidrRanges(newRanges: List<Pair<String, String>>) {
        cdnRanges.clear()
        cdnRanges.addAll(parseCidrs(newRanges))
        lastUpdateMs = System.currentTimeMillis()
        Log.i(TAG, "CIDR listesi güncellendi: ${cdnRanges.size} blok")
    }

    /** Toplam CDN CIDR blok sayısı */
    fun cidrBlockCount(): Int = cdnRanges.size

    // ── CIDR Parse Yardımcıları ──

    private fun parseCidrs(entries: List<Pair<String, String>>): List<CidrRange> {
        return entries.mapNotNull { (cidr, provider) ->
            try {
                parseCidr(cidr, provider)
            } catch (e: Exception) {
                Log.w(TAG, "CIDR parse hatası: $cidr — ${e.message}")
                null
            }
        }
    }

    private fun parseCidr(cidr: String, provider: String): CidrRange {
        val parts = cidr.split("/")
        val addressParts = parts[0].split(".")
        val prefixLen = parts[1].toInt()

        val ip = ((addressParts[0].toLong() shl 24) or
                  (addressParts[1].toLong() shl 16) or
                  (addressParts[2].toLong() shl 8) or
                  addressParts[3].toLong()) and 0xFFFFFFFFL

        val mask = if (prefixLen == 0) 0L else (0xFFFFFFFFL shl (32 - prefixLen)) and 0xFFFFFFFFL
        val network = ip and mask

        return CidrRange(network, mask, provider)
    }
}
