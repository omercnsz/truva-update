package com.truva.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AgreementScreen(
    onAccept: () -> kotlin.Unit,
    onReject: () -> kotlin.Unit
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = kotlin.collections.listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "TRUVA VPN & KAZIK SAVAR",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "KULLANICI SÖZLEŞMESİ VE GİZLİLİK",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "Son Güncelleme: 03.04.2026 | Sürüm: 1.0.0",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Agreement Text
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(16.dp)
                ) {
                    AgreementContent()
                }
            }

            // Footer Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Text("Reddet", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1.5f).height(56.dp),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Kabul Ediyorum", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
fun AgreementContent() {
    val sections = arrayOf(
        "1. TARAFLAR VE SÖZLEŞMENİN KABULÜ" to "İşbu sözleşme, Truva VPN (Masaüstü/Mobil) ve Kazık Savar yazılımlarını (bundan sonra \"Uygulama\" veya \"Yazılım\" olarak anılacaktır) kullanan gerçek kişiler (\"Kullanıcı\") ile Uygulama geliştiricisi (\"Geliştirici\") arasında akdedilmiştir. Kullanıcı, Yazılımı cihaza indirdiği, kurduğu veya \"Kabul Ediyorum\" butonuna bastığı anda bu sözleşmeyi tüm maddeleriyle, hiçbir istisna olmaksızın kabul etmiş sayılır.",
        
        "2. HİZMETİN NİTELİĞİ (ÜÇÜNCÜ TARAF SUNUCU RİSKİ)" to "Sunucu Kaynağı: Truva VPN, kendi VPN sunucu altyapısını işletmez. Uygulama, internet üzerindeki açık kaynaklardan taranarak (scraped) elde edilen, üçüncü taraflara ait ücretsiz sunuculara bağlantı sağlayan teknik bir arayüzdür.\n\nGüvenlik ve Log Politikası: Bağlanılan sunucular Geliştiricinin denetiminde değildir. Bu sunucuların sahiplerinin veri kayıt (log) tutma politikaları bilinmemektedir.\n\nKritik Uyarı: Kullanıcının bu sunucular üzerinden bankacılık işlemleri yapması, kredi kartı bilgisi girmesi veya hassas kişisel verilerini paylaşması kesinlikle önerilmez. Oluşabilecek veri sızıntılarından veya kimlik hırsızlıklarından münhasıran Kullanıcı sorumludur.",
        
        "3. GİZLİLİK VE VERİ İŞLEME (KVKK/GDPR UYUMU)" to "Kişisel Veriler: Uygulama, kullanıcıların internet trafik verilerini veya gerçek IP adreslerini kendi sunucularında kayıt altına almaz.\n\nÜçüncü Taraf Servisler: Hizmetin sürekliliği, anahtar (key) doğrulaması ve reklam gösterimi için Google Firebase ve AdMob servisleri kullanılmaktadır. Kullanıcı; anonim cihaz kimliği, işletim sistemi sürümü ve uygulama kullanım istatistiklerinin bu platformlar aracılığıyla işlenmesini kabul eder.",
        
        "4. RESMİ OLMAYAN DAĞITIM KANALLARI" to "Uygulamanın resmi web sitesi (truvavpn.com) veya resmi GitHub sayfası dışındaki kaynaklardan (3. taraf APK siteleri, dosya paylaşım platformları vb.) temin edilmesi durumunda oluşabilecek;\n- Modifiye edilmiş kodlardan kaynaklı güvenlik açıkları,\n- Zararlı yazılım (malware/virus) bulaşması,\ndurumlarında Geliştirici hiçbir hukuki veya teknik sorumluluk kabul etmez.",
        
        "5. KULLANIM KISITLAMALARI VE HUKUKİ SORUMLULUK" to "Kullanıcı, Uygulamayı kullanarak yerel (T.C. kanunları dahil) ve uluslararası yasaları ihlal etmeyeceğini taahhüt eder. Aşağıdaki eylemlerin gerçekleştirilmesi durumunda tüm hukuki ve cezai sorumluluk Kullanıcıya aittir:\n- Siber saldırılar (DDoS, hacking), ağ tarama faaliyetleri ve spam e-posta gönderimi.\n- Telif haklarına aykırı içeriklerin yasa dışı dağıtımı (Torrent vb.).\n- Çocuk istismarı, terör propagandası veya yasal olmayan diğer içeriklere erişim.\n\nGeliştirici, Uygulamanın kullanım amacının \"yasaklı sitelere erişim\" olmadığını, sadece \"anonim ve güvenli internet deneyimi\" olduğunu beyan eder.",
        
        "6. REKLAM VE ANAHTAR (KEY) MODELİ" to "Uygulama erişimi, Kazık Savar uygulaması üzerinden reklam izleme karşılığı üretilen 3 saatlik geçici anahtarlar ile sağlanır.\n\nReklam içerikleri Google AdMob tarafından sağlanır; Geliştirici reklam içeriğinden sorumlu tutulamaz.\n\nAnahtar sisteminin teknik arızalar nedeniyle çalışmaması durumunda Geliştiricinin bir tazminat yükümlülüğü yoktur.",
        
        "7. GARANTİ REDDİ VE SORUMLULUK SINIRLANDIRMASI" to "Hizmet Kalitesi: Yazılım \"Olduğu Gibi\" (As-Is) sunulmaktadır. Geliştirici; sunucu hızı, ping değerleri veya bağlantı sürekliliği konusunda garanti vermez.\n\nZarar Tazmini: Kullanım sonucu oluşabilecek donanımsal arızalar, veri kayıpları veya İnternet Servis Sağlayıcı (ISS) tarafından uygulanabilecek yaptırımlardan Geliştirici sorumlu değildir.",
        
        "8. FİKRİ MÜLKİYET VE TERSİNE MÜHENDİSLİK" to "Yazılımın kaynak kodları, görsel tasarımı ve markası Geliştiriciye aittir. Yazılımın tersine mühendislik (reverse engineering) yoluyla çözülmesi, kırılarak (crack) anahtar sisteminin bypass edilmesi veya izinsiz yeniden dağıtılması yasaktır.",
        
        "9. YETKİLİ MAHKEME VE YÜRÜRLÜK" to "İşbu sözleşmeden doğacak tüm ihtilaflarda İstanbul (Çağlayan) Mahkemeleri ve İcra Daireleri yetkilidir. Sözleşme, Kullanıcı Yazılımı cihazından kaldırana kadar geçerliliğini korur."
    )

    for (pair in sections) {
        val title: String = pair.first
        val body: String = pair.second
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp, top = 12.dp)
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Justify,
            lineHeight = 24.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}
