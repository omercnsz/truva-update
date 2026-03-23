package com.truva.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.truva.SettingsEntity
import com.truva.TruvaViewModel
import com.truva.spoofing.RegionProfile
import com.truva.spoofing.SpoofingStatus

/**
 * Bölge Profili Seçimi ve Spoofing Ayarları Ekranı
 *
 * Kullanıcı burada:
 * 1. Hedef bölge profilini seçer
 * 2. Bireysel spoofing katmanlarını açıp kapatır
 * 3. Aktif spoofing durumunu görür
 */
@Composable
fun RegionProfileSection(
    viewModel: TruvaViewModel,
    settings: SettingsEntity,
    spoofingStatus: SpoofingStatus
) {
    val selectedProfile by viewModel.selectedRegionProfile.collectAsState()

    Column(modifier = Modifier.fillMaxWidth()) {

        // ── Spoofing Ana Anahtarı ──
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (settings.isSpoofingEnabled)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Bölge Spoofing",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (settings.isSpoofingEnabled)
                            "IP + SIM + GPS + Sistem senkronize"
                        else
                            "Tüm spoofing devre dışı",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Switch(
                    checked = settings.isSpoofingEnabled,
                    onCheckedChange = { viewModel.toggleSpoofing(it) }
                )
            }
        }

        // ── Aktif Spoofing Durumu ──
        if (spoofingStatus.isActive) {
            SpoofingStatusCard(spoofingStatus)
        }

        // ── Bölge Profili Seçimi ──
        if (settings.isSpoofingEnabled) {
            Text(
                "Hedef Bölge Seçimi",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )

            viewModel.regionProfiles.forEach { profile ->
                RegionProfileRow(
                    profile = profile,
                    isSelected = selectedProfile?.id == profile.id,
                    onSelect = { viewModel.selectRegionProfile(profile) }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // ── Bireysel Spoofing Kontrolleri ──
            Text(
                "Spoofing Katmanları",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            SpoofToggleRow(
                icon = Icons.Default.SimCard,
                title = "SIM Spoofing",
                subtitle = "Operatör, MCC/MNC, Ülke kodu",
                checked = settings.isSimSpoofEnabled,
                onToggle = { viewModel.toggleSimSpoof(it) }
            )
            SpoofInfoText(
                "⚠ Sınırlı: Sahte SIM bilgileri üretilir ancak diğer uygulamalar " +
                "gerçek SIM kartı okur. Tam çalışması için root + Xposed gerekir. " +
                "VPN ile IP adresiniz zaten seçtiğiniz bölgeye ait görünür."
            )

            SpoofToggleRow(
                icon = Icons.Default.GpsFixed,
                title = "GPS Spoofing",
                subtitle = "Enlem, Boylam, Mock Location",
                checked = settings.isGpsSpoofEnabled,
                onToggle = { viewModel.toggleGpsSpoof(it) }
            )
            
            // ── Mock Location Durumu ve Bilgilendirme ──
            val context = androidx.compose.ui.platform.LocalContext.current
            var refreshTrigger by remember { mutableStateOf(0) }
            val isMockLocationEnabled = remember(refreshTrigger) {
                var isGranted = false
                try {
                    val appOpsManager = context.getSystemService(android.content.Context.APP_OPS_SERVICE) as android.app.AppOpsManager
                    isGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
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
                } catch (e: Exception) { }
                isGranted
            }

            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isMockLocationEnabled) 
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    else 
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, (if (isMockLocationEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error).copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isMockLocationEnabled) Icons.Default.Check else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isMockLocationEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (isMockLocationEnabled) "Sahte Konum İzni: AKTİF" else "Sahte Konum İzni: EKSİK",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "GPS Spoofing için Geliştirici Seçenekleri -> 'Sahte konum uygulaması' -> Truva seçilmelidir.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (!isMockLocationEnabled) {
                        Button(
                            onClick = {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                                intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS).apply { flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK })
                                }
                                refreshTrigger++
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Geliştirici Ayarları'na Git", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            SpoofToggleRow(
                icon = Icons.Default.Timer,
                title = "Saat Dilimi",
                subtitle = "Timezone manipülasyonu",
                checked = settings.isTimezoneSpoofEnabled,
                onToggle = { viewModel.toggleTimezoneSpoof(it) }
            )
            SpoofInfoText(
                "✅ Çalışır: Uygulama içindeki saat dilimini seçilen bölgeye göre " +
                "değiştirir. Örneğin ABD seçildiğinde uygulama saati New York saatine döner. " +
                "Root gerektirmez."
            )

            SpoofToggleRow(
                icon = Icons.Default.Language,
                title = "Dil & Bölge",
                subtitle = "Locale manipülasyonu",
                checked = settings.isLocaleSpoofEnabled,
                onToggle = { viewModel.toggleLocaleSpoof(it) }
            )
            SpoofInfoText(
                "✅ Çalışır: Uygulama dilini ve bölge formatını seçilen profile göre " +
                "ayarlar. Örneğin Japonya seçildiğinde tarih/sayı formatı Japonca olur. " +
                "Root gerektirmez."
            )

            SpoofToggleRow(
                icon = Icons.Default.PhoneAndroid,
                title = "Cihaz Kimliği",
                subtitle = "Android ID, IMEI, Build.SERIAL",
                checked = settings.isDeviceIdSpoofEnabled,
                onToggle = { viewModel.toggleDeviceIdSpoof(it) }
            )
            SpoofInfoText(
                "⚠ Kısmi: Her oturumda rastgele sahte cihaz kimliği üretilir. " +
                "Truva içinde geçerlidir ancak diğer uygulamalar kendi gerçek ID'lerini okur. " +
                "Tam çalışması için root + Xposed gerekir."
            )

            // ── Otomatik Eşleşme ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Otomatik Bölge Eşleşmesi", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Proxy değişince profil otomatik güncellenir",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Switch(
                    checked = settings.isAutoSyncRegion,
                    onCheckedChange = { viewModel.toggleAutoSyncRegion(it) }
                )
            }
        }
    }
}

@Composable
fun RegionProfileRow(
    profile: RegionProfile,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        onClick = onSelect,
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        border = if (isSelected) null
        else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bayrak
            Text(
                text = profile.flagEmoji,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(end = 12.dp)
            )

            // Bilgiler
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = "${profile.simOperatorName} | ${profile.timezone}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Seçim göstergesi
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Seçili",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            RadioButton(selected = isSelected, onClick = onSelect)
        }
    }
}

@Composable
fun SpoofingStatusCard(status: SpoofingStatus) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = status.flagEmoji ?: "",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = status.regionName ?: "Bilinmiyor",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // SIM durumu
            status.sim["operatör"]?.let { op ->
                Text(
                    "SIM: $op (${status.sim["MCC/MNC"] ?: ""})",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            // GPS durumu
            status.gps["konum"]?.let { loc ->
                Text(
                    "GPS: $loc (${status.gps["enlem"] ?: ""}, ${status.gps["boylam"] ?: ""})",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            // System durumu
            status.system["timezone"]?.let { tz ->
                Text(
                    "TZ: $tz | Locale: ${status.system["locale"] ?: ""}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun SpoofToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp).padding(end = 0.dp),
            tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
fun SpoofInfoText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 36.dp, end = 8.dp, bottom = 8.dp)
    )
}
