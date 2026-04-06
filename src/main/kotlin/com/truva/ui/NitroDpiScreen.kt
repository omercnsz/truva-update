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
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.truva.TruvaViewModel
import com.truva.VpnState

@Composable
fun NitroDpiScreen(
    viewModel: TruvaViewModel,
    onConnect: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    
    val isDpiActive = connectionState == VpnState.NITRO_DPI
    val isConnecting = connectionState == VpnState.CONNECTING
    val isDisconnecting = connectionState == VpnState.DISCONNECTING

    // Nitro Oyun Palette (Cyberpunk Yellow/Red)
    val nitroNeon = Color(0xFFFFEA00)
    val nitroDeep = Color(0xFFD50000)
    val nitroBackground = Color(0xFF0B0D17) 

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(nitroBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "NİTRO OYUN",
            style = MaterialTheme.typography.headlineLarge,
            color = nitroNeon,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
        Text(
            text = "L O C A L   D P I   B Y P A S S",
            style = MaterialTheme.typography.labelLarge,
            color = nitroNeon.copy(alpha = 0.6f),
            letterSpacing = 4.sp
        )

        Spacer(modifier = Modifier.weight(0.5f))

        // Ayar Kartı
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
            border = BorderStroke(1.dp, nitroNeon.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Yönlendirme Modu", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text("Trafiğin hangi uygulamalarda modifiye edileceğini seçin.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.nitroDpiAppMode == "all",
                        onClick = { viewModel.setNitroDpiAppMode("all") },
                        colors = RadioButtonDefaults.colors(selectedColor = nitroNeon)
                    )
                    Text("Tüm Trafik (Global Bypass)", color = Color.White)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.nitroDpiAppMode == "selected",
                        onClick = { viewModel.setNitroDpiAppMode("selected") },
                        colors = RadioButtonDefaults.colors(selectedColor = nitroNeon)
                    )
                    Text("Sadece Seçili Uygulamalar", color = Color.White)
                }
                
                if (settings.nitroDpiAppMode == "selected") {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = settings.nitroDpiApps,
                        onValueChange = { viewModel.setNitroDpiApps(it) },
                        label = { Text("Uygulama Paket Adları (virgülle ayırın)", color = Color.Gray) },
                        placeholder = { Text("örn: com.tencent.ig, com.activision.callofduty.shooter", color = Color.DarkGray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = nitroNeon,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Ana Buton
        DpiConnectButton(
            isActive = isDpiActive || isConnecting,
            buttonText = when {
                isDpiActive -> "0 PİNG AKTİF"
                isConnecting -> "AKTİVE EDİLİYOR"
                isDisconnecting -> "KAPATILIYOR"
                else -> "GÜCÜ AÇ"
            },
            onClick = {
                when {
                    isDpiActive -> viewModel.disconnectNitroDpi()
                    connectionState == VpnState.IDLE -> {
                        viewModel.forceConnectingState()
                        onConnect()
                    }
                }
            },
            primaryColor = nitroNeon,
            accentColor = nitroDeep
        )

        Spacer(modifier = Modifier.weight(1f))

        // Alt Bilgiler
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            InfoStatCard(
                modifier = Modifier.weight(1f),
                title = "Engine",
                value = "DPI Frag",
                icon = Icons.Default.Info,
                color = nitroNeon
            )
            InfoStatCard(
                modifier = Modifier.weight(1f),
                title = "Durum",
                value = when {
                    isDpiActive -> "BYPASS"
                    isConnecting -> "TUNNEL"
                    isDisconnecting -> "IDLE"
                    else -> "HAZIR"
                },
                icon = Icons.Default.Bolt,
                color = if (isDpiActive || isConnecting) nitroNeon else Color.Gray
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun DpiConnectButton(
    isActive: Boolean,
    buttonText: String,
    onClick: () -> Unit,
    primaryColor: Color,
    accentColor: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(190.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(primaryColor.copy(alpha = 0.15f))
            )
            Box(
                modifier = Modifier
                    .size(175.dp)
                    .scale(scale * 0.98f)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.2f))
            )
        }

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
                    tint = Color.Black
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = buttonText,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}


