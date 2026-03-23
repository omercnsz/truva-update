package com.truva.sync

import android.util.Log
import com.truva.AppDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * LocalhostBridge — Ana profilde çalışan mini HTTP sunucu.
 *
 * İş profilindeki Truva, bu sunucuya 127.0.0.1:38901 üzerinden
 * bağlanarak sunucu listesini ve oturum bilgisini çeker.
 *
 * Güvenlik: Sadece loopback (127.0.0.1) dinler, dışarıya kapalıdır.
 */
object LocalhostBridge {

    private const val TAG = "TruvaBridge"
    const val PORT = 38901

    private var serverSocket: ServerSocket? = null
    private var serverThread: Thread? = null
    private var dao: AppDao? = null

    @Volatile
    var isRunning = false
        private set

    /**
     * Köprüyü başlat. Ana profildeki Truva'dan çağrılır.
     * @param appDao Room DAO — sunucu ve oturum verilerini okumak için
     */
    fun start(appDao: AppDao) {
        if (isRunning) {
            Log.d(TAG, "Köprü zaten çalışıyor.")
            return
        }
        dao = appDao
        serverThread = Thread {
            try {
                // Sadece localhost'tan dinle
                val loopback = InetAddress.getByName("127.0.0.1")
                serverSocket = ServerSocket(PORT, 5, loopback)
                isRunning = true
                Log.i(TAG, "Köprü başlatıldı: 127.0.0.1:$PORT")

                while (!Thread.currentThread().isInterrupted) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        handleClient(client)
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.w(TAG, "Client hatası: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Köprü başlatma hatası: ${e.message}")
            } finally {
                isRunning = false
            }
        }.apply {
            isDaemon = true
            name = "TruvaBridge"
            start()
        }
    }

    /** Köprüyü durdur */
    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverThread?.interrupt()
        serverThread = null
        serverSocket = null
        dao = null
        Log.i(TAG, "Köprü durduruldu.")
    }

    private fun handleClient(client: Socket) {
        try {
            client.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val requestLine = reader.readLine() ?: return

            // Basit HTTP parse: "GET /sync/servers HTTP/1.1"
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                sendResponse(client, 400, """{"error":"bad request"}""")
                return
            }

            val path = parts[1]
            Log.d(TAG, "İstek: $path")

            when {
                path == "/sync/ping" -> {
                    sendResponse(client, 200, """{"status":"ok"}""")
                }
                path == "/sync/servers" -> {
                    handleSyncServers(client)
                }
                path == "/sync/session" -> {
                    handleSyncSession(client)
                }
                else -> {
                    sendResponse(client, 404, """{"error":"not found"}""")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "İstek işleme hatası: ${e.message}")
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun handleSyncServers(client: Socket) {
        val localDao = dao ?: run {
            sendResponse(client, 500, """{"error":"dao not available"}""")
            return
        }
        try {
            val proxies = runBlocking(Dispatchers.IO) {
                localDao.getAllProxiesList()
            }
            val json = ProxySerializer.listToJson(proxies)
            sendResponse(client, 200, json)
            Log.i(TAG, "${proxies.size} sunucu aktarıldı.")
        } catch (e: Exception) {
            sendResponse(client, 500, """{"error":"${e.message}"}""")
        }
    }

    private fun handleSyncSession(client: Socket) {
        val localDao = dao ?: run {
            sendResponse(client, 500, """{"error":"dao not available"}""")
            return
        }
        try {
            val settings = runBlocking(Dispatchers.IO) {
                localDao.getSettingsFlow().firstOrNull()
            }
            val sessionExpiry = settings?.sessionExpiryTime ?: 0L
            val json = JSONObject().apply {
                put("sessionExpiryTime", sessionExpiry)
                put("isActive", sessionExpiry > System.currentTimeMillis())
            }.toString()
            sendResponse(client, 200, json)
            Log.i(TAG, "Oturum bilgisi aktarıldı: expiry=$sessionExpiry")
        } catch (e: Exception) {
            sendResponse(client, 500, """{"error":"${e.message}"}""")
        }
    }

    private fun sendResponse(client: Socket, statusCode: Int, body: String) {
        try {
            val statusText = when (statusCode) {
                200 -> "OK"
                400 -> "Bad Request"
                404 -> "Not Found"
                500 -> "Internal Server Error"
                else -> "Unknown"
            }
            val writer = PrintWriter(client.getOutputStream(), true)
            writer.print("HTTP/1.1 $statusCode $statusText\r\n")
            writer.print("Content-Type: application/json\r\n")
            writer.print("Content-Length: ${body.toByteArray().size}\r\n")
            writer.print("Connection: close\r\n")
            writer.print("\r\n")
            writer.print(body)
            writer.flush()
        } catch (e: Exception) {
            Log.w(TAG, "Yanıt gönderme hatası: ${e.message}")
        }
    }
}
