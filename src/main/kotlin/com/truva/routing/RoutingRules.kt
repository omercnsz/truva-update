package com.truva.routing

/**
 * Routing Rule — Akıllı Yönlendirme Kuralı
 *
 * Her kural, belirli bir trafik türünü hangi çıkışa yönlendireceğini tanımlar.
 */
data class RoutingRule(
        val tag: String, // Kural etiketi
        val description: String, // Açıklama
        val type: RuleType, // Kural tipi
        val outboundTag: String, // "proxy", "direct", "dns-out"
        val domains: List<String>? = null, // Domain eşleşmesi
        val ips: List<String>? = null, // IP eşleşmesi
        val ports: String? = null, // Port aralığı "80,443" veya "1000-2000"
        val network: String? = null, // "tcp", "udp", "tcp,udp"
        val protocol: List<String>? = null, // Sniffing protocol
        val priority: Int = 100 // Düşük = yüksek öncelik
)

enum class RuleType {
    DOMAIN, // Domain bazlı kural
    IP, // IP bazlı kural
    PORT, // Port bazlı kural
    PROTOCOL, // Protokol bazlı kural
    NETWORK, // TCP/UDP kural
    COMPOSITE // Birden fazla koşulu birleştiren kural
}

/** Önceden tanımlı yönlendirme kural kümeleri */
object RoutingPresets {

    /**
     * Oyun Modu Kuralları — Tam tünel + oyun optimizasyonu
     *
     * Strateji:
     * - DNS → Proxy (en yüksek öncelik)
     * - Tüm TCP ve UDP trafiği → Proxy
     * VPN tünelinde "direct" outbound çalışmaz, bu yüzden her şey proxy'den geçer.
     */
    val GAMING_MODE_RULES: List<RoutingRule> =
            listOf(
                    // DNS trafiği → Proxy (en yüksek öncelik)
                    RoutingRule(
                            tag = "dns-proxy",
                            description = "DNS trafiği proxy üzerinden",
                            type = RuleType.PORT,
                            outboundTag = "proxy",
                            ports = "53",
                            priority = 1
                    ),
                    // Yasaklı/engellenmiş servisler → Proxy
                    RoutingRule(
                            tag = "blocked-services-proxy",
                            description = "Engelli servisler proxy üzerinden",
                            type = RuleType.DOMAIN,
                            outboundTag = "proxy",
                            domains =
                                    listOf(
                                            "domain:roblox.com",
                                            "domain:rbxcdn.com",
                                            "domain:robloxlabs.com",
                                            "domain:tiktok.com",
                                            "domain:tiktokv.com",
                                            "domain:musical.ly",
                                            "domain:byteoversea.com",
                                            "domain:tiktokcdn.com",
                                            "domain:discord.com",
                                            "domain:discord.gg",
                                            "domain:discordapp.com",
                                            "domain:twitch.tv",
                                            "domain:jtvnw.net",
                                            "domain:spotify.com",
                                            "domain:scdn.co",
                                            "domain:netflix.com",
                                            "domain:nflxvideo.net",
                                            "domain:google.com",
                                            "domain:googleapis.com",
                                            "domain:gstatic.com",
                                            "domain:youtube.com",
                                            "domain:ytimg.com",
                                            "domain:googlevideo.com",
                                            "domain:play.google.com"
                                    ),
                            priority = 10
                    ),
                    // Tüm trafik (TCP + UDP) → Proxy
                    RoutingRule(
                            tag = "catch-all-proxy",
                            description = "Tüm trafik proxy üzerinden (UDP dahil)",
                            type = RuleType.NETWORK,
                            outboundTag = "proxy",
                            network = "tcp,udp",
                            priority = 999
                    )
            )

