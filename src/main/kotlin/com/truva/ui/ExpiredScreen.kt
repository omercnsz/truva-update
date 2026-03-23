package com.truva.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.truva.TruvaViewModel

/**
 * Karartma Ekranı — Oturum süresi dolduğunda gösterilir.
 *
 * İş profilindeyken: "Ana Truva'yı Aç" butonu
 * Ana profildeyken: "Kazık Savar'ı Aç" butonu
 */
@Composable
fun ExpiredScreen(
    onNavigateToGateway: () -> Unit,
    isInWorkProfile: Boolean = false
) {

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Kilit ikonu — Modern Gradyan Benzeri Renk
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(100.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Başlık
            Text(
                "Erişim Durduruldu",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Açıklama
            Text(
                if (isInWorkProfile) {
                    "3 saatlik oturum süreniz doldu.\nAna Truva'dan süreyi yenileyebilirsiniz."
                } else {
                    "3 saatlik oturum süreniz doldu.\nTüm VPN ve güvenlik ayarları\ngeçici olarak uyku moduna alındı."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Yönlendirme butonu
            Button(
                onClick = onNavigateToGateway,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = MaterialTheme.shapes.extraLarge,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    if (isInWorkProfile) "Ana Truva'yı Aç" else "Süreyi Yenile — Kazık Savar'ı Aç",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Alt bilgi — Glass effect card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "💡 Nasıl Yenilenir?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (isInWorkProfile) {
                            "1. Ana profildeki Truva'yı açın\n" +
                            "2. Kazık Savar uygulamasından reklam izleyin\n" +
                            "3. Oturum otomatik olarak buraya da yansır"
                        } else {
                            "1. Kazık Savar uygulamasını açın\n" +
                            "2. Ödüllü reklam izleyin\n" +
                            "3. 3 saatlik tam erişim anında aktif olur"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
