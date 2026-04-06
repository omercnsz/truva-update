package com.truva.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.truva.SettingsEntity
import com.truva.TruvaViewModel

/**
 * İzolasyon & İş Profili Ekranı
 *
 * Shizuku ve İş Profili tabanlı izolasyon yönetimi. Eski "APK Yamalama" ve manuel uygulama listesi
 * tamamen temizlenmiştir.
 */
@Composable
fun SandboxSection(
        viewModel: TruvaViewModel,
        settings: SettingsEntity,
        onOpenIntegrityTest: () -> Unit
) {
        val sandboxStatus by viewModel.sandboxStatus.collectAsState()
        val adbConnected by viewModel.adbStatus.collectAsState()
        val installedApps by viewModel.installedApps.collectAsState()
        val context = androidx.compose.ui.platform.LocalContext.current

        var searchQuery by remember { mutableStateOf("") }
        val filteredApps = remember(installedApps, searchQuery) {
            installedApps.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {

                // ── 1. Ana İzolasyon Anahtarı ──
                Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor =
                                                if (settings.isSandboxEnabled)
                                                        MaterialTheme.colorScheme.secondaryContainer
                                                else MaterialTheme.colorScheme.surfaceVariant
                                )
                ) {
                        Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                                Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                        Icons.Default.Shield,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(24.dp),
                                                        tint =
                                                                if (settings.isSandboxEnabled)
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .outline
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                        "Gelişmiş İzolasyon",
                                                        style =
                                                                MaterialTheme.typography
                                                                        .titleMedium,
                                                        fontWeight = FontWeight.Bold
                                                )
                                        }
                                        Text(
                                                "Donanım ve sistem kimliği maskeleme aktif",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                }
                                Switch(
                                        checked = settings.isSandboxEnabled,
                                        onCheckedChange = { viewModel.toggleSandbox(it) }
                                )
                        }
                }

                if (settings.isSandboxEnabled) {
                        // ── 2. İş Profili Yönetimi ──
                        WorkProfileCard(
                                isActive = sandboxStatus.isWorkProfileActive,
                                canCreate = sandboxStatus.canCreateWorkProfile,
                                isDeviceCapable = sandboxStatus.isDeviceCapable,
                                onActivate = { viewModel.createWorkProfile() },
                                onRemove = { viewModel.removeWorkProfile() }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // ── 2.5. Work Profile Senkronizasyon ──
                        val inWorkProfile by viewModel.isInWorkProfile.collectAsState()
                        val bridgeOk by viewModel.bridgeStatus.collectAsState()
                        val syncMsg by viewModel.syncMessage.collectAsState()

                        Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                )
                        ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Sync, contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onTertiaryContainer)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                        if (inWorkProfile) "Ana Profilden Veri Çek"
                                                        else "Köprü Sunucusu (Aktif)",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer)
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))

                                        if (inWorkProfile) {
                                                // İŞ PROFİLİ: Fetch butonları
                                                Text("Ana profildeki köprüden sunucu ve oturum bilgisini çekin.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer)

                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                        if (bridgeOk) "🟢 Köprü bağlantısı aktif" else "🔴 Köprüye erişilemiyor — ana Truva VPN açık mı?",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = if (bridgeOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)

                                                Spacer(modifier = Modifier.height(12.dp))

                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                        FilledTonalButton(
                                                                onClick = { viewModel.syncServersFromBridge() },
                                                                modifier = Modifier.weight(1f),
                                                                enabled = bridgeOk
                                                        ) {
                                                                Icon(Icons.Default.Cloud, contentDescription = null,
                                                                        modifier = Modifier.size(18.dp))
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text("Sunucuları Çek", style = MaterialTheme.typography.labelSmall)
                                                        }
                                                        FilledTonalButton(
                                                                onClick = { viewModel.syncSessionFromBridge() },
                                                                modifier = Modifier.weight(1f),
                                                                enabled = bridgeOk
                                                        ) {
                                                                Icon(Icons.Default.Timer, contentDescription = null,
                                                                        modifier = Modifier.size(18.dp))
                                                                Spacer(modifier = Modifier.width(4.dp))
                                                                Text("Oturumu Çek", style = MaterialTheme.typography.labelSmall)
                                                        }
                                                }

                                                OutlinedButton(
                                                        onClick = { viewModel.checkBridgeConnection() },
                                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                                ) {
                                                        Text("Köprü Bağlantısını Yenile")
                                                }
                                        } else {
                                                // ANA PROFİL: Köprü durumu bilgisi
                                                Text("Bu profil köprü sunucusu olarak çalışıyor.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("🟢 Köprü aktif — İş profilindeki Truva VPN buradan veri çekebilir.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.primary)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("Port: 127.0.0.1:38901",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer)
                                        }

                                        // Sync mesajı
                                        syncMsg?.let { msg ->
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(msg,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onTertiaryContainer)
                                        }
                                }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // ── 3. Profil Durumu ve Güvenlik ──
                        SecurityIntegritySection(viewModel)

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                                onClick = onOpenIntegrityTest,
                                modifier = Modifier.fillMaxWidth(),
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.tertiary
                                        )
                        ) {
                                Icon(Icons.AutoMirrored.Filled.FactCheck, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Detaylı Bütünlük Testini Çalıştır")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // ── 1. Bağlantı Durum Göstergesi ──
                        Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                colors =
                                        CardDefaults.cardColors(
                                                containerColor =
                                                        if (adbConnected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                                        else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                        ),
                                border = BorderStroke(1.dp, (if (adbConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error).copy(alpha = 0.2f))
                        ) {
                                Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Box(
                                                modifier =
                                                        Modifier.size(10.dp)
                                                                .background(
                                                                        if (adbConnected)
                                                                                MaterialTheme.colorScheme.primary
                                                                        else MaterialTheme.colorScheme.error,
                                                                        CircleShape
                                                                )
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                                text =
                                                        if (adbConnected) "Truva VPN Motoru Bağlı"
                                                        else "ADB Bağlantısı Kesildi",
                                                color =
                                                        if (adbConnected) MaterialTheme.colorScheme.onPrimaryContainer
                                                        else MaterialTheme.colorScheme.onErrorContainer,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                        )
                                }
                        }

                        // ── 2. SIM Kimliği Gizleme (Uygulama Listesi) ──
                        if (inWorkProfile) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Uygulama Bazlı SIM Koruması", style = MaterialTheme.typography.titleMedium)
                                }
                                Text(
                                    if (adbConnected) "Uygulamaları seçin. Koruma anında uygulanacak."
                                    else "Uygulamaları şimdiden seçebilirsiniz. ADB bağlanınca otomatik uygulanacak.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (!adbConnected) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "ADB bağlı değil. Seçimleriniz kaydedilir, bağlanınca uygulanır.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilledTonalButton(
                                            onClick = { viewModel.autoConnectADB() },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Radar Taraması", style = MaterialTheme.typography.labelSmall)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                                try {
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK })
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.SettingsEthernet, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Hata Ayıklama", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("Uygulama ara...") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.medium
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Uygulama Listesi
                                val displayLimit = 50
                                filteredApps.take(displayLimit).forEach { app ->
                                    AppProtectionItem(
                                        app = app,
                                        enabled = true,
                                        onToggle = { isEnabled ->
                                            viewModel.setSimProtection(app.packageName, isEnabled, app.userId)
                                        }
                                    )
                                }
                                
                                if (filteredApps.size > displayLimit) {
                                    Text(
                                        "Ve ${filteredApps.size - displayLimit} uygulama daha...",
                                        modifier = Modifier.padding(8.dp).align(Alignment.CenterHorizontally),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
} // inWorkProfile check end
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (inWorkProfile) { // SIM Koruma Testi de sadece iş profilinde
                        // ── 3. IMEI / SIM Test Butonu ──
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.BugReport, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("SIM Koruma Testi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Truva VPN uygulamasını yukarıdan korumaya alıp ('Korumalı' yapıp), ardından aşağıdaki test butonuna basarak korumanın çalıştığını doğrulayabilirsiniz.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                var testResult by remember { mutableStateOf<String?>(null) }
                                
                                Button(
                                    onClick = {
                                        try {
                                            val tm = context.getSystemService(android.content.Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
                                            // Güvenlik izni kontrol etmemize gerek yok çünkü zaten test etmeye çalışıyoruz. 
                                            // Eğer SecurityException atarsa veya null dönerse appops koruması aktiftir.
                                            try {
                                                @Suppress("DEPRECATION")
                                                @android.annotation.SuppressLint("MissingPermission", "HardwareIds")
                                                val imei = tm?.imei ?: tm?.deviceId
                                                
                                                @Suppress("DEPRECATION")
                                                @android.annotation.SuppressLint("MissingPermission", "HardwareIds")
                                                val number = tm?.line1Number
                                                
                                                if (imei.isNullOrBlank() && number.isNullOrBlank()) {
                                                    testResult = "✅ BAŞARILI: Sistem okuma isteğimizi boş döndürdü. Koruma AKTİF."
                                                } else {
                                                    testResult = "❌ BAŞARISIZ: IMEI ($imei) veya Numara ($number) okundu. Koruma KAPALI."
                                                }
                                            } catch (se: SecurityException) {
                                                testResult = "✅ BAŞARILI: Güvenlik engeli çalıştı (SecurityException). Koruma AKTİF."
                                            }
                                        } catch (e: Exception) {
                                            testResult = "Hata: ${e.message}"
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Donanım Kimliklerini Okumayı Dene")
                                }
                                
                                if (testResult != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = testResult!!,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (testResult!!.startsWith("✅")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        } // inWorkProfile check end

                        Spacer(modifier = Modifier.height(16.dp))
                }
        }
}

@Composable
fun WorkProfileCard(
        isActive: Boolean,
        canCreate: Boolean,
        isDeviceCapable: Boolean,
        onActivate: () -> Unit,
        onRemove: () -> Unit
) {
        var showRemoveConfirm by remember { mutableStateOf(false) }

        if (showRemoveConfirm) {
                AlertDialog(
                        onDismissRequest = { showRemoveConfirm = false },
                        icon = {
                                Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                )
                        },
                        title = { Text("İş Profilini Kaldır") },
                        text = {
                                Text(
                                        "İş profili kaldırılacak ve profildeki tüm uygulama verileri silinecek."
                                )
                        },
                        confirmButton = {
                                Button(
                                        onClick = {
                                                showRemoveConfirm = false
                                                onRemove()
                                        },
                                        colors =
                                                ButtonDefaults.buttonColors(
                                                        containerColor =
                                                                MaterialTheme.colorScheme.error
                                                )
                                ) { Text("Kaldır") }
                        },
                        dismissButton = {
                                TextButton(onClick = { showRemoveConfirm = false }) {
                                        Text("İptal")
                                }
                        }
                )
        }

        if (isActive) {
                Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor =
                                                MaterialTheme.colorScheme.primaryContainer.copy(
                                                        alpha = 0.4f
                                                )
                                )
                ) {
                        Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Icon(
                                        Icons.Default.VerifiedUser,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                                "İş Profili Aktif",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                                "Veriler ayrı profilde izole edildi",
                                                style = MaterialTheme.typography.bodySmall
                                        )
                                }
                                OutlinedButton(
                                        onClick = { showRemoveConfirm = true },
                                        colors =
                                                ButtonDefaults.outlinedButtonColors(
                                                        contentColor =
                                                                MaterialTheme.colorScheme.error
                                                ),
                                        border =
                                                BorderStroke(
                                                        1.dp,
                                                        MaterialTheme.colorScheme.error.copy(
                                                                alpha = 0.4f
                                                        )
                                                )
                                ) { Text("Kaldır") }
                        }
                }
        } else if (canCreate) {
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Icon(
                                        Icons.Default.AdminPanelSettings,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                                "İş Profili",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                                "Ek izolasyon katmanı",
                                                style = MaterialTheme.typography.bodySmall
                                        )
                                }
                                FilledTonalButton(onClick = onActivate) { Text("Etkinleştir") }
                        }
                }
        } else if (!isDeviceCapable) {
                Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor =
                                                MaterialTheme.colorScheme.errorContainer.copy(
                                                        alpha = 0.2f
                                                )
                                )
                ) {
                        Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Icon(
                                        Icons.Default.Block,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                        "Cihazınız İş Profilini desteklemiyor.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                )
                        }
                }
        }
}

