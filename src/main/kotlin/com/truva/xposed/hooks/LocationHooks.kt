package com.truva.xposed.hooks

import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import com.truva.xposed.SpoofConfig

/**
 * LocationHooks — GPS Konum Spoofing
 *
 * Hook edilen API'ler:
 * - LocationManager.getLastKnownLocation()    → sahte konum
 * - LocationManager.requestLocationUpdates()  → sahte konum enjekte
 * - Location.getLatitude/getLongitude          → sahte koordinat
 * - Location.isFromMockProvider()             → false (anti-detection)
 * - Location.isMock()                         → false (Android S+)
 *
 * FusedLocationProviderClient da dolaylı olarak etkilenir çünkü
 * Google Play Services LocationManager'ı dahili olarak kullanır.
 */
object LocationHooks {

    private const val TAG = "TruvaHook.GPS"

    fun install(lpparam: XC_LoadPackage.LoadPackageParam, config: SpoofConfig) {
        val cl = lpparam.classLoader

        val lat = config.getDouble("gps.latitude")
        val lon = config.getDouble("gps.longitude")

        if (lat == 0.0 && lon == 0.0) {
            XposedBridge.log("[$TAG] GPS koordinatları boş, hook'lar atlanıyor")
            return
        }

        XposedBridge.log("[$TAG] Hedef konum: $lat, $lon")

        // ── getLastKnownLocation ──
        XposedHelpers.findAndHookMethod(
            "android.location.LocationManager", cl,
            "getLastKnownLocation",
            String::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    param.result = createFakeLocation(param.args[0] as? String ?: "gps", lat, lon)
                }
            }
        )

        // ── requestLocationUpdates (4-arg version) ──
        try {
            XposedHelpers.findAndHookMethod(
                "android.location.LocationManager", cl,
                "requestLocationUpdates",
                String::class.java, Long::class.java, Float::class.java,
                "android.location.LocationListener",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        // Listener'a sahte konum gönder
                        try {
                            val listener = param.args[3]
                            val fakeLocation = createFakeLocation("gps", lat, lon)
                            listener.javaClass
                                .getMethod("onLocationChanged", Location::class.java)
                                .invoke(listener, fakeLocation)
                        } catch (_: Throwable) {}
                    }
                }
            )
        } catch (_: Throwable) {
            XposedBridge.log("[$TAG] requestLocationUpdates hook uyarısı (opsiyonel)")
        }

        // ── Location nesnesini doğrudan hook'la ──
        // Latitude
        XposedHelpers.findAndHookMethod(
            "android.location.Location", cl,
            "getLatitude",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    param.result = lat
                }
            }
        )

        // Longitude
        XposedHelpers.findAndHookMethod(
            "android.location.Location", cl,
            "getLongitude",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    param.result = lon
                }
            }
        )

        // ── Mock provider tespitini engelle ──
        // isFromMockProvider (API 18-30)
        try {
            XposedHelpers.findAndHookMethod(
                "android.location.Location", cl,
                "isFromMockProvider",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        param.result = false
                    }
                }
            )
        } catch (_: Throwable) {}

        // isMock (API 31+)
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                XposedHelpers.findAndHookMethod(
                    "android.location.Location", cl,
                    "isMock",
                    object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            param.result = false
                        }
                    }
                )
            } catch (_: Throwable) {}
        }

        // ── getExtras — mock flag'ini kaldır ──
        XposedHelpers.findAndHookMethod(
            "android.location.Location", cl,
            "getExtras",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val extras = param.result as? Bundle
                    extras?.remove("mockProvider")
                    extras?.remove("isMock")
                }
            }
        )

        XposedBridge.log("[$TAG] GPS hook'ları kuruldu → ($lat, $lon)")
    }

    /**
     * Gerçekçi bir sahte Location nesnesi oluştur.
     * Zaman damgası, doğruluk ve hız değerleri gerçekçi ayarlanır.
     */
    private fun createFakeLocation(provider: String, lat: Double, lon: Double): Location {
        return Location(provider).apply {
            latitude = lat
            longitude = lon
            accuracy = 12f + (Math.random() * 8).toFloat()  // 12-20m doğruluk
            altitude = 45.0 + Math.random() * 30  // 45-75m yükseklik
            speed = 0f
            bearing = 0f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()

            // Mock flag'ini kaldır
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                val extraBundle = Bundle()
                extras = extraBundle
            }
        }
    }
}
