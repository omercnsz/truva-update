package com.truva.ui

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.truva.nidg.NidgEngine
import com.truva.nidg.NidgReport
import com.truva.nidg.NidgVpnManager

/**
 * NIDG Dashboard — Tam Ağ Analiz Raporu + VPN Toggle + Kalibrasyon
 */
@Composable
fun NidgDashboardScreen(report: NidgReport) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val nidgVpnState by NidgVpnManager.state.collectAsState()

    // VPN izni launcher
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            NidgVpnManager.connect(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ═══════════════════════════════════════════
        // 1. NIDG VPN BAĞLANTI KARTI
        // ═══════════════════════════════════════════
        NidgVpnCard(
            vpnState = nidgVpnState,
            onConnect = {
                // VPN izni kontrolü
                val prepareIntent = VpnService.prepare(context)
                if (prepareIntent != null) {
                    vpnPermissionLauncher.launch(prepareIntent)
                } else {
                    NidgVpnManager.connect(context)
                }
            },
            onDisconnect = { NidgVpnManager.disconnect(context) }
        )

        // WiFi uyarısı
        if (nidgVpnState == NidgVpnManager.NidgVpnState.WIFI_BLOCKED) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFC62828).copy(alpha = 0.15f))) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WifiOff, null, tint = Color(0xFFC62828), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("WiFi aktif — NIDG sadece mobil veride çalışır", style = MaterialTheme.typography.bodySmall, color = Color(0xFFC62828), fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Ana VPN çakışma uyarısı
        if (nidgVpnState == NidgVpnManager.NidgVpnState.MAIN_VPN_ACTIVE) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE65100).copy(alpha = 0.15f))) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VpnLock, null, tint = Color(0xFFE65100), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Ana VPN aktif — önce ana VPN'i kapatın", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE65100), fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ── Durum + Analiz Modu ──
        StatusCard(isActive = report.isActive, uptimeMs = report.analysisUptimeMs, mode = report.analysisMode)

        // ── Ana Metrikler (3 kart) ──
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard(Modifier.weight(1f), "Toplam", "%.1f".format(report.totalMB), "MB", Icons.Default.CloudDownload, MaterialTheme.colorScheme.primary)
            MetricCard(Modifier.weight(1f), "Net Kullanım", "%.1f".format(report.netUsefulMB), "MB", Icons.Default.CheckCircle, Color(0xFF2E7D32))
            MetricCard(Modifier.weight(1f), "Overhead", "%.1f".format(report.overheadMB), "MB", Icons.Default.Warning, Color(0xFFE65100))
        }

        // ── NER + Confidence Score ──
        NerGaugeCard(nerPercent = report.nerPercent, confidenceScore = report.confidenceScore, confidenceExplanation = report.confidenceExplanation)

        // ── Sinyal Telemetri ──
        if (report.signalRsrp > -999) {
            SignalCard(report)
        }

        // ═══════════════════════════════════════════
        // 2. KALİBRASYON KARTI
        // ═══════════════════════════════════════════
        CalibrationCard(report = report)

        // ── TCP Retransmission ──
        StatCard("TCP Retransmission", listOf(
            "Toplam TCP Paket" to formatNumber(report.totalTcpPackets),
            "Tekrarlanan Paket" to formatNumber(report.retransmittedPackets),
            "Tekrarlanan Veri" to formatBytes(report.retransmittedBytes),
            "Aktif Akış" to report.activeFlows.toString()
        ), Icons.Default.Sync)

        // ── QUIC / UDP ──
        StatCard("UDP / QUIC Analizi", listOf(
            "UDP Paket" to formatNumber(report.totalUdpPackets),
            "QUIC Paket" to formatNumber(report.quicPackets),
            "Jitter" to "%.1f ms".format(report.quicJitterMs),
            "Ort. IAT" to "%.1f ms".format(report.quicAvgIatMs),
            "Burst" to formatNumber(report.quicBurstCount),
            "Gap (Drop)" to formatNumber(report.quicGapCount),
            "Jitter Index" to "%.2f".format(report.quicJitterIndex)
        ), Icons.Default.Speed)

        // ── Traceroute ──
        if (report.tracerouteAvailable) {
            StatCard("Yol Analizi (Traceroute)", listOf(
                "Tünel RTT" to "${report.tunnelRttMs} ms",
                "Bypass RTT" to "${report.bypassRttMs} ms",
                "Operatör Segment" to "${report.operatorSegmentMs} ms",
                "Tünel Hop" to "${report.tunnelHops}",
                "Bypass Hop" to "${report.bypassHops}"
            ), Icons.Default.Route)
        }

        // ── CDN Filtreleme ──
        StatCard("CDN / ABR Filtreleme", listOf(
            "CDN Paket" to formatNumber(report.cdnPackets),
            "CDN-Dışı Paket" to formatNumber(report.nonCdnPackets),
            "CIDR Durumu" to if (report.isCidrStale) "⚠️ Bayat" else "✅ Güncel"
        ), Icons.Default.FilterList)

        // ── Confidence Score Detayları ──
        if (report.confidenceFlags.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VerifiedUser, null, Modifier.size(20.dp), MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Güven Skoru Kısıtlamaları", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    report.confidenceFlags.forEach { flag ->
                        Text("• $flag", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
                }
            }
        }

        // ── Analiz Kalitesi ──
        StatCard("Analiz Kalitesi", listOf(
            "Düşürülen Paket" to formatNumber(report.droppedAnalysisPackets),
            "Analiz Süresi" to formatDuration(report.analysisUptimeMs),
            "Analiz Modu" to report.analysisMode,
            "Dondurulan Log" to "${report.frozenLogCount}"
        ), Icons.Default.Analytics)

        // ── Disclaimer ──
        Text(
            "* Sadece mobil veri trafiği analiz edilir, WiFi hariç tutulur.\n* Ölçüm cihaz üzerinde yapılmaktadır. Operatör faturasıyla küçük farklılıklar olabilir.\n* QUIC metrikleri istatistiksel çıkarıma dayanır.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )
    }
}

