package com.truva.sandbox

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.util.Log

/**
 * WorkProfileManager — Android İş Profili Yöneticisi
 *
 * Android'in yerleşik Managed Profile API'sini kullanarak izole bir çalışma profili oluşturur. İş
 * profili:
 * - Ayrı veri alanı (contacts, storage, accounts)
 * - Ayrı uygulama listesi
 * - Ana profilden izole — uygulamalar arası veri sızıntısı önlenir
 *
 * Truva bu profili kullanarak yamalı uygulamaları izole ortamda çalıştırır.
 */
class WorkProfileManager(private val context: Context) {

    private val tag = "TruvaWorkProfile"

    private val dpm: DevicePolicyManager by lazy {
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    }

    private val userManager: UserManager by lazy {
        context.getSystemService(Context.USER_SERVICE) as UserManager
    }

    val adminComponent: ComponentName by lazy {
        ComponentName(context, TruvaDeviceAdmin::class.java)
    }

    /**
     * İş profili mevcut mu? (Ana profilden doğru çalışan kontrol)
     *
     * NOT: dpm.isProfileOwnerApp() sadece İŞ PROFİLİ İÇİNDEN çalışır. Ana profilden çağrıldığında
     * her zaman false döner. Bu yüzden userManager.userProfiles.size kullanıyoruz.
     */
    fun isWorkProfileActive(): Boolean {
        return try {
            val profileCount = userManager.userProfiles.size
            Log.d(tag, "Profil sayısı: $profileCount")
            profileCount > 1
        } catch (e: Exception) {
            Log.w(tag, "İş profili kontrol hatası: ${e.message}")
            false
        }
    }

