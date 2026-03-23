package com.truva.sandbox.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

/** Arka planda Android'in yayınladığı ADB portlarını (mDNS üzerinden) otomatik bulur. */
object AdbScanner {
    private const val TAG = "TruvaAdbScanner"

    var pairingPort: Int? = null
    var connectPort: Int? = null

    private var nsdManager: NsdManager? = null
    private var pairingListener: NsdManager.DiscoveryListener? = null
    private var connectListener: NsdManager.DiscoveryListener? = null
    private var onPortsFound: (() -> Unit)? = null
    var onConnectPortFound: ((Int) -> Unit)? = null

    fun startScanning(context: Context, onComplete: () -> Unit) {
        pairingPort = null
        connectPort = null
        onPortsFound = onComplete
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

        pairingListener =
                createListener("_adb-tls-pairing") { port ->
                    pairingPort = port
                    checkAndComplete()
                }
        connectListener =
                createListener("_adb-tls-connect") { port ->
                    connectPort = port
                    onConnectPortFound?.invoke(port)
                    checkAndComplete()
                }

        nsdManager?.discoverServices(
                "_adb-tls-pairing._tcp.",
                NsdManager.PROTOCOL_DNS_SD,
                pairingListener
        )
        nsdManager?.discoverServices(
                "_adb-tls-connect._tcp.",
                NsdManager.PROTOCOL_DNS_SD,
                connectListener
        )
        Log.i(TAG, "ADB Port taraması başlatıldı (Radar Açık)...")
    }

    private fun checkAndComplete() {
        if (pairingPort != null && connectPort != null) {
            Log.i(
                    TAG,
                    "GÖREV BAŞARILI! Eşleştirme Portu: $pairingPort | Bağlantı Portu: $connectPort"
            )
            onPortsFound?.invoke()
            stopScanning()
        }
    }

    private fun createListener(
            expectedType: String,
            onPortFound: (Int) -> Unit
    ): NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType.contains(expectedType)) {
                    nsdManager?.resolveService(
                            service,
                            object : NsdManager.ResolveListener {
                                override fun onResolveFailed(
                                        serviceInfo: NsdServiceInfo,
                                        errorCode: Int
                                ) {
                                    Log.e(TAG, "Resolve failed for $expectedType: $errorCode")
                                }
                                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                    Log.i(TAG, "$expectedType portu yakalandı: ${serviceInfo.port}")
                                    onPortFound(serviceInfo.port)
                                }
                            }
                    )
                }
            }
            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Start discovery failed for $serviceType: $errorCode")
                stopScanning()
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
    }

    fun stopScanning() {
        try {
            pairingListener?.let { nsdManager?.stopServiceDiscovery(it) }
            connectListener?.let { nsdManager?.stopServiceDiscovery(it) }
        } catch (e: Exception) {
            /* Yoksay */
        }
        pairingListener = null
        connectListener = null
    }
}
