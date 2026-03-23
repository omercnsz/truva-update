package com.truva.sandbox.shizuku

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * TruvaPermissionManager — İzin yönetim yardımcı sınıfı. Shizuku bağımlılığı kaldırılmıştır. Manuel
 * kısıtlama için Ayarlar'a yönlendirir.
 */
object TruvaPermissionManager {
    private const val TAG = "TruvaPermissionManager"

    /** Kullanıcıyı uygulamanın "Uygulama Bilgisi" sayfasına gönderir. */
    fun openAppSettings(context: Context, packageName: String) {
        try {
            val intent =
                    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
            context.startActivity(intent)
            Log.i(TAG, "$packageName için Ayarlar sayfası açıldı.")
        } catch (e: Exception) {
            Log.e(TAG, "Ayarlar sayfası açılamadı", e)
        }
    }

    /** ADB Shell üzerinden AppOps kısıtlaması uygular (LADB mantığı). */
    fun restrictViaAdb(
            context: android.content.Context,
            packageName: String,
            op: String,
            mode: String
    ): Boolean {
        return try {
            // TruvaAdbClient motorunu kullanarak komutu cihaza bas
            val result =
                    com.truva.sandbox.adb.TruvaAdbClient.executeAppOpsCommand(
                            context,
                            packageName,
                            op,
                            mode
                    )

            if (result) {
                Log.i("TruvaADB", "AppOps başarıyla uygulandı: $packageName -> $mode")
            }
            result
        } catch (e: Exception) {
            Log.e("TruvaADB", "ADB kısıtlaması başarısız oldu", e)
            false
        }
    }
}
