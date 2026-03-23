package com.truva

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.lifecycleScope
import com.truva.R
import com.truva.ui.IpStatusSection
import com.truva.ui.ManipulationIntegrityScreen
import com.truva.ui.RegionProfileSection
import com.truva.ui.SandboxSection
import com.truva.ui.SmartRoutingSection
import com.truva.ui.ExpiredScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Truva VPN — Ana Ekran
 *
 * Basit ve odaklı UI:
 * 1. Bağlantı durumu kartı
 * 2. Bağlan / Kes butonları
 * 3. Sunucu listesi (seçim)
 * 4. Deep link ile sunucu ekleme (truvavpn://import?config=...)
 *
 * İleri özellikler (Spoofing, Sandbox, Smart Routing) ayrı panellerde, ama temel VPN bağlantısını
 * ETKİLEMEZLER.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: TruvaViewModel by viewModels {
        val db = AppDatabase.getDatabase(this)
        TruvaViewModelFactory(db.appDao(), db.simProtectionDao(), application)
    }

    private lateinit var vpnLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
    private lateinit var provisionLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
    private lateinit var notificationPermissionLauncher:
            androidx.activity.result.ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bildirim izni sonucu
        notificationPermissionLauncher =
                registerForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts
                                .RequestPermission()
                ) { isGranted ->
                    if (!isGranted) {
                        Log.w(
                                "TruvaMain",
                                "Bildirim izni reddedildi. ADB sihirli eşleştirme çalışmayabilir."
                        )
                    }
                }

        // VPN izni sonucu
        vpnLauncher =
                registerForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts
                                .StartActivityForResult()
                ) { result ->
                    if (result.resultCode == RESULT_OK) {
                        startService(
                                Intent(this, MyVpnService::class.java).apply {
                                    action = MyVpnService.ACTION_CONNECT
                                }
                        )
                    }
                }

        // İş Profili (Work Profile) oluşturma sonucu
        provisionLauncher =
                registerForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts
                                .StartActivityForResult()
                ) { result ->
                    val success = result.resultCode == RESULT_OK
                    Log.i(
                            "TruvaMain",
                            "Work Profile provisioning result: success=$success code=${result.resultCode}"
                    )
                    viewModel.onProvisioningResult(success)
                }

        setContent {
            TruvaTheme {

                // ViewModel'den gelen provisioning intent'ini yakala ve Activity'den başlat
                val provisioningIntent by viewModel.provisioningIntent.collectAsState()
                LaunchedEffect(provisioningIntent) {
                    provisioningIntent?.let { intent ->
                        viewModel.consumeProvisioningIntent()
                        try {
                            provisionLauncher.launch(intent)
                        } catch (e: Exception) {
                            Log.e("TruvaMain", "Provisioning intent hatası", e)
                            Toast.makeText(
                                            this@MainActivity,
                                            "İş profili oluşturulamıyor: ${e.message}",
                                            Toast.LENGTH_LONG
                                    )
                                    .show()
                        }
                    }
                }

                // İş Profili/Sandbox Durumu için Başlangıç Kontrolü
                LaunchedEffect(Unit) { viewModel.refreshSandboxStatus() }

                Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                ) {
                    // Oturum kontrolü
                    val isSessionActive by viewModel.isSessionActive.collectAsState()
                    val isInWorkProfile by viewModel.isInWorkProfile.collectAsState()

                    if (isSessionActive) {
                        TruvaDashboard(
                                viewModel = viewModel,
                                onConnect = { startVpn() },
                                onDisconnect = { stopVpn() }
                        )
                    } else {
                        ExpiredScreen(
                                isInWorkProfile = isInWorkProfile,
                                onNavigateToGateway = {
                                    if (isInWorkProfile) {
                                        // İş profili → Ana profildeki Truva'yı aç (cross-profile intent)
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("truvavpn://expired"))
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(this@MainActivity, "Ana Truva açılamadı", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        // Ana profil → Kazık Savar'ı aç
                                        try {
                                            val launchIntent = packageManager.getLaunchIntentForPackage("com.kaziksavar.app")
                                            if (launchIntent != null) {
                                                startActivity(launchIntent)
                                            } else {
                                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.kaziksavar.app")))
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(this@MainActivity, "Kazık Savar uygulaması bulunamadı", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                        )
                    }
                }
            }
        }

        // Başlangıç İzinleri (Sadece ilk girişte sorulur)
        val prefs = getSharedPreferences("truva_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("initial_permissions_asked", false)) {
            // 1. Android 13+ (API 33) için Bildirim İzni İste
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch("android.permission.POST_NOTIFICATIONS")
            }

            // 2. Pil Optimizasyonu Muafiyeti İste
            if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)) {
                BatteryOptimizationHelper.requestExemptionIfNeeded(this)
            }

            // İkisi de sorulduktan sonra bir daha sormamak için kaydet
            prefs.edit().putBoolean("initial_permissions_asked", true).apply()
        }

        // Başlangıç verileri
        lifecycleScope.launch(Dispatchers.IO) { seedInitialData() }

        // Deep link
        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    // ═══════════════════════════════════════════
    // VPN Kontrol
    // ═══════════════════════════════════════════

    private fun startVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnLauncher.launch(intent)
        } else {
            startService(
                    Intent(this, MyVpnService::class.java).apply {
                        action = MyVpnService.ACTION_CONNECT
                    }
            )
        }
    }

    private fun stopVpn() {
        startService(
                Intent(this, MyVpnService::class.java).apply {
                    action = MyVpnService.ACTION_DISCONNECT
                }
        )
    }

    // ═══════════════════════════════════════════
    // Deep Link — Flutter'dan sunucu alma
    // ═══════════════════════════════════════════

    private fun handleDeepLink(intent: Intent?) {
        val data: Uri? = intent?.data
        if (Intent.ACTION_VIEW != intent?.action || data == null) return
        if (data.scheme != "truvavpn") return

        when (data.host) {
            // Kazık Savar'dan oturum aktivasyonu: truvavpn://activate
            "activate" -> {
                // 1. Kazık Savar'ın gönderdiği onay sürüm kodunu yakala
                val targetVersion = data.getQueryParameter("target_truva_v")?.toIntOrNull() ?: 0
                
                // 2. Telefonundaki Truva'nın sürüm kodunu al
                val currentVersion = try {
                    PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0)).toInt()
                } catch (e: Exception) { 0 }

                Log.i("TruvaDeepLink", "Aktivasyon denemesi. Hedef: $targetVersion, Mevcut: $currentVersion")

                // 3. Versiyon Kontrolü
                if (targetVersion > 0 && currentVersion < targetVersion) {
                    // Eğer uygulama eskiyse aktivasyonu reddet
                    Toast.makeText(this, "❌ Truva sürümünüz eskidir. Lütfen güncelleyin!", Toast.LENGTH_LONG).show()
                } else {
                    // Sürüm güncelse oturumu aç
                    viewModel.activateSession() 
                    Toast.makeText(this, "✅ 3 saatlik erişim aktive edildi!", Toast.LENGTH_LONG).show()
                }
                return
            }
            // İş profilinden gelen süre yenileme talebi: truvavpn://expired
            "expired" -> {
                Log.i("TruvaDeepLink", "İş profilinden süre yenileme talebi — Kazık Savar'a yönlendiriliyor")
                try {
                    val launchIntent = packageManager.getLaunchIntentForPackage("com.kaziksavar.app")
                    if (launchIntent != null) {
                        startActivity(launchIntent)
                    } else {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.kaziksavar.app")))
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Kazık Savar uygulaması bulunamadı", Toast.LENGTH_SHORT).show()
                }
                return
            }
            // Sunucu ekleme: truvavpn://import?config=...
            "import" -> {
                val configUrl = data.getQueryParameter("config")
                if (configUrl.isNullOrEmpty()) return
                handleServerImport(configUrl)
            }
        }
    }

    private fun handleServerImport(configUrl: String) {
        Log.i("TruvaDeepLink", "Sunucu linki alındı: $configUrl")

        lifecycleScope.launch(Dispatchers.IO) {
            val proxy = VlessLinkParser.parse(configUrl)
            if (proxy == null) {
                launch(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Geçersiz VLESS linki!", Toast.LENGTH_LONG)
                            .show()
                }
                return@launch
            }

            val dao = AppDatabase.getDatabase(this@MainActivity).appDao()
            dao.insertProxy(proxy)

            // Eklenen sunucuyu bul ve seç
            val allProxies = dao.getAllProxies().firstOrNull() ?: emptyList()
            val inserted = allProxies.lastOrNull { it.ip == proxy.ip && it.uuid == proxy.uuid }
            if (inserted != null) {
                dao.setActiveProxy(inserted.id)
            }

            launch(Dispatchers.Main) {
                Toast.makeText(
                                this@MainActivity,
                                "Sunucu eklendi: ${proxy.name} — BAĞLAN'a basın",
                                Toast.LENGTH_SHORT
                        )
                        .show()
                // Otomatik VPN başlatma kaldırıldı:
                // Pil optimizasyonu + VPN izni sayfalarıyla çakışıyordu.
                // Kullanıcı BAĞLAN butonuna basarak bağlanmalı.
            }
        }
    }

    // ═══════════════════════════════════════════
    // Başlangıç Verileri
    // ═══════════════════════════════════════════

    private suspend fun seedInitialData() {
        val dao = AppDatabase.getDatabase(this@MainActivity).appDao()

        // Settings satırı yoksa oluştur
        val currentSettings = dao.getSettingsFlow().firstOrNull()
        if (currentSettings == null) {
            dao.updateSettings(SettingsEntity())
        }

        // Eski placeholder sunucuları temizle (your-uuid ile başlayanlar)
        val existing = dao.getAllProxies().firstOrNull() ?: emptyList()
        existing.filter { it.uuid.startsWith("your-uuid") }.forEach { dao.deleteProxy(it) }
    }
}

