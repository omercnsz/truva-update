package com.truva.nidg

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.TrafficStats
import android.util.Log

/**
 * Adaptive Heartbeat & Power-Aware Analysis
 *
 * ÖNEMLİ: NIDG sadece MOBİL VERİ bağlantısında çalışır.
 * WiFi'da analiz tamamen durur — operatör veri tüketimi ile karışması önlenir.
 *
 * State-Driven Polling:
 *   MOBILE + SCREEN_ON → aktif analiz
 *   MOBILE + SCREEN_OFF + veri akışı yok → polling durur
 *   WIFI → analiz tamamen devre dışı
 */
class AdaptiveHeartbeat(private val context: Context) {

    companion object {
        private const val TAG = "NidgHeartbeat"
        private const val TRAFFIC_CHECK_BYTES = 1024L
    }

    enum class AnalysisMode {
        ACTIVE,         // Mobil veri + ekran açık → tam analiz
        LIGHTHOUSE,     // Mobil veri + ekran kapalı + aktif trafik → kritik metrikler
        DORMANT,        // Mobil veri + ekran kapalı + trafik yok → analiz durur
        WIFI_PAUSED     // WiFi'da → analiz tamamen devre dışı
    }

    // ── Durum ──
    var currentMode = AnalysisMode.WIFI_PAUSED
        private set

    var isMobileData = false
        private set

    private var isScreenOn = true
    private var lastRxBytes = 0L
    private var lastTxBytes = 0L
    private var screenReceiver: BroadcastReceiver? = null
    private var networkCallbackRegistered = false

    /**
     * Ekran ve ağ tipi dinleyicilerini kaydeder.
     */
    fun start() {
        // ── Ekran Dinleyici ──
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        isScreenOn = true
                        updateMode()
                        Log.d(TAG, "Ekran açık → ${currentMode.name}")
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        isScreenOn = false
                        updateMode()
                        Log.d(TAG, "Ekran kapalı → ${currentMode.name}")
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        context.registerReceiver(screenReceiver, filter)

        // ── Ağ Tipi Dinleyici ──
        registerNetworkCallback()

        // Başlangıç: Mevcut ağ tipini kontrol et
        checkCurrentNetworkType()

        lastRxBytes = TrafficStats.getTotalRxBytes()
        lastTxBytes = TrafficStats.getTotalTxBytes()

        Log.i(TAG, "Adaptive Heartbeat başlatıldı (Sadece mobil veri modunda)")
    }

    /**
     * ConnectivityManager ile ağ tipi değişikliklerini dinler.
     */
    private fun registerNetworkCallback() {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    val wasMobile = isMobileData
                    isMobileData = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)

                    if (wasMobile != isMobileData) {
                        Log.i(TAG, "Ağ tipi değişti: ${if (isMobileData) "MOBİL VERİ ✅" else if (isWifi) "WiFi ⏸️" else "DİĞER ⏸️"}")
                        updateMode()
                    }
                }

                override fun onLost(network: Network) {
                    isMobileData = false
                    updateMode()
                    Log.i(TAG, "Ağ bağlantısı kesildi → ${currentMode.name}")
                }
            })
            networkCallbackRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Ağ callback kaydı hatası: ${e.message}")
            // Fallback: İlk kontrolü kullan
            checkCurrentNetworkType()
        }
    }

    /**
     * Mevcut ağ tipini kontrol eder (başlangıç ve fallback).
     */
    private fun checkCurrentNetworkType() {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork
            val capabilities = activeNetwork?.let { cm.getNetworkCapabilities(it) }

            isMobileData = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            Log.i(TAG, "Mevcut ağ: ${if (isMobileData) "MOBİL VERİ" else "WiFi/Diğer"}")
        } catch (e: Exception) {
            isMobileData = false
        }
        updateMode()
    }

    /**
     * Trafik aktivitesini kontrol eder ve modu günceller.
     */
    fun checkTrafficAndUpdateMode() {
        // Önce ağ tipi kontrolü
        if (!isMobileData) {
            currentMode = AnalysisMode.WIFI_PAUSED
            return
        }

        val currentRx = TrafficStats.getTotalRxBytes()
        val currentTx = TrafficStats.getTotalTxBytes()
        val rxDelta = currentRx - lastRxBytes
        val txDelta = currentTx - lastTxBytes
        val hasTraffic = (rxDelta + txDelta) > TRAFFIC_CHECK_BYTES
        lastRxBytes = currentRx
        lastTxBytes = currentTx

        if (!isScreenOn) {
            currentMode = if (hasTraffic) AnalysisMode.LIGHTHOUSE else AnalysisMode.DORMANT
        } else {
            currentMode = AnalysisMode.ACTIVE
        }
    }

    /** Analiz çalıştırılmalı mı? WiFi'da HAYIR */
    fun shouldAnalyze(): Boolean = isMobileData && currentMode != AnalysisMode.DORMANT && currentMode != AnalysisMode.WIFI_PAUSED

    /** Sinyal yoklaması? Sadece mobil + aktif */
    fun shouldPollSignal(): Boolean = isMobileData && currentMode == AnalysisMode.ACTIVE

    /** Traceroute? Sadece mobil + aktif */
    fun shouldRunTraceroute(): Boolean = isMobileData && currentMode == AnalysisMode.ACTIVE

    fun stop() {
        try { screenReceiver?.let { context.unregisterReceiver(it) } } catch (_: Exception) {}
        screenReceiver = null
    }

    private fun updateMode() {
        currentMode = if (!isMobileData) {
            AnalysisMode.WIFI_PAUSED
        } else if (isScreenOn) {
            AnalysisMode.ACTIVE
        } else {
            AnalysisMode.DORMANT
        }
    }
}
