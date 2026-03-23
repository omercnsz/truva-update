package com.truva.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.truva.SettingsEntity
import com.truva.TruvaViewModel

/**
 * Akıllı Yönlendirme (Smart Routing) Ayarları Ekranı
 *
 * Routing modları:
 * - Standart: Tüm trafik proxy üzerinden
 * - Oyun: UDP direct + engelli servisler proxy (min. ping)
 * - Video Akış: Streaming proxy + diğer direct
 * - Anti-Sansür: Sadece engelli domainler proxy
 */
@Composable
fun SmartRoutingSection(
    viewModel: TruvaViewModel,
    settings: SettingsEntity
) {
    Column(modifier = Modifier.fillMaxWidth()) {

        Text(
            "Akıllı Yönlendirme (Smart Routing)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Açıklama
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Text(
                "Trafik türüne göre en düşük gecikme ve en yüksek gizlilikle " +
                        "optimal yönlendirme kararı verir.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(12.dp)
            )
        }

        // ── Routing Modları ──
        RoutingModeCard(
            icon = Icons.Default.Shield,
            title = "Standart Mod",
            description = "Tüm trafik proxy üzerinden — maksimum gizlilik",
            isSelected = settings.routingMode == "standard",
            onClick = { viewModel.setRoutingMode("standard") }
        )

        RoutingModeCard(
            icon = Icons.Default.SportsEsports,
            title = "Oyun Modu",
            description = "UDP trafiği direkt, engelli servisler proxy — min. ping",
            isSelected = settings.routingMode == "gaming",
            onClick = { viewModel.setRoutingMode("gaming") }
        )

        RoutingModeCard(
            icon = Icons.Default.OndemandVideo,
            title = "Video Akış Modu",
            description = "Netflix, YouTube, TikTok proxy — diğer direkt",
            isSelected = settings.routingMode == "streaming",
            onClick = { viewModel.setRoutingMode("streaming") }
        )

        RoutingModeCard(
            icon = Icons.Default.VpnLock,
            title = "Anti-Sansür Modu",
            description = "Sadece engelli domainler proxy — bant tasarrufu",
            isSelected = settings.routingMode == "anti_censorship",
            onClick = { viewModel.setRoutingMode("anti_censorship") }
        )

        // ── Seçili mod bilgilendirmesi ──
        val modeInfo = when (settings.routingMode) {
            "gaming" -> Triple(
                "⚡ Oyun Modu Aktif",
                "Oyun UDP paketleri VPN'i bypass eder → 0 ping artışı. " +
                "Roblox, Discord, TikTok gibi engelli servisler şifreli tünelden geçer. " +
                "DNS şifrelidir, sızıntı olmaz.",
                MaterialTheme.colorScheme.secondaryContainer
            )
            "streaming" -> Triple(
                "🎬 Video Akış Modu Aktif",
                "Netflix, YouTube, TikTok, Twitch, Spotify gibi platformlar proxy üzerinden geçer → " +
                "bölge kilidi aşılır. Diğer trafik de proxy üzerinden gider.",
                MaterialTheme.colorScheme.tertiaryContainer
            )
            "anti_censorship" -> Triple(
                "🔓 Anti-Sansür Modu Aktif",
                "Sadece engelli siteler (Roblox, Discord, Twitter, Reddit vb.) ve Google servisleri " +
                "proxy üzerinden geçer. Diğer trafik doğrudan çıkar → daha hızlı, daha az veri tüketimi.",
                MaterialTheme.colorScheme.primaryContainer
            )
            else -> Triple(
                "🛡️ Standart Mod Aktif",
                "Tüm internet trafiğiniz şifreli tünelden geçer — maksimum gizlilik. " +
                "En güvenli mod, ancak tüm trafik proxy üzerinden gittiği için biraz daha yavaş olabilir.",
                MaterialTheme.colorScheme.surfaceVariant
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = modeInfo.third.copy(alpha = 0.5f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, modeInfo.third.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    modeInfo.first,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    modeInfo.second,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
                if (settings.routingMode != "standard") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "ⓘ Bu mod VPN bağlandığında otomatik uygulanır. Değişiklik için önce VPN'i kesin.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // ── Gelişmiş Seçenekler ──
        Text(
            "Gelişmiş",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Smart Routing", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Otomatik trafik sınıflandırma",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Switch(
                checked = settings.isSmartRoutingEnabled,
                onCheckedChange = { viewModel.toggleSmartRouting(it) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("UDP Direct Bypass", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Oyun UDP paketleri VPN'i bypass eder (0 ping artışı)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Switch(
                checked = settings.isUdpDirectBypass,
                onCheckedChange = {
                    viewModel.toggleUdpDirectBypass(it)
                }
            )
        }
    }
}

@Composable
fun RoutingModeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        border = if (isSelected) null
        else androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            RadioButton(selected = isSelected, onClick = onClick)
        }
    }
}
