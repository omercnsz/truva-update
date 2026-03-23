package com.truva.routing

import android.util.Log
import com.truva.ProxyEntity
import com.truva.SettingsEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * SmartRouter — Xray JSON Konfigürasyon Üretici
 *
 * İki mod sunar:
 *
 * 1. buildCoreConfig(proxy)
 *    En basit çalışan config. Routing/Fragment/Mux/DNS yok.
 *    Sadece SOCKS5 inbound + VLESS outbound.
 *    Sorun tespiti ve temel bağlantı için kullanılır.
 *
 * 2. buildFullConfig(proxy, settings)
 *    Routing kuralları, DNS, oyun/video modları dahil tam config.
 *    Temel bağlantı çalıştıktan sonra aktif edilir.
 *
 * ÖNEMLİ KISITLAMALAR:
 *   - xtls-rprx-vision + Mux = UYUMSUZ (Xray resmi kısıtlama)
 *   - dialerProxy + fragment = DPI bypass ama bazı sunucularda bozar
 */
object SmartRouter {

    private const val TAG = "TruvaSmartRouter"

    /**
     * Minimal çekirdek config — sadece SOCKS5 + VLESS.
     * Fragment yok, Mux yok, Routing yok, DNS yok.
     */
    fun buildCoreConfig(proxy: ProxyEntity): String {
        val config = JSONObject()
        config.put("log", JSONObject().apply { put("loglevel", "warning") })

        // DNS
        config.put("dns", JSONObject().apply {
            put("servers", JSONArray().apply {
                put("1.1.1.1")
                put("8.8.8.8")
            })
        })

        // Inbound: SOCKS5 + sniffing
        config.put("inbounds", JSONArray().apply {
            put(JSONObject().apply {
                put("tag", "socks-in")
                put("port", 10808)
                put("protocol", "socks")
                put("listen", "127.0.0.1")
                put("settings", JSONObject().apply {
                    put("auth", "noauth")
                    put("udp", true)
                })
                put("sniffing", JSONObject().apply {
                    put("enabled", true)
                    put("destOverride", JSONArray().apply {
                        put("http")
                        put("tls")
                        put("quic")
                    })
                })
            })
        })

        // Outbounds: VLESS + Freedom
        config.put("outbounds", JSONArray().apply {
            put(buildVlessOutbound(proxy))
            put(JSONObject().apply {
                put("tag", "direct")
                put("protocol", "freedom")
            })
        })

        val json = config.toString(2)
        Log.d(TAG, "Core config: ${json.length} byte")
        return json
    }

    /**
     * Tam config — routing kuralları, DNS, mod bazlı optimizasyonlar dahil.
     * Temel bağlantı çalıştıktan sonra aktif edilir.
     */
    fun buildFullConfig(proxy: ProxyEntity, settings: SettingsEntity): String {
        val config = JSONObject()

        // Log
        config.put("log", JSONObject().apply {
            put("loglevel", if (settings.isGamingModeEnabled) "error" else "warning")
        })

        // DNS
        config.put("dns", JSONObject().apply {
            put("servers", JSONArray().apply {
                put("1.1.1.1")
                put("8.8.8.8")
            })
            put("queryStrategy", "UseIPv4")
        })

        // Inbound: SOCKS5 + sniffing
        config.put("inbounds", JSONArray().apply {
            put(JSONObject().apply {
                put("tag", "socks-in")
                put("port", 10808)
                put("protocol", "socks")
                put("listen", "127.0.0.1")
                put("settings", JSONObject().apply {
                    put("auth", "noauth")
                    put("udp", true)
                })
                put("sniffing", JSONObject().apply {
                    put("enabled", true)
                    put("destOverride", JSONArray().apply {
                        put("http")
                        put("tls")
                        put("quic")
                    })
                })
            })
        })

        // Outbounds
        config.put("outbounds", JSONArray().apply {
            put(buildVlessOutbound(proxy))
            put(JSONObject().apply {
                put("tag", "direct")
                put("protocol", "freedom")
                put("settings", JSONObject().apply { put("domainStrategy", "AsIs") })
            })
            put(JSONObject().apply {
                put("tag", "block")
                put("protocol", "blackhole")
            })
        })

        // Routing
        val rules = getRoutingRules(settings)
        config.put("routing", JSONObject().apply {
            put("domainStrategy", "IPIfNonMatch")
            put("rules", JSONArray().apply {
                rules.sortedBy { it.priority }.forEach { rule ->
                    put(ruleToJson(rule))
                }
            })
        })

        val json = config.toString(2)
        Log.d(TAG, "Full config: ${json.length} byte")
        return json
    }

