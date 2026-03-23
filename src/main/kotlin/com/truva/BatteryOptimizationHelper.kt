package com.truva

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Pil optimizasyonu muafiyeti modülü.
 * Uygulamanın arka planda kısıtlanmadan çalışması için sistemden muafiyet ister.
 */
object BatteryOptimizationHelper {

    /**
     * Uygulamanın pil optimizasyonundan muaf olup olmadığını döner.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Muafiyet yoksa kullanıcıyı ayar sayfasına yönlendirir veya (Android 6+) doğrudan
     * REQUEST_IGNORE_BATTERY_OPTIMIZATIONS ile izin isteyebilir.
     * Manifest'te android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS tanımlı olmalı.
     */
    fun requestExemptionIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (isIgnoringBatteryOptimizations(context)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent().apply {
                action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                // Bazı cihazlarda doğrudan izin verilmez; kullanıcıyı pil ayarlarına götür
                openBatterySettings(context)
            }
        }
    }

    /**
     * Pil / uygulama optimizasyonu ayarlarını açar (yedek yol).
     */
    fun openBatterySettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) { }
    }
}