// ═══════════════════════════════════════════════════════════
// UI — Compose Dashboard
// ═══════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TruvaDashboard(viewModel: TruvaViewModel, onConnect: () -> Unit, onDisconnect: () -> Unit) {
    val proxies by viewModel.allProxies.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState(initial = VpnState.IDLE)
    val activeServer by viewModel.activeServerName.collectAsState(initial = null)
    val errorMsg by viewModel.errorMessage.collectAsState(initial = null)
    val truvaSettings by viewModel.settings.collectAsState()
    val spoofingStatus by viewModel.spoofingStatus.collectAsState()

    val isConnecting = connectionState == VpnState.CONNECTING
    val isConnected = connectionState == VpnState.CONNECTED
    val isDisconnecting = connectionState == VpnState.DISCONNECTING
    val isBusy = isConnecting || isDisconnecting

    // Hangi sekme seçili
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("BAĞLANTI", "UYGULAMALAR", "GÜVENLİK")

    // Güvenlik sekmesi içindeki genişletilmiş bölümler
    var expandedSection by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                // Üst Bar: Logo + Başlık
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Logo
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher),
                        contentDescription = null,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(MaterialTheme.shapes.small)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "TRUVA",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                }

                // Tab Menüsü
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            selectedContentColor = MaterialTheme.colorScheme.primary,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> {
                    // BAĞLANTI SEKİMESİ
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Sistem Geneli Bağlantı",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                "Tüm trafiği güvenli VPN tüneline alarak tam koruma sağlar.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        item {
                            StatusCard(connectionState, activeServer, isBusy, isConnected, errorMsg)
                        }
                        item {
                            // Butonlar
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = onConnect,
                                    enabled = !isBusy && !isConnected,
                                    modifier = Modifier.weight(1f).height(56.dp),
                                    shape = MaterialTheme.shapes.large,
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                                ) {
                                    if (isConnecting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                    }
                                    Text(if (isConnecting) "BAĞLANIYOR..." else "BAĞLAN", fontWeight = FontWeight.Bold)
                                }
                                if (isConnected) {
                                    Button(
                                        onClick = onDisconnect,
                                        enabled = !isBusy,
                                        modifier = Modifier.weight(1f).height(56.dp),
                                        shape = MaterialTheme.shapes.large,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    ) {
                                        Text("KES", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Text("Sunucu Seçimi", style = MaterialTheme.typography.titleMedium)
                        }
                        items(proxies, key = { it.id }) { proxy ->
                            ProxyRow(proxy, onSelect = { viewModel.selectProxy(proxy) }, onDelete = { viewModel.deleteProxy(proxy) })
                        }
                        item { IpStatusSection(viewModel = viewModel) }
                        item { Spacer(modifier = Modifier.height(32.dp)) }
                    }
                }
                1 -> {
                    // UYGULAMALAR SEKİMESİ (Sandbox / Isolation)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Uygulama İzolasyonu", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("İş profili kullanarak uygulamaları ana sistemden izole edin.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                        }
                        item {
                            SandboxSection(
                                viewModel = viewModel,
                                settings = truvaSettings,
                                onOpenIntegrityTest = { selectedTab = 2 }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(32.dp)) }
                    }
                }
                2 -> {
                    // GÜVENLİK SEKİMESİ (Spoofing, Routing, Integrity, Permissions)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                        
                        // 1. Akıllı Yönlendirme
                        item {
                            SectionHeader(
                                title = "Akıllı Yönlendirme",
                                description = "Trafik türüne göre yönlendirme modu",
                                expanded = expandedSection == 1,
                                onClick = { expandedSection = if (expandedSection == 1) -1 else 1 }
                            )
                        }
                        if (expandedSection == 1) item { SmartRoutingSection(viewModel = viewModel, settings = truvaSettings) }

                        // 2. Bölge Spoofing
                        item {
                            SectionHeader(
                                title = "Bölge Spoofing",
                                description = "Cihaz kimlik bilgilerini değiştirin",
                                expanded = expandedSection == 2,
                                onClick = { expandedSection = if (expandedSection == 2) -1 else 2 }
                            )
                        }
                        if (expandedSection == 2) item { RegionProfileSection(viewModel = viewModel, settings = truvaSettings, spoofingStatus = spoofingStatus) }

                        // 3. Bütünlük Testi
                        item {
                            SectionHeader(
                                title = "Sistem Bütünlük Testi",
                                description = "Anlık manipülasyon durumunu kontrol edin",
                                expanded = expandedSection == 4,
                                onClick = { expandedSection = if (expandedSection == 4) -1 else 4 }
                            )
                        }
                        if (expandedSection == 4) item { ManipulationIntegrityScreen(viewModel = viewModel) }

                        // 4. İzinler
                        item {
                            SectionHeader(
                                title = "İzinler ve Durum",
                                description = "Gerekli yetkileri yönetin",
                                expanded = expandedSection == 5,
                                onClick = { expandedSection = if (expandedSection == 5) -1 else 5 }
                            )
                        }
                        if (expandedSection == 5) {
                            item { Box(modifier = Modifier.fillMaxWidth().height(400.dp)) { com.truva.ui.PermissionDashboardScreen() } }
                        }
                        item { Spacer(modifier = Modifier.height(32.dp)) }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// Durum Kartı
// ═══════════════════════════════════════════════════════════

@Composable
fun StatusCard(
    state: VpnState,
    serverName: String?,
    isBusy: Boolean,
    isConnected: Boolean,
    errorMessage: String? = null
) {
    val (bgColor, contentColor, statusText) =
        when (state) {
            VpnState.IDLE -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "Bağlantı Yok")
            VpnState.CONNECTING -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, "Bağlanıyor...")
            VpnState.CONNECTED -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, "Güvenli Tünel Aktif")
            VpnState.DISCONNECTING -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, "Kesiliyor...")
            VpnState.ERROR -> Triple(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, "Bağlantı Hatası")
        }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = bgColor.copy(alpha = 0.8f)),
        border = BorderStroke(1.dp, bgColor.copy(alpha = 0.5f))
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                    color = contentColor
                )
            } else {
                Icon(
                    imageVector = if (isConnected) Icons.Default.Lock else Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = contentColor
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    statusText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                if (serverName != null && (isConnected || state == VpnState.CONNECTING)) {
                    Text(
                        serverName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                }
                if (state == VpnState.ERROR) {
                    Text(
                        errorMessage ?: "Bilinmeyen hata",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, description: String, expanded: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color =
        if (expanded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Text(
                if (expanded) "▲" else "▼",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun ProxyRow(proxy: ProxyEntity, onSelect: () -> Unit, onDelete: () -> Unit) {
    val flag =
        when (proxy.region) {
            "SE" -> "\uD83C\uDDF8\uD83C\uDDEA"
            "FI" -> "\uD83C\uDDEB\uD83C\uDDEE"
            "LT" -> "\uD83C\uDDF1\uD83C\uDDF9"
            "DE" -> "\uD83C\uDDE9\uD83C\uDDEA"
            "NL" -> "\uD83C\uDDF3\uD83C\uDDF1"
            "US" -> "\uD83C\uDDFA\uD83C\uDDF8"
            "TR" -> "\uD83C\uDDF9\uD83C\uDDF7"
            "GB" -> "\uD83C\uDDEC\uD83C\uDDE7"
            "FR" -> "\uD83C\uDDEB\uD83C\uDDF7"
            else -> "\uD83C\uDF10"
        }

    Surface(
        onClick = onSelect,
        shape = MaterialTheme.shapes.medium,
        color =
        if (proxy.isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
        border =
        if (proxy.isSelected) null
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                flag,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(end = 12.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(proxy.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    proxy.ip,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioButton(selected = proxy.isSelected, onClick = onSelect)
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Sil",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
