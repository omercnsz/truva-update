package com.truva.sandbox.adb

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AdbPairingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val pairingCode = remoteInput?.getCharSequence("KEY_CODE")?.toString()?.trim() ?: return

        val pPort = AdbScanner.pairingPort
        val cPort = AdbScanner.connectPort

        if (pPort != null && cPort != null && pairingCode.isNotEmpty()) {
            Toast.makeText(context, "Truva: Eşleştiriliyor...", Toast.LENGTH_SHORT).show()

            CoroutineScope(Dispatchers.IO).launch {
                val success = TruvaAdbClient.pairAndConnect(context, pPort, cPort, pairingCode)

                CoroutineScope(Dispatchers.Main).launch {
                    val msg =
                            if (success) "Truva: BAĞLANTI BAŞARILI! 🚀"
                            else "Truva: Eşleştirme Başarısız!"
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

                    val nm =
                            context.getSystemService(Context.NOTIFICATION_SERVICE) as
                                    NotificationManager
                    nm.cancel(1001)
                }
            }
        } else {
            Toast.makeText(context, "Portlar henüz bulunamadı veya şifre boş!", Toast.LENGTH_LONG)
                    .show()
        }
    }

    companion object {
        fun showNotification(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "truva_adb"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                        NotificationChannel(
                                channelId,
                                "ADB Eşleştirme",
                                NotificationManager.IMPORTANCE_HIGH
                        )
                )
            }

            val remoteInput =
                    RemoteInput.Builder("KEY_CODE").setLabel("6 Haneli Şifreyi Girin").build()

            val replyIntent = Intent(context, AdbPairingReceiver::class.java)
            val replyPendingIntent =
                    PendingIntent.getBroadcast(
                            context,
                            0,
                            replyIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )

            val action =
                    NotificationCompat.Action.Builder(
                                    android.R.drawable.ic_menu_send,
                                    "Şifreyi Gönder",
                                    replyPendingIntent
                            )
                            .addRemoteInput(remoteInput)
                            .build()

            val notification =
                    NotificationCompat.Builder(context, channelId)
                            .setSmallIcon(android.R.drawable.stat_sys_warning)
                            .setContentTitle("Truva: Cihaz Bulundu!")
                            .setContentText("Lütfen ekrandaki 6 haneli şifreyi girin.")
                            .addAction(action)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setOngoing(true)
                            .build()

            nm.notify(1001, notification)
        }
    }
}
