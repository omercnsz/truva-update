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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.lifecycleScope
import com.truva.R
import com.truva.nidg.NidgEngine
import com.truva.ui.*
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
    private lateinit var gamingVpnLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
    private lateinit var provisionLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
    private lateinit var notificationPermissionLauncher:
            androidx.activity.result.ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // NIDG: Context'i ayarla (sinyal ve traceroute modülleri için gerekli)
        NidgEngine.initialize(this)

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
                    } else {
                        viewModel.forceIdleState()
                    }
                }

        // Oyun Modu VPN izni sonucu
        gamingVpnLauncher =
                registerForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts
                                .StartActivityForResult()
                ) { result ->
                    if (result.resultCode == RESULT_OK) {
                        viewModel.connectGameMode()
                    } else {
                        viewModel.forceIdleState()
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

        // Sözleşme ve Başlangıç İzinleri Kontrolü
        val prefs = getSharedPreferences("truva_prefs", MODE_PRIVATE)
        var isAgreementAccepted by mutableStateOf(prefs.getBoolean("agreement_accepted", false))

        setContent {
            TruvaTheme {
                if (!isAgreementAccepted) {
                    AgreementScreen(
                        onAccept = {
                            prefs.edit().putBoolean("agreement_accepted", true).apply()
                            isAgreementAccepted = true
                        },
                        onReject = {
                            finish() // Uygulamayı kapat
                        }
                    )
                } else {
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
                                    onDisconnect = { stopVpn() },
                                    onGamingConnect = { startGamingVpn() }
                            )
                        } else {
                            ExpiredScreen(
                                    isInWorkProfile = isInWorkProfile,
                                    onNavigateToGateway = {
                                        if (isInWorkProfile) {
                                            // İş profili → Ana profildeki Truva'yı aç (CrossProfileApps)
                                            try {
                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                                                    val crossProfileApps = getSystemService(android.content.Context.CROSS_PROFILE_APPS_SERVICE) as android.content.pm.CrossProfileApps
                                                    val targetUser = crossProfileApps.targetUserProfiles.firstOrNull()
                                                    if (targetUser != null) {
                                                        crossProfileApps.startMainActivity(componentName, targetUser)
                                                    } else {
                                                        android.widget.Toast.makeText(this@MainActivity, "Ana profil bulunamadı", android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    // API 28 altı için fallback (nadirdir)
                                                    android.widget.Toast.makeText(this@MainActivity, "Lütfen ana profildeki Truva VPN'yi manuel açın", android.widget.Toast.LENGTH_LONG).show()
                                                }
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(this@MainActivity, "Ana Truva VPN açılamadı", android.widget.Toast.LENGTH_SHORT).show()
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
        }

        // Başlangıç İzinleri (Sadece ilk girişte sorulur)
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
        viewModel.forceConnectingState()
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

    private fun startGamingVpn() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            gamingVpnLauncher.launch(intent)
        } else {
            viewModel.connectGameMode()
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
                    Toast.makeText(this, "❌ Truva VPN sürümünüz eskidir. Lütfen güncelleyin!", Toast.LENGTH_LONG).show()
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
fun TruvaDashboard(
    viewModel: TruvaViewModel,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onGamingConnect: () -> Unit
) {
    val proxies by viewModel.allProxies.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState(initial = VpnState.IDLE)
    val activeServer by viewModel.activeServerName.collectAsState(initial = null)
    val errorMsg by viewModel.errorMessage.collectAsState(initial = null)
    val truvaSettings by viewModel.settings.collectAsState()
    val spoofingStatus by viewModel.spoofingStatus.collectAsState()

    val selectedTab by viewModel.selectedTab.collectAsState()

    val isConnecting = connectionState == VpnState.CONNECTING
    val isConnected = connectionState == VpnState.CONNECTED
    val isDisconnecting = connectionState == VpnState.DISCONNECTING
    val isBusy = isConnecting || isDisconnecting

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(errorMsg) {
        errorMsg?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    // Hangi alt ekran (Dashboard içindeki Drawer menüsü geçişleri için)
    var selectedDrawerItem by remember { mutableIntStateOf(0) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerShape = RectangleShape,
                modifier = Modifier.width(300.dp).fillMaxHeight()
            ) {
                // Drawer Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "TRUVA VPN",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "v19.6.0 Premium",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))

                // Menu Items
                val menuItems = listOf<Triple<Int, String, ImageVector>>(
                    Triple(0, "Dashboard", Icons.Default.Shield),
                    Triple(1, "İzolasyon (Sandbox)", Icons.Default.Lock),
                    Triple(2, "Bölge Değiştirici", Icons.Default.Public),
                    Triple(3, "Güvenlik Testi", Icons.Default.Info),
                    Triple(4, "İzin Yönetimi", Icons.Default.Settings),
                    Triple(5, "Ağ Analizi", Icons.Default.Analytics)
                )

                menuItems.forEach { (id, label, icon) ->
                    NavigationDrawerItem(
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(label, fontWeight = FontWeight.Bold) },
                        selected = selectedDrawerItem == id,
                        onClick = {
                            selectedDrawerItem = id
                            viewModel.selectTab(TruvaViewModel.TruvaTab.DASHBOARD)
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            unselectedContainerColor = Color.Transparent,
                            unselectedTextColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            unselectedIconColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            if (selectedTab == TruvaViewModel.TruvaTab.GAMING) {
                                "NİTRO GEÇİT"
                            } else if (selectedTab == TruvaViewModel.TruvaTab.NITRO_DPI) {
                                "NİTRO OYUN"
                            } else {
                                when (selectedDrawerItem) {
                                    0 -> "DASHBOARD"
                                    1 -> "İZOLASYON"
                                    2 -> "BÖLGE DEĞIŞTIRICI"
                                    3 -> "GÜVENLIK TESTI"
                                    4 -> "İZİNLER"
                                    5 -> "AĞ ANALİZİ"
                                    6 -> "NIDG"
                                    else -> "TRUVA VPN"
                                }
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menü")
                        }
                    },
                    actions = {
                        val sessionActive by viewModel.isSessionActive.collectAsState()
                        if (sessionActive) {
                            val sessionRemaining by viewModel.remainingTimeFormatted.collectAsState()
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.padding(end = 12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = "Kalan Süre",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = sessionRemaining,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.primary
                    )
                )
            },
            bottomBar = {
                // Nitro ve Oyun sekmeleri artık ana Truva'da da görünür (iş profili şartı kaldırıldı).
                run {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Shield, contentDescription = null) },
                            label = { Text("VPN") },
                            selected = selectedTab == TruvaViewModel.TruvaTab.DASHBOARD,
                            onClick = { viewModel.selectTab(TruvaViewModel.TruvaTab.DASHBOARD) }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.Gamepad, contentDescription = null) },
                            label = { Text("Nitro") },
                            selected = selectedTab == TruvaViewModel.TruvaTab.GAMING,
                            onClick = { viewModel.selectTab(TruvaViewModel.TruvaTab.GAMING) }
                        )
                        NavigationBarItem(
                            icon = { Icon(Icons.Default.SportsEsports, contentDescription = null) },
                            label = { Text("Oyun") },
                            selected = selectedTab == TruvaViewModel.TruvaTab.NITRO_DPI,
                            onClick = { viewModel.selectTab(TruvaViewModel.TruvaTab.NITRO_DPI) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (selectedTab == TruvaViewModel.TruvaTab.GAMING) {
                    GamingModeScreen(viewModel = viewModel, onConnect = onGamingConnect)
                } else if (selectedTab == TruvaViewModel.TruvaTab.NITRO_DPI) {
                    com.truva.ui.NitroDpiScreen(viewModel = viewModel, onConnect = { viewModel.connectNitroDpi() })
                } else {
                    when (selectedDrawerItem) {
                        0 -> {
                            // DASHBOARD: Verilen sıraya göre: Konum -> Durum -> Yönlendirme -> Liste -> Buton
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 1. En Üstte Konum Bilgisi
                                item {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Konum Bilgisi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                    IpStatusSection(viewModel = viewModel)
                                }

                                // 2. Akıllı Yönlendirme
                                item {
                                    Text("Akıllı Yönlendirme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                    SmartRoutingSection(viewModel = viewModel, settings = truvaSettings)
                                }

                                // 4. VPN Sunucu Listesi (3. Oyun Modu Buradan Kaldırıldı)
                                item {
                                    Text("Sunucu Seçimi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                                }
                            items(proxies, key = { it.id }) { proxy ->
                                ProxyRow(proxy, onSelect = { viewModel.selectProxy(proxy) }, onDelete = { viewModel.deleteProxy(proxy) })
                            }

                            // 4. En Altta Bağlan Butonu
                            item {
                                // Durum kartını buraya, butonun hemen üstüne taşıdık.
                                StatusCard(connectionState, activeServer, isBusy, isConnected, errorMsg)
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Button(
                                        onClick = onConnect,
                                        enabled = !isBusy && !isConnected,
                                        modifier = Modifier.fillMaxWidth().height(64.dp),
                                        shape = MaterialTheme.shapes.extraLarge,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF00695C), // Vibrant Teal
                                            contentColor = Color.White
                                        )
                                    ) {
                                        if (isConnecting) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp, color = Color.White)
                                        } else {
                                            Text(if (isConnected) "BAĞLI" else "BAĞLAN", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                                        }
                                    }
                                }
                                if (isConnected) {
                                    Button(
                                        onClick = onDisconnect,
                                        enabled = !isBusy,
                                        modifier = Modifier.fillMaxWidth().height(56.dp).padding(bottom = 16.dp),
                                        shape = MaterialTheme.shapes.large,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFC62828), // Vibrant Red
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Text("BAĞLANTIYI KES", fontWeight = FontWeight.Black, color = Color.White)
                                    }
                                }
                                Spacer(modifier = Modifier.height(32.dp))
                            }
                        }
                    }
                    1 -> {
                        // UYGULAMA İZOLASYONU - Kaydırılabilir yapıldı
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            item { SandboxSection(viewModel = viewModel, settings = truvaSettings, onOpenIntegrityTest = { selectedDrawerItem = 3 }) }
                        }
                    }
                    2 -> {
                        // BÖLGE DEĞİŞTİRİCİ - Kaydırılabilir yapıldı
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            item { RegionProfileSection(viewModel = viewModel, settings = truvaSettings, spoofingStatus = spoofingStatus) }
                        }
                    }
                    3 -> {
                        // GÜVENLİK TESTİ - Kaydırılabilir yapıldı
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            item { ManipulationIntegrityScreen(viewModel = viewModel) }
                        }
                    }
                    4 -> {
                        // İZİNLER - Kaydırılabilir yapıldı
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            item { Box(modifier = Modifier.fillMaxWidth().height(800.dp)) { com.truva.ui.PermissionDashboardScreen() } }
                        }
                    }
                    5 -> {
                        // AĞ ANALİZİ (NIDG)
                        val nidgReport by NidgEngine.report.collectAsState()
                        NidgDashboardScreen(report = nidgReport)
                    }
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
            VpnState.GAMING -> Triple(Color(0xFF00E5FF), Color(0xFF1A237E), "Nitro Geçit: Aktif")
            VpnState.NITRO_DPI -> Triple(Color(0xFFFFEA00), Color(0xFFD50000), "Nitro Oyun: 0 Ping DPI Aktif")
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
                    imageVector = when {
                        state == VpnState.GAMING || state == VpnState.NITRO_DPI -> Icons.Default.Bolt
                        isConnected -> Icons.Default.Lock
                        else -> Icons.Default.Info
                    },
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


