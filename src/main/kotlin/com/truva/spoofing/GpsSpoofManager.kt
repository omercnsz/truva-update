@file:Suppress("DEPRECATION")

package com.truva.spoofing

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.util.Log

/**
 * GPS Spoofing Manager — LocationManager Mock Provider
 *
 * Android'in resmi Mock Location API'sini kullanarak sahte konum sağlar. Root gerektirmez;
 * Developer Options'ta "Mock Location App" ayarı gerektirir.
 *
 * Sandbox içindeki uygulamalar bu manager üzerinden manipüle edilmiş koordinatlar alır.
 * Koordinatlar seçilen RegionProfile ile senkronize çalışır.
 */
class GpsSpoofManager(private val context: Context) {

    private val tag = "TruvaGpsSpoof"
    private val mockProviderName = "truva_gps"
    private var isProviderAdded = false

    @Volatile private var activeProfile: RegionProfile? = null

    @Volatile private var isRunning = false

    private var mockThread: Thread? = null

    /** Aktif bölge profilini ayarla */
    fun setProfile(profile: RegionProfile?) {
        activeProfile = profile
        if (profile != null) {
            Log.i(
                    tag,
                    "GPS Spoofing hedef: ${profile.latitude}, ${profile.longitude} (${profile.displayName})"
            )
        } else {
            Log.i(tag, "GPS Spoofing devre dışı")
        }
    }

    fun getProfile(): RegionProfile? = activeProfile

    /** Mock Location provider'ı başlat. Sürekli olarak sahte konum verisi yayınlar. */
    @SuppressLint("MissingPermission")
    fun startMocking() {
        val profile =
                activeProfile
                        ?: run {
                            Log.w(tag, "Profil ayarlanmadan mock başlatılamaz")
                            return
                        }

        val locationManager =
                context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                        ?: run {
                            Log.e(tag, "LocationManager bulunamadı")
                            return
                        }

        try {
            // Mevcut mock provider'ı temizle
            stopMocking()

            // GPS ve Network provider'lar için mock ekle
            addMockProvider(locationManager, LocationManager.GPS_PROVIDER)
            addMockProvider(locationManager, LocationManager.NETWORK_PROVIDER)
            isProviderAdded = true

            // Sürekli sahte konum gönderen thread
            isRunning = true
            mockThread =
                    Thread {
                        while (isRunning) {
                            try {
                                val currentProfile = activeProfile ?: break
                                pushMockLocation(
                                        locationManager,
                                        LocationManager.GPS_PROVIDER,
                                        currentProfile
                                )
                                pushMockLocation(
                                        locationManager,
                                        LocationManager.NETWORK_PROVIDER,
                                        currentProfile
                                )
                                Thread.sleep(1000) // Her saniye güncelle
                            } catch (e: InterruptedException) {
                                break
                            } catch (e: Exception) {
                                Log.w(tag, "Mock konum güncellemesi başarısız", e)
                                break
                            }
                        }
                    }
                            .apply {
                                name = "TruvaGpsMock"
                                isDaemon = true
                                start()
                            }

            Log.i(tag, "GPS Mock başlatıldı: ${profile.latitude}, ${profile.longitude}")
        } catch (e: SecurityException) {
            Log.e(
                    tag,
                    "Mock Location izni yok. Developer Options'ta 'Mock Location App' olarak Truva seçilmeli.",
                    e
            )
        } catch (e: Exception) {
            Log.e(tag, "GPS Mock başlatılamadı", e)
        }
    }

    /** Mock Location provider'ı durdur ve temizle */
    fun stopMocking() {
        isRunning = false
        mockThread?.interrupt()
        mockThread = null

        if (!isProviderAdded) return

        val locationManager =
                context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return

        try {
            removeMockProvider(locationManager, LocationManager.GPS_PROVIDER)
            removeMockProvider(locationManager, LocationManager.NETWORK_PROVIDER)
            isProviderAdded = false
            Log.i(tag, "GPS Mock durduruldu")
        } catch (e: Exception) {
            Log.w(tag, "Mock provider temizleme hatası", e)
        }
    }

    // ────────────────────────────────────────────
    // Internal — Mock Provider yönetimi
    // ────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun addMockProvider(lm: LocationManager, providerName: String) {
        try {
            lm.removeTestProvider(providerName)
        } catch (_: Exception) {
            /* provider yoksa ignore */
        }

        try {
            lm.addTestProvider(
                    providerName,
                    false, // requiresNetwork
                    false, // requiresSatellite
                    false, // requiresCell
                    false, // hasMonetaryCost
                    true, // supportsAltitude
                    true, // supportsSpeed
                    true, // supportsBearing
                    android.location.Criteria.POWER_LOW,
                    android.location.Criteria.ACCURACY_FINE
            )
            lm.setTestProviderEnabled(providerName, true)
        } catch (e: Exception) {
            Log.w(tag, "TestProvider eklenemedi: $providerName", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun removeMockProvider(lm: LocationManager, providerName: String) {
        try {
            lm.setTestProviderEnabled(providerName, false)
            lm.removeTestProvider(providerName)
        } catch (_: Exception) {
            /* ignore */
        }
    }

    @SuppressLint("MissingPermission")
    private fun pushMockLocation(lm: LocationManager, provider: String, profile: RegionProfile) {
        val location = createMockLocation(provider, profile)
        try {
            lm.setTestProviderLocation(provider, location)
        } catch (e: Exception) {
            Log.w(tag, "Mock konum push hatası ($provider)", e)
        }
    }

    /**
     * Profil bilgilerinden sahte Location nesnesi oluştur. Anti-detection için küçük rastgele sapma
     * eklenir.
     */
    private fun createMockLocation(provider: String, profile: RegionProfile): Location {
        val jitter = 0.0001 // ~11 metre rastgele sapma
        return Location(provider).apply {
            latitude = profile.latitude + (Math.random() - 0.5) * jitter
            longitude = profile.longitude + (Math.random() - 0.5) * jitter
            altitude = profile.altitude + (Math.random() - 0.5) * 2.0
            accuracy = profile.accuracy + (Math.random().toFloat() * 2f)
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            speed = 0.0f
            bearing = 0.0f

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                bearingAccuracyDegrees = 0.1f
                verticalAccuracyMeters = 3.0f
                speedAccuracyMetersPerSecond = 0.01f
            }

            // isFromMockProvider flag'i gizle (anti-detection)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                isMock = false
            }
            // Eski API'ler için extras ile flag override
            extras = android.os.Bundle().apply { putBoolean("mockProvider", false) }
        }
    }

    // ────────────────────────────────────────────
    // Spoofed Getter'lar — Sandbox hook'ları için
    // ────────────────────────────────────────────

    fun getSpoofedLocation(): Location? {
        val profile = activeProfile ?: return null
        return createMockLocation(LocationManager.GPS_PROVIDER, profile)
    }

    fun getSpoofedLatitude(): Double = activeProfile?.latitude ?: 0.0
    fun getSpoofedLongitude(): Double = activeProfile?.longitude ?: 0.0

    /** Spoofing durumu özet bilgisi */
    fun getStatusSummary(): Map<String, String> {
        val profile = activeProfile ?: return mapOf("durum" to "Kapalı")
        return mapOf(
                "durum" to if (isRunning) "Aktif" else "Hazır",
                "enlem" to "%.4f".format(profile.latitude),
                "boylam" to "%.4f".format(profile.longitude),
                "konum" to profile.displayName
        )
    }
}
