package com.truva

import android.net.Uri
import android.util.Log

/**
 * VLESS-Reality URI'lerini parse edip ProxyEntity'e dönüştüren yardımcı sınıf.
 *
 * Desteklenen format:
 * vless://UUID@HOST:PORT?type=tcp&security=reality&pbk=PUBLIC_KEY&sid=SHORT_ID&sni=SNI&flow=xtls-rprx-vision&fp=chrome#DISPLAY_NAME
 *
 * Flutter katalog uygulamasından deep link ile gelen ham vless:// stringini
 * doğrudan Room veritabanına kaydedilebilir ProxyEntity nesnesine çevirir.
 */
object VlessLinkParser {

    private const val TAG = "VlessLinkParser"

    /**
     * Raw vless:// linkini ProxyEntity'e çevirir.
     * Başarısız olursa null döner.
     */
    fun parse(rawLink: String): ProxyEntity? {
        return try {
            // vless:// URI formatında parse et
            val uri = Uri.parse(rawLink)

            if (uri.scheme?.lowercase() != "vless") {
                Log.w(TAG, "Geçersiz şema: ${uri.scheme} (beklenen: vless)")
                return null
            }

            // UUID → userInfo kısmında (vless://UUID@host:port)
            val uuid = uri.userInfo
            if (uuid.isNullOrEmpty()) {
                Log.w(TAG, "UUID bulunamadı")
                return null
            }

            // Host (IP veya domain)
            val host = uri.host
            if (host.isNullOrEmpty()) {
                Log.w(TAG, "Host bulunamadı")
                return null
            }

            // Port (varsayılan 443)
            val port = if (uri.port > 0) uri.port else 443

            // Query parametreleri
            val publicKey = uri.getQueryParameter("pbk") ?: ""
            val shortId = uri.getQueryParameter("sid") ?: ""
            val sni = uri.getQueryParameter("sni") ?: "google.com"
            val password = uri.getQueryParameter("password")
                ?: uri.getQueryParameter("pwd")
                ?: uri.getQueryParameter("pass")
                ?: ""
            val flow = uri.getQueryParameter("flow") ?: "xtls-rprx-vision"
            val security = uri.getQueryParameter("security") ?: "reality"
            val network = uri.getQueryParameter("type") ?: "tcp"
            val fingerprint = uri.getQueryParameter("fp") ?: "chrome"
            val path = uri.getQueryParameter("path") ?: uri.getQueryParameter("serviceName") ?: "/"

            // Fragment → Sunucu görünen adı (vless://...#İsveç - Stockholm)
            val displayName = uri.fragment?.let { Uri.decode(it) }
                ?: "$host:$port"

            // Bölge kodu çıkarımı: displayName'den veya sni'den tahmin et
            val region = extractRegionCode(displayName, sni)

            if (publicKey.isEmpty()) {
                Log.w(TAG, "Reality publicKey (pbk) eksik — link Reality olmayabilir")
            }

            ProxyEntity(
                name = displayName,
                region = region,
                ip = host,
                port = port,
                uuid = uuid,
                publicKey = publicKey,
                shortId = shortId,
                sni = sni,
                password = password,
                flow = flow,
                security = security,
                network = network,
                fingerprint = fingerprint,
                path = path,
                isSelected = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "VLESS link parse hatası: ${e.message}", e)
            null
        }
    }

    /**
     * Görünen ad veya SNI'dan bölge kodunu (ISO 3166-1 alpha-2) tahmin eder.
     * Bulunamazsa "XX" döner.
     */
    private fun extractRegionCode(displayName: String, sni: String): String {
        val text = "$displayName $sni".lowercase()

        // Bilinen bölge eşleşmeleri
        val regionMap = mapOf(
            "isveç" to "SE", "sweden" to "SE", "stockholm" to "SE", "se" to "SE",
            "finlandiya" to "FI", "finland" to "FI", "helsinki" to "FI", "fi" to "FI",
            "litvanya" to "LT", "lithuania" to "LT", "vilnius" to "LT", "lt" to "LT",
            "almanya" to "DE", "germany" to "DE", "frankfurt" to "DE", "berlin" to "DE", "de" to "DE",
            "hollanda" to "NL", "netherlands" to "NL", "amsterdam" to "NL", "nl" to "NL",
            "türkiye" to "TR", "turkey" to "TR", "istanbul" to "TR", "tr" to "TR",
            "abd" to "US", "usa" to "US", "united states" to "US", "new york" to "US", "los angeles" to "US", "us" to "US",
            "ingiltere" to "GB", "uk" to "GB", "united kingdom" to "GB", "london" to "GB", "gb" to "GB",
            "fransa" to "FR", "france" to "FR", "paris" to "FR", "fr" to "FR",
            "japonya" to "JP", "japan" to "JP", "tokyo" to "JP", "jp" to "JP",
            "singapur" to "SG", "singapore" to "SG", "sg" to "SG",
            "kanada" to "CA", "canada" to "CA", "toronto" to "CA", "ca" to "CA",
            "avustralya" to "AU", "australia" to "AU", "sydney" to "AU", "au" to "AU",
            "romanya" to "RO", "romania" to "RO", "bucharest" to "RO", "ro" to "RO",
            "bulgaristan" to "BG", "bulgaria" to "BG", "sofia" to "BG", "bg" to "BG",
            "polonya" to "PL", "poland" to "PL", "warsaw" to "PL", "pl" to "PL",
            "rusya" to "RU", "russia" to "RU", "moscow" to "RU", "ru" to "RU"
        )

        // Uzun eşleşmeleri önce kontrol et (ör. "united states" vs "us")
        for ((keyword, code) in regionMap.entries.sortedByDescending { it.key.length }) {
            if (text.contains(keyword)) {
                return code
            }
        }

        // Fragment'teki ilk 2 harf bölge kodu olabilir (ör. "#SE - Stockholm")
        val firstTwoChars = displayName.trim().take(2).uppercase()
        if (firstTwoChars.length == 2 && firstTwoChars.all { it.isLetter() }) {
            return firstTwoChars
        }

        return "XX" // Bilinmeyen bölge
    }
}
