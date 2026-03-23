package com.truva.sync

import com.truva.ProxyEntity
import org.json.JSONArray
import org.json.JSONObject

/**
 * ProxyEntity ↔ JSON dönüşüm yardımcısı.
 * Localhost köprüsü üzerinden sunucu aktarımında kullanılır.
 */
object ProxySerializer {

    fun toJson(proxy: ProxyEntity): JSONObject {
        return JSONObject().apply {
            put("id", proxy.id)
            put("name", proxy.name)
            put("region", proxy.region)
            put("ip", proxy.ip)
            put("port", proxy.port)
            put("uuid", proxy.uuid)
            put("publicKey", proxy.publicKey)
            put("shortId", proxy.shortId)
            put("sni", proxy.sni)
            put("password", proxy.password)
            put("flow", proxy.flow)
            put("security", proxy.security)
            put("network", proxy.network)
            put("fingerprint", proxy.fingerprint)
            put("path", proxy.path)
            put("isSelected", proxy.isSelected)
            put("latency", proxy.latency ?: -1L)
        }
    }

    fun fromJson(json: JSONObject): ProxyEntity {
        return ProxyEntity(
            id = json.optInt("id", 0),
            name = json.optString("name", ""),
            region = json.optString("region", ""),
            ip = json.optString("ip", ""),
            port = json.optInt("port", 443),
            uuid = json.optString("uuid", ""),
            publicKey = json.optString("publicKey", ""),
            shortId = json.optString("shortId", ""),
            sni = json.optString("sni", "google.com"),
            password = json.optString("password", ""),
            flow = json.optString("flow", "xtls-rprx-vision"),
            security = json.optString("security", "reality"),
            network = json.optString("network", "tcp"),
            fingerprint = json.optString("fingerprint", "chrome"),
            path = json.optString("path", "/"),
            isSelected = json.optBoolean("isSelected", false),
            latency = json.optLong("latency", -1L).let { if (it == -1L) null else it }
        )
    }

    fun listToJson(proxies: List<ProxyEntity>): String {
        val arr = JSONArray()
        proxies.forEach { arr.put(toJson(it)) }
        return arr.toString()
    }

    fun listFromJson(raw: String): List<ProxyEntity> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
    }
}
