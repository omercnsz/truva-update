package com.truva.sync

import android.util.Log
import com.truva.ProxyEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * BridgeClient — İş profilindeki Truva'nın ana profildeki köprüden veri çekmesi için HTTP client.
 *
 * Ana profildeki LocalhostBridge'e 127.0.0.1:38901 üzerinden bağlanır.
 */
object BridgeClient {

    private const val TAG = "TruvaBridgeClient"
    private const val BASE_URL = "http://127.0.0.1:${LocalhostBridge.PORT}"
    private const val TIMEOUT_MS = 5000

    /**
     * Köprünün ayakta olup olmadığını kontrol et.
     * @return true = köprü aktif
     */
    suspend fun checkBridge(): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = httpGet("$BASE_URL/sync/ping")
            val json = JSONObject(response)
            json.optString("status") == "ok"
        } catch (e: Exception) {
            Log.d(TAG, "Köprü erişilemez: ${e.message}")
            false
        }
    }

    /**
     * Ana profildeki sunucu listesini çek.
     * @return Sunucu listesi veya boş liste
     */
    suspend fun fetchServers(): List<ProxyEntity> = withContext(Dispatchers.IO) {
        try {
            val response = httpGet("$BASE_URL/sync/servers")
            val proxies = ProxySerializer.listFromJson(response)
            Log.i(TAG, "${proxies.size} sunucu alındı.")
            proxies
        } catch (e: Exception) {
            Log.e(TAG, "Sunucu çekme hatası: ${e.message}")
            emptyList()
        }
    }

    /**
     * Ana profildeki oturum bilgisini çek.
     * @return Pair(sessionExpiryTime, isActive)
     */
    suspend fun fetchSession(): Pair<Long, Boolean> = withContext(Dispatchers.IO) {
        try {
            val response = httpGet("$BASE_URL/sync/session")
            val json = JSONObject(response)
            val expiry = json.optLong("sessionExpiryTime", 0L)
            val active = json.optBoolean("isActive", false)
            Log.i(TAG, "Oturum alındı: expiry=$expiry, active=$active")
            Pair(expiry, active)
        } catch (e: Exception) {
            Log.e(TAG, "Oturum çekme hatası: ${e.message}")
            Pair(0L, false)
        }
    }

    private fun httpGet(urlString: String): String {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.setRequestProperty("Accept", "application/json")

        try {
            val responseCode = conn.responseCode
            if (responseCode != 200) {
                throw RuntimeException("HTTP $responseCode")
            }
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line)
            }
            reader.close()
            return sb.toString()
        } finally {
            conn.disconnect()
        }
    }
}
