package com.truva.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.truva.TruvaViewModel

/**
 * Bağlantı Durumu (IP Lokasyon) Modülü
 *
 * "What is my IP" mantığıyla kullanıcının dış dünyaya hangi IP ve konumla göründüğünü gösterir. VPN
 * aktifken IP'nin değiştiğini teyit ettirir.
 */

/** IP bilgilerini tutan veri sınıfı */
data class IpInfo(
        val query: String = "",
        val country: String = "",
        val city: String = "",
        val isp: String = "",
        val lat: Double = 0.0,
        val lon: Double = 0.0,
        val status: String = "pending" // "success", "fail", "pending", "loading"
)

@Composable
fun IpStatusSection(viewModel: TruvaViewModel) {
    val ipInfo by viewModel.ipInfo.collectAsState()
    val isLoading = ipInfo.status == "loading"
    val isSuccess = ipInfo.status == "success"
    val isPending = ipInfo.status == "pending"

    // İlk açılışta otomatik sorgula
    LaunchedEffect(Unit) {
        if (isPending) {
            viewModel.checkCurrentLocation()
        }
    }

    Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors =
                    CardDefaults.cardColors(
                            containerColor =
                                    when {
                                        isSuccess -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                        ipInfo.status == "fail" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    }
                    ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ── Başlık Satırı ──
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                            Icons.Default.Public,
                            contentDescription = null,
                            tint =
                                    if (isSuccess) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                            "Bağlantı Durumu",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                    )
                }

                // Yenile butonu
                IconButton(onClick = { viewModel.checkCurrentLocation() }, enabled = !isLoading) {
                    if (isLoading) {
                        CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Yenile",
                                tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── Bilgi Satırları ──
            when {
                isLoading -> {
                    Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                                "IP bilgileri sorgulanıyor…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                isPending -> {
                    Text(
                            "IP bilgilerini görmek için yenile butonuna basın.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                ipInfo.status == "fail" -> {
                    Text(
                            "⚠ ${ipInfo.query.ifEmpty { "İnternet bağlantısı yok veya servis yanıt vermiyor." }}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                isSuccess -> {
                    IpInfoRow(icon = Icons.Default.Wifi, label = "IP Adresi", value = ipInfo.query)
                    IpInfoRow(
                            icon = Icons.Default.LocationOn,
                            label = "Konum",
                            value =
                                    buildString {
                                        if (ipInfo.city.isNotEmpty()) append(ipInfo.city)
                                        if (ipInfo.country.isNotEmpty()) {
                                            if (isNotEmpty()) append(", ")
                                            append(ipInfo.country)
                                        }
                                    }
                    )
                    IpInfoRow(icon = Icons.Default.Shield, label = "İSS", value = ipInfo.isp)
                }
            }
        }
    }
}

@Composable
private fun IpInfoRow(icon: ImageVector, label: String, value: String) {
    if (value.isEmpty()) return
    Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
                text = "$label: ",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
        )
        Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