    /**
     * Video Akış Modu Kuralları — Akış optimizasyonu
     *
     * Tüm trafik proxy üzerinden (tam tünel).
     */
    val VIDEO_STREAMING_RULES: List<RoutingRule> =
            listOf(
                    // DNS → Proxy
                    RoutingRule(
                            tag = "dns-proxy",
                            description = "DNS trafiği proxy üzerinden",
                            type = RuleType.PORT,
                            outboundTag = "proxy",
                            ports = "53",
                            priority = 1
                    ),
                    // Streaming servisleri → Proxy
                    RoutingRule(
                            tag = "streaming-proxy",
                            description = "Video akış servisleri proxy üzerinden",
                            type = RuleType.DOMAIN,
                            outboundTag = "proxy",
                            domains =
                                    listOf(
                                            "domain:netflix.com",
                                            "domain:nflxvideo.net",
                                            "domain:nflximg.net",
                                            "domain:youtube.com",
                                            "domain:googlevideo.com",
                                            "domain:ytimg.com",
                                            "domain:twitch.tv",
                                            "domain:jtvnw.net",
                                            "domain:ttvnw.net",
                                            "domain:tiktok.com",
                                            "domain:tiktokcdn.com",
                                            "domain:disneyplus.com",
                                            "domain:bamgrid.com",
                                            "domain:hulu.com",
                                            "domain:hulustream.com",
                                            "domain:primevideo.com",
                                            "domain:aiv-cdn.net",
                                            "domain:spotify.com",
                                            "domain:scdn.co"
                                    ),
                            priority = 10
                    ),
                    // Kalan → Proxy (tam koruma)
                    RoutingRule(
                            tag = "catch-all-proxy",
                            description = "Geri kalan trafik proxy",
                            type = RuleType.NETWORK,
                            outboundTag = "proxy",
                            network = "tcp,udp",
                            priority = 999
                    )
            )

    /** Standart Mod — Tam tünel (tüm trafik proxy) */
    val STANDARD_RULES: List<RoutingRule> =
            listOf(
                    // DNS → Proxy
                    RoutingRule(
                            tag = "dns-proxy",
                            description = "DNS trafiği proxy üzerinden",
                            type = RuleType.PORT,
                            outboundTag = "proxy",
                            ports = "53",
                            priority = 1
                    ),
                    RoutingRule(
                            tag = "all-proxy",
                            description = "Tüm trafik proxy üzerinden",
                            type = RuleType.NETWORK,
                            outboundTag = "proxy",
                            network = "tcp,udp",
                            priority = 999
                    )
            )

    /** Anti-Sansür Modu — Tüm trafik proxy (VPN tünelinde direct çalışmaz) */
    val ANTI_CENSORSHIP_RULES: List<RoutingRule> =
            listOf(
                    // DNS → Proxy
                    RoutingRule(
                            tag = "dns-proxy",
                            description = "DNS trafiği proxy üzerinden",
                            type = RuleType.PORT,
                            outboundTag = "proxy",
                            ports = "53",
                            priority = 1
                    ),
                    // Bilinen engelli domainler → Proxy
                    RoutingRule(
                            tag = "blocked-domains-proxy",
                            description = "Engelli domainler proxy üzerinden",
                            type = RuleType.DOMAIN,
                            outboundTag = "proxy",
                            domains =
                                    listOf(
                                            "domain:roblox.com",
                                            "domain:rbxcdn.com",
                                            "domain:tiktok.com",
                                            "domain:tiktokcdn.com",
                                            "domain:discord.com",
                                            "domain:discordapp.com",
                                            "domain:twitter.com",
                                            "domain:x.com",
                                            "domain:reddit.com",
                                            "domain:wikipedia.org",
                                            "domain:medium.com",
                                            "domain:imgur.com",
                                            "domain:pastebin.com"
                                    ),
                            priority = 10
                    ),
                    // Google servisleri → Proxy
                    RoutingRule(
                            tag = "google-proxy",
                            description = "Google servisleri proxy üzerinden",
                            type = RuleType.DOMAIN,
                            outboundTag = "proxy",
                            domains =
                                    listOf(
                                            "domain:google.com",
                                            "domain:googleapis.com",
                                            "domain:play.google.com",
                                            "domain:gstatic.com",
                                            "domain:googleusercontent.com"
                                    ),
                            priority = 20
                    ),
                    // Kalan trafik → Proxy (VPN tünelinde direct çalışmaz)
                    RoutingRule(
                            tag = "catch-all-proxy",
                            description = "Tüm trafik proxy üzerinden",
                            type = RuleType.NETWORK,
                            outboundTag = "proxy",
                            network = "tcp,udp",
                            priority = 999
                    )
            )
}
