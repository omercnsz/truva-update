package com.truva.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.truva.TruvaViewModel
import com.truva.VpnState

@Composable
fun GamingModeScreen(viewModel: TruvaViewModel, onConnect: () -> Unit) {
    val connectionState by viewModel.connectionState.collectAsState(initial = VpnState.IDLE)
    val isGaming = connectionState == VpnState.GAMING
    val isConnecting = connectionState == VpnState.CONNECTING
    val isDisconnecting = connectionState == VpnState.DISCONNECTING

    // Nitro Renk Paleti (Neon Cyan & Deep Indigo)
    val nitroCyan = Color(0xFF00E5FF)
    val nitroDeep = Color(0xFF1A237E)
    val nitroBackground = Color(0xFF0B0D17) // Daha koyu, uzay/nitro teması

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(nitroBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Üst Başlık
        Text(
            text = "NİTRO GEÇİT",
            style = MaterialTheme.typography.headlineLarge,
            color = nitroCyan,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
        Text(
            text = "U L T R A  S P E E D",
            style = MaterialTheme.typography.labelLarge,
            color = nitroCyan.copy(alpha = 0.6f),
            letterSpacing = 4.sp
        )

        Spacer(modifier = Modifier.weight(1f))

        // Merkezi Bağlantı Butonu (Pulsing Effect)
        GamingConnectButton(
            isActive = isGaming || isConnecting,
            buttonText = when {
                isGaming -> "AKTİF"
                isConnecting -> "AKTİVE EDİLİYOR"
                isDisconnecting -> "KAPATILIYOR"
                else -> "GÜCÜ AÇ"
            },
            onClick = {
                when {
                    isGaming -> viewModel.disconnectGameMode()
                    connectionState == VpnState.IDLE -> {
                        viewModel.forceConnectingState()
                        onConnect()
                    }
                }
            },
            primaryColor = nitroCyan,
            accentColor = nitroDeep
        )

        Spacer(modifier = Modifier.weight(1f))

        // Bilgi Kartları
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            InfoStatCard(
                modifier = Modifier.weight(1f),
                title = "Gecikme",
                value = if (isGaming) "24 ms" else "--",
                icon = Icons.Default.Speed,
                color = nitroCyan
            )
            InfoStatCard(
                modifier = Modifier.weight(1f),
                title = "Durum",
                value = when {
                    isGaming -> "STABİL"
                    isConnecting -> "TUNNEL"
                    isDisconnecting -> "IDLE"
                    else -> "HAZIR"
                },
                icon = Icons.Default.Bolt,
                color = if (isGaming || isConnecting) nitroCyan else Color.Gray
            )
        }


    }
}

@Composable
fun GamingConnectButton(
    isActive: Boolean,
    buttonText: String,
    onClick: () -> Unit,
    primaryColor: Color,
    accentColor: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isActive) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = if (isActive) 0.6f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(240.dp)
    ) {
        // Dış Halo (Neon Efekti)
        Box(
            modifier = Modifier
                .size(200.dp * scale)
                .drawBehind {
                    drawCircle(
                        color = primaryColor.copy(alpha = alpha),
                        radius = size.minDimension / 2f
                    )
                }
        )

        // Ana Buton
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(primaryColor, accentColor)
                    )
                )
                .border(2.dp, primaryColor.copy(alpha = 0.5f), CircleShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = buttonText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun InfoStatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF161B22)
        ),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                title, 
                style = MaterialTheme.typography.labelSmall, 
                color = Color.Gray
            )
            Text(
                value, 
                style = MaterialTheme.typography.titleMedium, 
                color = Color.White, 
                fontWeight = FontWeight.Bold
            )
        }
    }
}