    // ═══════════════════════════════════════════════════
    // VLESS Outbound Builder
    // ═══════════════════════════════════════════════════

    private fun buildVlessOutbound(proxy: ProxyEntity): JSONObject {
        return JSONObject().apply {
            put("tag", "proxy")
            put("protocol", "vless")
            put("settings", JSONObject().apply {
                put("vnext", JSONArray().apply {
                    put(JSONObject().apply {
                        put("address", proxy.ip)
                        put("port", proxy.port)
                        put("users", JSONArray().apply {
                            put(JSONObject().apply {
                                put("id", proxy.uuid)
                                put("encryption", "none")
                                if (proxy.flow.isNotBlank()) {
                                    put("flow", proxy.flow)
                                }
                            })
                        })
                    })
                })
                put("udp", true)
            })
            put("streamSettings", JSONObject().apply {
                put("network", proxy.network)
                put("security", proxy.security)
                when (proxy.security) {
                    "reality" -> {
                        put("realitySettings", JSONObject().apply {
                            put("serverName", proxy.sni)
                            put("publicKey", proxy.publicKey)
                            put("shortId", proxy.shortId)
                            put("fingerprint", proxy.fingerprint)
                            if (proxy.password.isNotBlank()) {
                                put("password", proxy.password)
                            }
                        })
                    }
                    "tls" -> {
                        put("tlsSettings", JSONObject().apply {
                            put("serverName", proxy.sni)
                            put("fingerprint", proxy.fingerprint)
                            put("allowInsecure", false)
                        })
                    }
                }
                if (proxy.network == "ws") {
                    put("wsSettings", JSONObject().apply {
                        put("path", proxy.path)
                        if (proxy.sni.isNotBlank()) {
                            put("headers", JSONObject().put("Host", proxy.sni))
                        }
                    })
                } else if (proxy.network == "grpc") {
                    put("grpcSettings", JSONObject().apply {
                        put("serviceName", proxy.path)
                    })
                }
            })
            // NOT: Mux xtls-rprx-vision ile UYUMSUZ — asla eklenmemeli
        }
    }

    // ═══════════════════════════════════════════════════
    // Routing Kuralları
    // ═══════════════════════════════════════════════════

    private fun getRoutingRules(settings: SettingsEntity): List<RoutingRule> {
        return when (settings.routingMode) {
            "gaming" -> RoutingPresets.GAMING_MODE_RULES
            "streaming" -> RoutingPresets.VIDEO_STREAMING_RULES
            "anti_censorship" -> RoutingPresets.ANTI_CENSORSHIP_RULES
            else -> RoutingPresets.STANDARD_RULES
        }
    }

    private fun ruleToJson(rule: RoutingRule): JSONObject {
        return JSONObject().apply {
            put("type", "field")
            put("outboundTag", rule.outboundTag)
            rule.domains?.let { put("domain", JSONArray().apply { it.forEach { d -> put(d) } }) }
            rule.ips?.let { put("ip", JSONArray().apply { it.forEach { ip -> put(ip) } }) }
            rule.ports?.let { put("port", it) }
            rule.network?.let { put("network", it) }
            rule.protocol?.let { put("protocol", JSONArray().apply { it.forEach { p -> put(p) } }) }
        }
    }

    // ═══════════════════════════════════════════════════
    // Eski API uyumluluğu (buildXrayConfig → buildFullConfig)
    // ═══════════════════════════════════════════════════

    @Suppress("UNUSED_PARAMETER")
    fun buildXrayConfig(
        proxy: ProxyEntity,
        settings: SettingsEntity,
        gamingForceProxy: Boolean = false
    ): String = buildFullConfig(proxy, settings)

    /** UDP direct bypass test (opsiyonel) */
    fun testDirectUdpAvailability(proxyIp: String, port: Int = 443): Boolean {
        return try {
            val socket = java.net.DatagramSocket()
            socket.soTimeout = 3000
            val address = java.net.InetAddress.getByName(proxyIp)
            val data = ByteArray(1) { 0x00 }
            socket.send(java.net.DatagramPacket(data, data.size, address, port))
            socket.close()
            true
        } catch (_: Exception) {
            false
        }
    }
}