// ═══════════════════════════════════════════
// NIDG VPN Bağlantı Kartı
// ═══════════════════════════════════════════

@Composable
private fun NidgVpnCard(
    vpnState: NidgVpnManager.NidgVpnState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val isActive = vpnState == NidgVpnManager.NidgVpnState.ACTIVE
    val isConnecting = vpnState == NidgVpnManager.NidgVpnState.CONNECTING
    val bgColor = when (vpnState) {
        NidgVpnManager.NidgVpnState.ACTIVE -> Color(0xFF1B5E20)
        NidgVpnManager.NidgVpnState.CONNECTING -> Color(0xFFE65100)
        NidgVpnManager.NidgVpnState.WIFI_BLOCKED -> Color(0xFFC62828)
        NidgVpnManager.NidgVpnState.MAIN_VPN_ACTIVE -> Color(0xFFE65100)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isActive) Icons.Default.Sensors else Icons.Default.SensorsOff,
                    null, tint = bgColor, modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ağ Analiz VPN", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        when (vpnState) {
                            NidgVpnManager.NidgVpnState.ACTIVE -> "Analiz aktif — Mobil veri izleniyor"
                            NidgVpnManager.NidgVpnState.CONNECTING -> "Bağlanıyor..."
                            NidgVpnManager.NidgVpnState.WIFI_BLOCKED -> "WiFi'da kullanılamaz"
                            NidgVpnManager.NidgVpnState.MAIN_VPN_ACTIVE -> "Ana VPN aktif"
                            else -> "Başlatmak için dokunun"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { if (isActive) onDisconnect() else onConnect() },
                enabled = !isConnecting,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isActive) Color(0xFFC62828) else bgColor,
                    contentColor = Color.White
                )
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Bağlanıyor...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(
                        if (isActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                        null, modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isActive) "ANALİZİ DURDUR" else "ANALİZİ BAŞLAT",
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════
// Kalibrasyon Kartı
// ═══════════════════════════════════════════

@Composable
private fun CalibrationCard(report: NidgReport) {
    var operatorMbInput by remember { mutableStateOf("") }
    var calibrationResult by remember { mutableStateOf<String?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, null, Modifier.size(20.dp), MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Kalibrasyon", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(12.dp))

            // Mevcut durum
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Durum", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Text(
                    if (report.isCalibrated) "✅ Kalibrelendi" else "⚠️ Kalibre edilmedi",
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold
                )
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("K Katsayısı", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Text("%.4f".format(report.kFactor), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
            if (report.hasKAnomaly) {
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("K Anomali", style = MaterialTheme.typography.bodySmall, color = Color(0xFFC62828))
                    Text("⚠️ Tespit edildi", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFFC62828))
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Truva VPN Ölçümü", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Text("%.2f MB".format(report.totalMB), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(Modifier.height(12.dp))

            // Kalibrasyon giriş alanı
            Text("Operatör Uygulamasından Tüketim (MB)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = operatorMbInput,
                    onValueChange = { operatorMbInput = it.filter { c -> c.isDigit() || c == '.' } },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Örn: 150.5") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    suffix = { Text("MB") }
                )

                Button(
                    onClick = {
                        val operatorMb = operatorMbInput.toDoubleOrNull()
                        if (operatorMb != null && report.totalBytes > 0) {
                            val operatorBytes = (operatorMb * 1_048_576).toLong()
                            val result = NidgEngine.calibration.calibrate(report.totalBytes, operatorBytes)
                            calibrationResult = "K = ${"%.4f".format(result.kFactor)}"
                        } else {
                            calibrationResult = "Geçersiz değer veya veri yok"
                        }
                    },
                    enabled = operatorMbInput.isNotBlank() && report.totalBytes > 0,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("KALIBRE ET", fontWeight = FontWeight.Bold)
                }
            }

            if (calibrationResult != null) {
                Spacer(Modifier.height(8.dp))
                Text(calibrationResult!!, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ═══════════════════════════════════════════
// Mevcut Alt Bileşenler
// ═══════════════════════════════════════════

@Composable
private fun StatusCard(isActive: Boolean, uptimeMs: Long, mode: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (isActive) Color(0xFF1B5E20).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isActive) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                null, tint = if (isActive) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline, modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(if (isActive) "Analiz Aktif" else "Analiz Pasif", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (isActive) "Mod: $mode | Süre: ${formatDuration(uptimeMs)}" else "Analiz VPN'i başlatın",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun MetricCard(modifier: Modifier, title: String, value: String, unit: String, icon: ImageVector, color: Color) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))) {
        Column(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = color)
            Text(unit, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.7f))
            Spacer(Modifier.height(2.dp))
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun NerGaugeCard(nerPercent: Double, confidenceScore: Double, confidenceExplanation: String) {
    val gaugeColor = when {
        nerPercent >= 95 -> Color(0xFF2E7D32); nerPercent >= 85 -> Color(0xFF558B2F)
        nerPercent >= 70 -> Color(0xFFF9A825); nerPercent >= 50 -> Color(0xFFEF6C00); else -> Color(0xFFC62828)
    }
    val label = when {
        nerPercent >= 95 -> "Mükemmel"; nerPercent >= 85 -> "İyi"; nerPercent >= 70 -> "Orta"; nerPercent >= 50 -> "Düşük"; else -> "Kritik"
    }

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = gaugeColor.copy(alpha = 0.12f))) {
        Column(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Bağlantı Verimliliği", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("%${String.format("%.1f", nerPercent)}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = gaugeColor)
            Text(label, style = MaterialTheme.typography.labelMedium, color = gaugeColor.copy(alpha = 0.8f), fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (nerPercent / 100.0).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp), color = gaugeColor, trackColor = gaugeColor.copy(alpha = 0.2f)
            )
            if (confidenceScore > 0) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Güven Skoru", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Text("%.0f/100".format(confidenceScore), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                if (confidenceExplanation.isNotEmpty()) {
                    Text(confidenceExplanation, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun SignalCard(report: NidgReport) {
    val signalColor = when (report.signalQuality) {
        "Mükemmel" -> Color(0xFF2E7D32); "İyi" -> Color(0xFF558B2F); "Orta" -> Color(0xFFF9A825); "Zayıf" -> Color(0xFFEF6C00); else -> Color(0xFFC62828)
    }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = signalColor.copy(alpha = 0.1f))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SignalCellularAlt, null, Modifier.size(20.dp), signalColor)
                Spacer(Modifier.width(8.dp))
                Text("Sinyal Kalitesi", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(report.signalQuality, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = signalColor)
            }
            Spacer(Modifier.height(12.dp))
            listOf(
                "RSRP" to "${report.signalRsrp} dBm", "RSRQ" to "${report.signalRsrq} dB",
                "Ağ Tipi" to report.signalNetworkType,
                "Sinyal Stabilitesi" to if (report.signalIsStable) "✅ Stabil" else "⚠️ Dalgalı",
                "Örnek Sayısı" to "${report.signalSampleCount}"
            ).forEach { (label, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun StatCard(title: String, items: List<Pair<String, String>>, icon: ImageVector) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(20.dp), MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            items.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════
// Yardımcılar
// ═══════════════════════════════════════════

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.2f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatNumber(n: Long): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fK".format(n / 1_000.0)
    else -> n.toString()
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000; val minutes = seconds / 60; val hours = minutes / 60
    return when {
        hours > 0 -> "%d sa %02d dk".format(hours, minutes % 60)
        minutes > 0 -> "%d dk %02d sn".format(minutes, seconds % 60)
        else -> "%d sn".format(seconds)
    }
}
