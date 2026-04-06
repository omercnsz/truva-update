package com.truva.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.truva.TruvaViewModel

@Composable
fun PermissionDashboardScreen() {
    val context = LocalContext.current
    var refreshTrigger by remember { mutableStateOf(0) }

    val isNotificationsEnabled = remember(refreshTrigger) {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    val isBatteryOptimized = remember(refreshTrigger) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            !pm.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true // M öncesi kısıtlama yok varsayılır
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "İzinler ve Sağlık Durumu",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Truva VPN'in kusursuz çalışması için aşağıdaki izinlerin açık ve arka plan kısıtlamalarının kapalı olması gerekir.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(24.dp))

        // 1. Pil Optimizasyonu
        PermissionItem(
            title = "Pil Optimizasyonunu Kapat",
            description = "Bağlantının arka planda kopmaması için Truva VPN'i 'Kısıtlanmamış' olarak ayarlayın.",
            isGranted = isBatteryOptimized,
            icon = Icons.Default.BatteryStd,
            onActionClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Bildirimler
        PermissionItem(
            title = "Bildirim İzni",
            description = "VPN durumu ve ADB koptuğunda yeniden bağlanma uyarıları için gereklidir.",
            isGranted = isNotificationsEnabled,
            icon = Icons.Default.Notifications,
            onActionClick = {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
                context.startActivity(intent)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Mock Location App (Geliştirici Seçenekleri)
        val isMockLocationEnabled = remember(refreshTrigger) {
            var isGranted = false
            try {
                val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
                isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    appOpsManager.unsafeCheckOpNoThrow(
                        android.app.AppOpsManager.OPSTR_MOCK_LOCATION,
                        android.os.Process.myUid(),
                        context.packageName
                    ) == android.app.AppOpsManager.MODE_ALLOWED
                } else {
                    @Suppress("DEPRECATION")
                    appOpsManager.checkOpNoThrow(
                        "android:mock_location",
                        android.os.Process.myUid(),
                        context.packageName
                    ) == android.app.AppOpsManager.MODE_ALLOWED
                }
            } catch (e: Exception) {
                // Ignore
            }
            isGranted
        }

        PermissionItem(
            title = "Sahte Konum (Mock Location)",
            description = "GPS Spoofing için Geliştirici Seçeneklerinden Truva VPN'i 'Sahte Konum Seçilen Uygulama' yapın.",
            isGranted = isMockLocationEnabled,
            icon = Icons.Default.LocationOn,
            buttonText = "Geliştirici Ayarları'na Git",
            onActionClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // Cihazda geliştirici ayarları açık değilse ana ayarlara at
                    context.startActivity(Intent(Settings.ACTION_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))
        
        // 4. VPN Uyarısı
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("VPN Profil İzni", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(
                        "Uygulamayı ilk başlattığınızda çıkan 'Bağlantı İsteği' uyarısını onaylayın.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 5. Geliştirici Modu Onboarding
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeveloperMode, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Geliştirici Modu Nasıl Açılır?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "1. Ayarlar -> Telefon Hakkında'ya gidin.\n" +
                    "2. 'Derleme Numarası' (Build Number) üzerine 7 kez dokunun.\n" +
                    "3. 'Artık bir geliştiricisiniz' uyarısını görene kadar devam edin.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            context.startActivity(Intent(Settings.ACTION_SETTINGS).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Telefon Hakkında / Ayarlar'ı Aç")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { refreshTrigger++ },
            modifier = Modifier.align(Alignment.End)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Durumları Yenile")
        }
    }
}

@Composable
fun PermissionItem(
    title: String,
    description: String,
    isGranted: Boolean,
    icon: ImageVector,
    buttonText: String = "Ayarlara Git",
    onActionClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(text = description, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                if (!isGranted) {
                    FilledTonalButton(onClick = onActionClick, modifier = Modifier.fillMaxWidth()) {
                        Text(buttonText)
                    }
                } else {
                    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                        Text("Verildi / Gözden Geçirildi")
                    }
                }
            }
        }
    }
}