    /** Truva bu cihazda profil sahibi mi? Bu sadece iş profili İÇİNDEN çalışır. */
    fun isProfileOwner(): Boolean {
        return try {
            dpm.isProfileOwnerApp(context.packageName)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * İş profili oluşturma intent'ini döndür. Activity'den startActivityForResult ile çağrılmalı.
     */
    fun getProvisioningIntent(): Intent? {
        if (isWorkProfileActive()) {
            Log.w(tag, "İş profili zaten mevcut")
            return null
        }

        // Cihaz iş profili destekliyor mu?
        if (!canCreateWorkProfile()) {
            Log.w(tag, "Bu cihaz İş Profili oluşturmayı desteklemiyor")
            return null
        }

        return Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).apply {
            putExtra(
                    DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                    adminComponent
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true)
            }
            // Android 12+ (API 33): Kullanıcı onay ekranını atla
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_EDUCATION_SCREENS, true)
            }
        }
    }

    /**
     * Yeni iş profili oluşturulabilir mi?
     *
     * false dönme sebepleri:
     * 1. Cihaz desteklemiyor
     * 2. Zaten bir iş profili var (ikinci oluşturulamaz)
     */
    fun canCreateWorkProfile(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                dpm.isProvisioningAllowed(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE)
            } else {
                !isWorkProfileActive() // Profil yoksa deneriz
            }
        } catch (e: Exception) {
            Log.w(tag, "Provisioning kontrolü başarısız: ${e.message}")
            false
        }
    }

    /** Cihaz iş profili özelliğini destekliyor mu? (Zaten profil var olsa bile destekliyordur) */
    fun isDeviceCapable(): Boolean {
        // Zaten profil varsa → cihaz destekliyor demektir
        if (isWorkProfileActive()) return true
        // Profil yoksa → oluşturulabilir mi?
        return canCreateWorkProfile()
    }

    /** İş profilinin durumu hakkında detay döndür. */
    fun getProfileStatus(): WorkProfileStatus {
        val isActive = isWorkProfileActive()
        val canCreate = canCreateWorkProfile()
        val isCapable = isDeviceCapable()
        val profileCount =
                try {
                    userManager.userProfiles.size
                } catch (_: Exception) {
                    1
                }

        return WorkProfileStatus(
                isActive = isActive,
                canCreate = canCreate,
                isDeviceCapable = isCapable,
                profileCount = profileCount,
                statusMessage =
                        when {
                            isActive -> "İş profili aktif — uygulamalar izole çalışıyor"
                            canCreate -> "İş profili oluşturulabilir — henüz oluşturulmamış"
                            !isCapable -> "Bu cihaz iş profili desteklemiyor"
                            else -> "İş profili oluşturulamıyor"
                        }
        )
    }

    /**
     * İş profilini kaldır.
     *
     * Ana profilden çağrıldığında dpm.wipeData() çalışmaz (profil sahibi değiliz). Bu durumda
     * Android Ayarlar'a yönlendiriyoruz.
     *
     * @return Kaldırma başlatıldıysa true
     */
    fun removeWorkProfile(): Boolean {
        // Önce doğrudan kaldırmayı dene (profil sahibi isek)
        try {
            if (dpm.isProfileOwnerApp(context.packageName)) {
                dpm.wipeData(0)
                Log.i(tag, "İş profili doğrudan kaldırıldı (profil sahibi)")
                return true
            }
        } catch (e: Exception) {
            Log.d(tag, "Doğrudan kaldırma başarısız: ${e.message}")
        }

        // Ana profilden kaldırma → Ayarlar'a yönlendir
        return openWorkProfileSettings()
    }

    /**
     * İş profili içindeki bir uygulamanın iznini Shizuku olmadan zorla reddet. Sadece Profil Sahibi
     * (Work Profile içindeki Truva) olduğumuzda çalışır.
     */
    fun restrictPermissionAutomatically(packageName: String, permission: String): Boolean {
        return try {
            if (isProfileOwner()) {
                dpm.setPermissionGrantState(
                        adminComponent,
                        packageName,
                        permission,
                        DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
                )
                Log.i(tag, "$packageName için $permission izni otomatik reddedildi.")
                true
            } else {
                Log.d(tag, "Profil sahibi değiliz, otomatik kısıtlama yapılamadı.")
                false
            }
        } catch (e: Exception) {
            Log.e(tag, "İzin kısıtlama hatası: ${e.message}")
            false
        }
    }

    /**
     * İş profilindeki TÜM uygulamaların SIM/Telefon bilgisi okumasını engelle.
     *
     * Profile Owner yetkisiyle setPermissionGrantState(DENIED) kullanır.
     * Bu sayede uygulamalar getDeviceId(), getLine1Number(), getSimSerialNumber()
     * gibi izin gerektiren çağrılarda SecurityException alır.
     *
     * @return Kısıtlanan uygulama sayısı
     */
    fun lockdownAllAppsSimPermissions(): Int {
        if (!isProfileOwner()) {
            Log.w(tag, "Profil sahibi değiliz, toplu kısıtlama yapılamaz.")
            return 0
        }

        val pm = context.packageManager
        val apps = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
        var count = 0

        val restrictedPermissions = listOf(
            "android.permission.READ_PHONE_STATE",
            "android.permission.READ_PHONE_NUMBERS",
            "android.permission.READ_SMS",
            "android.permission.READ_CALL_LOG",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION"
        )

        apps.forEach { appInfo ->
            // Truva'nın kendisini ve sistem uygulamalarını atla
            if (appInfo.packageName == context.packageName) return@forEach
            if (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0) return@forEach

            restrictedPermissions.forEach { perm ->
                try {
                    dpm.setPermissionGrantState(
                        adminComponent,
                        appInfo.packageName,
                        perm,
                        DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
                    )
                } catch (e: Exception) {
                    // Bazı izinler bazı uygulamalarda tanımlı olmayabilir, sessizce atla
                    Log.d(tag, "${appInfo.packageName} için $perm kısıtlanamadı: ${e.message}")
                }
            }
            count++
        }
        Log.i(tag, "═══ $count uygulama için SIM/Telefon izinleri kısıtlandı ═══")
        return count
    }

    /**
     * Belirli bir uygulamanın tüm SIM/Telefon izinlerini toplu olarak kısıtla.
     */
    fun lockdownAppSimPermissions(packageName: String): Boolean {
        if (!isProfileOwner()) return false

        val permissions = listOf(
            "android.permission.READ_PHONE_STATE",
            "android.permission.READ_PHONE_NUMBERS",
            "android.permission.READ_SMS",
            "android.permission.READ_CALL_LOG"
        )

        var success = true
        permissions.forEach { perm ->
            if (!restrictPermissionAutomatically(packageName, perm)) {
                success = false
            }
        }
        return success
    }

    /**
     * Android Ayarlar'daki iş profili yönetim sayfasını aç. Kullanıcı buradan "İş profili kaldır"
     * yapabilir.
     */
    fun openWorkProfileSettings(): Boolean {
        return try {
            // Sırasıyla dene: en spesifikten en genele
            val candidates =
                    listOf(
                            "android.settings.MANAGED_PROFILE_SETTINGS",
                            Settings.ACTION_SYNC_SETTINGS,
                            "android.settings.USER_SETTINGS",
                            Settings.ACTION_SETTINGS
                    )

            var launched = false
            for (action in candidates) {
                try {
                    val intent =
                            Intent(action).apply {
                                addFlags(
                                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                                )
                            }
                    val resolveInfo = context.packageManager.resolveActivity(intent, 0)
                    if (resolveInfo != null) {
                        context.startActivity(intent)
                        Log.i(tag, "Ayarlar açıldı: $action")
                        launched = true
                        break
                    }
                } catch (e: Exception) {
                    Log.d(tag, "Action başarısız: $action — ${e.message}")
                }
            }

            if (!launched) {
                // Son çare: package ile doğrudan Settings aç
                val fallback =
                        Intent(Settings.ACTION_SETTINGS).apply {
                            addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            )
                        }
                context.startActivity(fallback)
                Log.i(tag, "Fallback ayarlar açıldı")
                launched = true
            }

            launched
        } catch (e: Exception) {
            Log.e(tag, "Ayarlar açılamadı", e)
            false
        }
    }

    /**
     * İş profilinde VPN kilidi (Always-On + Lockdown).
     *
     * Aktifken, iş profilindeki tüm uygulamalar yalnızca Truva VPN üzerinden
     * internete çıkabilir. VPN bağlı değilse internet tamamen kesilir.
     *
     * @param enabled true = VPN kilidi aç, false = kilidi kaldır
     * @return İşlem başarılı mı
     */
    fun setVpnLockdown(enabled: Boolean): Boolean {
        if (!isProfileOwner()) {
            Log.w(tag, "Profil sahibi değiliz, VPN lockdown yapılamaz.")
            return false
        }
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (enabled) {
                    dpm.setAlwaysOnVpnPackage(
                        adminComponent,
                        context.packageName,  // Truva'nın kendisi
                        true  // lockdownEnabled = true → VPN olmadan internet yok
                    )
                    Log.i(tag, "VPN Always-On Lockdown AKTİF: ${context.packageName}")
                } else {
                    dpm.setAlwaysOnVpnPackage(adminComponent, null, false)
                    Log.i(tag, "VPN Always-On Lockdown KALDIRILDI")
                }
                true
            } else {
                Log.w(tag, "VPN Lockdown Android 7+ gerektirir")
                false
            }
        } catch (e: Exception) {
            Log.e(tag, "VPN Lockdown hatası: ${e.message}")
            false
        }
    }

    companion object {
        const val REQUEST_PROVISION_PROFILE = 7001
    }
}

data class WorkProfileStatus(
        val isActive: Boolean,
        val canCreate: Boolean,
        val isDeviceCapable: Boolean,
        val profileCount: Int,
        val statusMessage: String
)