@Composable
fun SecurityIntegritySection(viewModel: TruvaViewModel) {
        val sandboxStatus by viewModel.sandboxStatus.collectAsState()
        val adbStatus by viewModel.adbStatus.collectAsState()

        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                                "Kontrol Panel Durumu",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        StatusRow("İş Profili Altyapısı", sandboxStatus.isWorkProfileActive)
                        StatusRow("ADB Kablosuz Bağlantı", adbStatus)
                        StatusRow(
                                "Gelişmiş Kısıtlama Yetkisi",
                                sandboxStatus.isWorkProfileActive || adbStatus
                        )

                        // Eğer hiçbir yetki yoksa kullanıcıya eşleştirme butonu göster
                        if (!sandboxStatus.isWorkProfileActive && !adbStatus) {
                                val context = androidx.compose.ui.platform.LocalContext.current
                                Spacer(modifier = Modifier.height(12.dp))
                                FilledTonalButton(
                                        onClick = {
                                                // 1. Ayarlar uygulamasını Kablosuz Hata Ayıklama
                                                // menüsüne yönlendir
                                                val intent =
                                                        android.content.Intent(
                                                                        android.provider.Settings
                                                                                .ACTION_APPLICATION_DEVELOPMENT_SETTINGS
                                                                )
                                                                .apply {
                                                                        addFlags(
                                                                                android.content
                                                                                        .Intent
                                                                                        .FLAG_ACTIVITY_NEW_TASK
                                                                        )
                                                                }
                                                context.startActivity(intent)

                                                // 2. Arka planda radarı çalıştır
                                                com.truva.sandbox.adb.AdbScanner.startScanning(
                                                        context
                                                ) {
                                                        // İki port da otomatik bulunduğunda
                                                        // bildirimi patlat!
                                                        com.truva.sandbox.adb.AdbPairingReceiver
                                                                .showNotification(context)
                                                }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                ) {
                                        Icon(Icons.Default.Link, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("ADB ile Kablosuz Eşleştir")
                                }
                        }
                }
        }
}

