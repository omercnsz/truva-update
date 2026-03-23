package com.truva.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.truva.TruvaViewModel
import java.util.Locale
import java.util.TimeZone

/**
 * Cihaz Seviyesinde Doğrulama Modülü
 *
 * Truva'nın sahte değerlerini değil, Android sisteminin o anki GERÇEK (veya manipüle edilmiş) aktif
 * değerlerini cihazdan okur.
 */
@Composable
fun ManipulationIntegrityScreen(viewModel: TruvaViewModel) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                        text = "Cihaz Durumu Doğrulama",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                )
                Text(
                        text = "Sistemin algıladığı aktif değerler aşağıdadır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(bottom = 16.dp)
                )

                // 1. Ağ ve IP (ip-api.com üzerinden gerçek dış IP testi)
                IpStatusSection(viewModel)

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Sistemden Okunan Saat ve Bölge (Anlık)
                val currentTimeZone = TimeZone.getDefault().id
                val currentLocale = Locale.getDefault().toLanguageTag()

                IntegrityCard(
                        title = "Aktif Sistem Ayarları",
                        icon = Icons.Default.Language,
                        isActive = true,
                        data =
                                mapOf(
                                        "Sistem Saat Dilimi" to currentTimeZone,
                                        "Sistem Dil/Bölge" to currentLocale,
                                        "UTC Ofseti" to
                                                "${TimeZone.getDefault().rawOffset / 3600000} saat"
                                )
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 3. Sistemden Okunan SIM/Şebeke (TelephonyManager'dan canlı)
                IntegrityCard(
                        title = "Aktif Şebeke Durumu",
                        icon = Icons.Default.SimCard,
                        isActive = true,
                        data =
                                mapOf(
                                        "Algılanan Operatör" to
                                                viewModel.simSpoofManager.getRealOperatorName(),
                                        "Ülke Kodu (ISO)" to
                                                viewModel
                                                        .simSpoofManager
                                                        .getRealCountryIso()
                                                        .uppercase(),
                                        "Şebeke Tipi" to "LTE / 4G"
                                )
                )

                // 4. Cihaz Kimlikleri (Settings.Secure'dan canlı)
                IntegrityCard(
                        title = "Aktif Cihaz Kimliği",
                        icon = Icons.Default.PhonelinkSetup,
                        isActive = true,
                        data =
                                mapOf(
                                        "Aktif Android ID" to
                                                viewModel.systemSpoofManager.getRealAndroidId()
                                )
                )

                // 5. Güvenlik ve İzin Katmanı
                SecurityIntegritySection(viewModel)
        }
}
