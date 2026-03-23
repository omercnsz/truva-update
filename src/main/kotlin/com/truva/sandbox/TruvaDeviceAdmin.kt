package com.truva.sandbox

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log

/**
 * TruvaDeviceAdmin — İş Profili Device Admin Receiver
 *
 * Android'in DevicePolicyManager API'si ile iş profili oluşturmak için gerekli. Truva iş profili
 * sahibi (profile owner) olarak atandığında bu receiver aktif olur.
 */
class TruvaDeviceAdmin : DeviceAdminReceiver() {

    private val tag = "TruvaDeviceAdmin"

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        Log.i(tag, "İş profili kurulumu tamamlandı")

        val manager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        val componentName = ComponentName(context, TruvaDeviceAdmin::class.java)

        try {
            manager.setProfileName(componentName, "Truva Sandbox")
            manager.setProfileEnabled(componentName)
            Log.i(tag, "İş profili etkinleştirildi: Truva Sandbox")

            // Cross-profile intent filtrelerini kur
            setupCrossProfileFilters(context)

            // İş profilindeki tüm uygulamaların SIM/Telefon izinlerini kilitle
            applySecurityLockdown(context)

            // VPN kilidi: İnternet sadece Truva VPN üzerinden
            applyVpnLockdown(context)

            // Gmail, Chrome ve Google Play Services'i iş profilinde etkinleştir
            enableEssentialApps(context, manager, componentName)
        } catch (e: Exception) {
            Log.e(tag, "Profil etkinleştirme hatası", e)
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(tag, "Device Admin etkinleştirildi")
        // Device admin etkinleştirildiğinde de VPN kilidini uygula
        applyVpnLockdown(context)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.i(tag, "Device Admin devre dışı")
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Cihaz yeniden başladığında VPN kilidini otomatik uygula
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                Log.i(tag, "Boot algılandı — VPN kilidi yeniden uygulanıyor")
                applyVpnLockdown(context)
                applySecurityLockdown(context)
            }
        }
    }

    companion object {
        private const val TAG = "TruvaDeviceAdmin"

        /**
         * Cross-profile intent filtrelerini kur veya yenile.
         *
         * Bu metod her zaman çağrılabilir (sadece onProfileProvisioningComplete değil). Mevcut
         * filtreleri temizleyip yeniden ekler.
         *
         * SADECE iş profili (managed profile) bağlamında çalışır. Ana profilden çağrılırsa sessizce
         * atlar.
         */
        fun setupCrossProfileFilters(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return

            val manager =
                    context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val componentName = ComponentName(context, TruvaDeviceAdmin::class.java)

            // Sadece profil sahibi isek çalıştır
            if (!manager.isProfileOwnerApp(context.packageName)) {
                Log.d(TAG, "Profil sahibi değiliz, cross-profile filtreler atlanıyor")
                return
            }

            try {
                // Önce mevcut filtreleri temizle (yenileme için)
                manager.clearCrossProfileIntentFilters(componentName)
                Log.d(TAG, "Mevcut cross-profile filtreler temizlendi")

                // ── Çift yönlü Cross-Profile Intent Filtreleri ──
                // FLAG_PARENT_CAN_ACCESS_MANAGED: Ana → İş profili
                // FLAG_MANAGED_CAN_ACCESS_PARENT: İş → Ana profil
                val flagToManaged = DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED
                val flagToParent = DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT

                // ════════════════════════════════════════════════
                // Cross-Profile Intent Filtreleri
                // ════════════════════════════════════════════════

                // ACTION_SEND ile dosya paylaşımı (Örnek)
                val sendFilter =
                        IntentFilter(Intent.ACTION_SEND).apply {
                            addCategory(Intent.CATEGORY_DEFAULT)
                            addDataType("*/*")
                        }
                manager.addCrossProfileIntentFilter(
                        componentName,
                        sendFilter,
                        DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED
                )
                Log.i(TAG, "Cross-profile ACTION_SEND filtresi eklendi")

                // İş profili → Ana profil: truvavpn:// deep link'leri (oturum senkronizasyonu)
                val truvaFilter = IntentFilter(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    addDataScheme("truvavpn")
                }
                manager.addCrossProfileIntentFilter(
                        componentName,
                        truvaFilter,
                        DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT
                )
                Log.i(TAG, "Cross-profile truvavpn:// filtresi eklendi (İş → Ana)")

                Log.i(TAG, "═══ Cross-profile filtreler başarıyla kuruldu ═══")
            } catch (e: Exception) {
                Log.e(TAG, "Cross-profile filtre kurulum hatası", e)
            }
        }

        /**
         * İş profilinde güvenlik kilitlemesi uygula.
         *
         * 1. Tüm uygulamaların SIM/Telefon izinlerini toplu reddet
         * 2. UserRestrictions ile hassas verilere erişimi kısıtla
         *
         * Bu metod hem ilk kurulumda hem de her uygulama açılışında çağrılabilir.
         */
        fun applySecurityLockdown(context: Context) {
            val manager =
                    context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val componentName = ComponentName(context, TruvaDeviceAdmin::class.java)

            if (!manager.isProfileOwnerApp(context.packageName)) {
                Log.d(TAG, "Profil sahibi değiliz, güvenlik kilitlemesi atlanıyor")
                return
            }

            try {
                // 1. Tüm uygulamaların SIM izinlerini toplu kısıtla
                val wpm = WorkProfileManager(context)
                val count = wpm.lockdownAllAppsSimPermissions()
                Log.i(TAG, "Toplu SIM kilitleme: $count uygulama kısıtlandı")

                // 2. UserRestrictions — Sistem seviyesinde kısıtlamalar
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // SMS gönderimini engelle
                    manager.addUserRestriction(componentName, android.os.UserManager.DISALLOW_SMS)
                    // Giden aramaları engelle (SIM numarası sızıntısını önler)
                    manager.addUserRestriction(componentName, android.os.UserManager.DISALLOW_OUTGOING_CALLS)
                    Log.i(TAG, "UserRestrictions uygulandı: SMS ve arama engellendi")
                }

                Log.i(TAG, "═══ Güvenlik kilitlemesi tamamlandı ═══")
            } catch (e: Exception) {
                Log.e(TAG, "Güvenlik kilitlemesi hatası", e)
            }
        }

        /**
         * VPN Always-On Lockdown — İnternet sadece Truva VPN üzerinden.
         *
         * Profil sahibi olduğumuz sürece VPN kilidi uygular.
         * VPN bağlı değilken internet tamamen kesilir.
         */
        fun applyVpnLockdown(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                Log.w(TAG, "VPN Lockdown Android 7+ gerektirir")
                return
            }

            val manager =
                    context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val componentName = ComponentName(context, TruvaDeviceAdmin::class.java)

            if (!manager.isProfileOwnerApp(context.packageName)) {
                Log.d(TAG, "Profil sahibi değiliz, VPN lockdown atlanıyor")
                return
            }

            try {
                manager.setAlwaysOnVpnPackage(
                        componentName,
                        context.packageName,  // Truva'nın kendisi
                        true  // lockdownEnabled = true → VPN olmadan internet yok
                )
                Log.i(TAG, "═══ VPN Always-On Lockdown AKTİF: ${context.packageName} ═══")
            } catch (e: Exception) {
                Log.e(TAG, "VPN Lockdown hatası: ${e.message}")
            }
        }

        /**
         * İş profilinde Gmail, Chrome ve Google Play Services'i etkinleştir.
         *
         * enableSystemApp() ile sistem uygulamalarını iş profili içinde görünür yapar.
         * Kullanıcı Gmail ile kayıt olabilir ve Chrome ile web'e erişebilir.
         */
        fun enableEssentialApps(
            context: Context,
            manager: DevicePolicyManager,
            componentName: ComponentName
        ) {
            val essentialApps = listOf(
                "com.google.android.gm",      // Gmail
                "com.android.chrome",          // Chrome tarayıcı
                "com.google.android.gms",      // Google Play Services
                "com.google.android.gsf",      // Google Services Framework
                "com.android.vending",         // Google Play Store
                "com.google.android.apps.messaging"  // Google Messages (isteğe bağlı)
            )

            essentialApps.forEach { packageName ->
                try {
                    manager.enableSystemApp(componentName, packageName)
                    Log.i(TAG, "Sistem uygulaması etkinleştirildi: $packageName")
                } catch (e: Exception) {
                    // Uygulama cihazda yoksa sessizce atla
                    Log.d(TAG, "Sistem uygulaması etkinleştirilemedi: $packageName — ${e.message}")
                }
            }
            Log.i(TAG, "═══ Temel uygulamalar iş profilinde etkinleştirildi ═══")
        }
    }
}