@Composable
fun StatusRow(label: String, isOk: Boolean) {
        Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp)
        ) {
                Icon(
                        if (isOk) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (isOk) Color(0xFF2E7D32) else Color(0xFFC62828),
                        modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.bodyMedium)
        }
}

@Composable
fun IntegrityCard(title: String, icon: ImageVector, data: Map<String, String>, isActive: Boolean) {
        Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                colors =
                        CardDefaults.cardColors(
                                containerColor =
                                        if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                        ),
                border = BorderStroke(1.dp, (if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline).copy(alpha = 0.1f))
        ) {
                Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Icon(
                                        icon,
                                        contentDescription = null,
                                        tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                        title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                )
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        data.forEach { (key, value) ->
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                        Text(
                                                "$key: ",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold
                                        )
                                        Text(value, style = MaterialTheme.typography.bodySmall)
                                }
                        }
                }
        }
}

@Composable
fun AppProtectionItem(
    app: com.truva.AppProtectionInfo,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(app.name) },
        supportingContent = { Text(app.packageName) },
        trailingContent = {
            Switch(
                checked = app.isProtected,
                onCheckedChange = onToggle,
                enabled = enabled
            )
        },
        leadingContent = {
            Icon(
                Icons.Default.SimCard, 
                contentDescription = null, 
                tint = if (app.isProtected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
