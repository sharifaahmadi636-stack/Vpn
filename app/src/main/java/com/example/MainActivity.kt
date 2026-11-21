package com.example

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.OwnerAuthManager
import com.example.data.model.SystemSettings
import com.example.data.model.UiState
import com.example.data.model.VpnServer
import com.example.data.repository.VpnRepository
import com.example.ui.screens.OwnerAuthScreen
import com.example.ui.screens.OwnerControlScreen
import com.example.ui.screens.ServersScreen
import com.example.ui.screens.TunnelScreen
import com.example.ui.theme.AegisVpnTheme
import com.example.ui.theme.EmeraldSecure
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.viewmodel.VpnViewModel
import com.example.vpn.VpnConnectionState
import com.example.vpn.VpnTunnelManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private enum class AegisTab(val titleRes: Int, val tag: String) {
    TUNNEL(R.string.tab_tunnel, "nav_tunnel"),
    SERVERS(R.string.tab_servers, "nav_servers"),
    OWNER(R.string.tab_owner_control, "nav_owner")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AegisVpnTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AegisRootApp()
                }
            }
        }
    }
}

@Composable
private fun AegisRootApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var unauthorizedError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            val user = auth.currentUser
            if (user != null && !OwnerAuthManager.isAuthorizedOwner(user)) {
                unauthorizedError = context.getString(R.string.owner_unauthorized_error)
                OwnerAuthManager.signOut(credentialManager, scope)
                currentUser = null
            } else {
                currentUser = user
            }
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    val user = currentUser
    if (user == null || !OwnerAuthManager.isAuthorizedOwner(user)) {
        OwnerAuthScreen(
            externalError = unauthorizedError,
            onClearExternalError = { unauthorizedError = null },
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        key(user.uid) {
            OwnerDashboardScaffold(
                currentUserId = user.uid,
                ownerEmail = user.email.orEmpty(),
                ownerName = user.displayName ?: "مالک سیستم",
                onSignOut = {
                    VpnTunnelManager.stopTunnel(context)
                    OwnerAuthManager.signOut(credentialManager, scope) {
                        currentUser = null
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OwnerDashboardScaffold(
    currentUserId: String,
    ownerEmail: String,
    ownerName: String,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val viewModel: VpnViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                VpnViewModel(
                    repository = VpnRepository(db),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val serversState by viewModel.serversState.collectAsStateWithLifecycle()
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val logsState by viewModel.logsState.collectAsStateWithLifecycle()
    val tunnel by viewModel.tunnelSnapshot.collectAsStateWithLifecycle()
    val isBootstrapping by viewModel.isBootstrapping.collectAsStateWithLifecycle()
    val isPingingAll by viewModel.isPingingAll.collectAsStateWithLifecycle()
    val pingingServerIds by viewModel.pingingServerIds.collectAsStateWithLifecycle()
    val isCheckingSecurity by viewModel.isCheckingSecurity.collectAsStateWithLifecycle()
    val securityReport by viewModel.securityReport.collectAsStateWithLifecycle()
    val feedbackMessage by viewModel.feedbackMessage.collectAsStateWithLifecycle()

    val settings = (settingsState as? UiState.Success)?.data
    val servers = (serversState as? UiState.Success)?.data.orEmpty()

    var currentTab by remember { mutableStateOf(AegisTab.TUNNEL) }
    var pendingConnectServer by remember { mutableStateOf<VpnServer?>(null) }

    if (currentTab != AegisTab.TUNNEL) {
        BackHandler { currentTab = AegisTab.TUNNEL }
    }

    LaunchedEffect(feedbackMessage) {
        val msg = feedbackMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearFeedback()
    }

    // Keep local tile/boot policy snapshot synced with Firebase settings & selected server
    LaunchedEffect(servers, settings) {
        val selected = servers.firstOrNull { it.id == settings?.selectedServerId }
            ?: servers.firstOrNull { it.active }
        VpnTunnelManager.saveLastPolicy(context, selected, settings)
    }

    // Enforce owner master lock
    LaunchedEffect(settings?.systemEnabled, tunnel.state) {
        if (settings?.systemEnabled == false &&
            (tunnel.state == VpnConnectionState.CONNECTED || tunnel.state == VpnConnectionState.CONNECTING)
        ) {
            VpnTunnelManager.stopTunnel(context)
            viewModel.showFeedback("سیستم توسط مالک قفل شد و تونل متوقف گردید.")
        }
    }

    // Zero-permission SAF .yml export launcher
    val exportYamlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/yaml")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val yamlContent = viewModel.exportYamlString(servers, settings)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(yamlContent.toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                viewModel.showFeedback("فایل .yml با موفقیت ذخیره شد.")
            }.onFailure {
                viewModel.showFeedback("خطا در ذخیره فایل .yml")
            }
        }
    }

    // Zero-permission SAF .yml import launcher
    val importYamlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            }.onSuccess { text ->
                viewModel.importServersFromYaml(text)
            }.onFailure {
                viewModel.showFeedback("خطا در خواندن فایل .yml")
            }
        }
    }

    fun executeVpnStart(server: VpnServer, activeSettings: SystemSettings?) {
        VpnTunnelManager.startTunnel(context, server, activeSettings)
        viewModel.onTunnelConnected(server, server.latencyMs)
    }

    val vpnConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val server = pendingConnectServer
        pendingConnectServer = null
        if (result.resultCode == Activity.RESULT_OK && server != null) {
            executeVpnStart(server, settings)
        } else {
            viewModel.showFeedback("مجوز سیستم VPN برای برقراری تونل الزامی است.")
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        val server = pendingConnectServer ?: return@rememberLauncherForActivityResult
        val prepareIntent = VpnTunnelManager.prepareVpnIntent(context)
        if (prepareIntent != null) {
            vpnConsentLauncher.launch(prepareIntent)
        } else {
            pendingConnectServer = null
            executeVpnStart(server, settings)
        }
    }

    fun startConnectionFlow(server: VpnServer?) {
        if (settings?.systemEnabled == false) {
            viewModel.showFeedback("سیستم توسط مالک غیرفعال است. از بخش کنترل مالک فعال کنید.")
            return
        }
        val target = server ?: servers.firstOrNull { it.active }
        if (target == null) {
            viewModel.showFeedback("هیچ سرور فعالی در Firebase یافت نشد.")
            return
        }
        if (!target.active) {
            viewModel.showFeedback("این سرور غیرفعال است. ابتدا آن را فعال کنید.")
            return
        }

        if (settings?.selectedServerId != target.id) {
            viewModel.selectServer(target, settings)
        }

        pendingConnectServer = target
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        val prepareIntent = VpnTunnelManager.prepareVpnIntent(context)
        if (prepareIntent != null) {
            vpnConsentLauncher.launch(prepareIntent)
        } else {
            pendingConnectServer = null
            executeVpnStart(target, settings)
        }
    }

    fun disconnectFlow() {
        val elapsedSec = if (tunnel.connectedSinceMs > 0L) {
            ((System.currentTimeMillis() - tunnel.connectedSinceMs) / 1000L).coerceAtLeast(0L)
        } else 0L
        val totalBytes = tunnel.bytesIn + tunnel.bytesOut
        val activeServer = servers.firstOrNull { it.id == tunnel.activeServerId }
        VpnTunnelManager.stopTunnel(context)
        viewModel.onTunnelDisconnected(activeServer, elapsedSec, totalBytes)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 680.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = ObsidianBg,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = "AEGIS VPN",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = SpaceGroteskFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = EmeraldSecure
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = ObsidianBg
                    )
                )
            },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar(
                        containerColor = ObsidianSurface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        AegisTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = tabIcon(tab, selected),
                                        contentDescription = stringResource(tab.titleRes)
                                    )
                                },
                                label = { Text(stringResource(tab.titleRes)) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(containerColor = ObsidianSurface) {
                        AegisTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = tabIcon(tab, selected),
                                        contentDescription = stringResource(tab.titleRes)
                                    )
                                },
                                label = { Text(stringResource(tab.titleRes)) },
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    when (currentTab) {
                        AegisTab.TUNNEL -> TunnelScreen(
                            serversState = serversState,
                            settings = settings,
                            tunnel = tunnel,
                            isBootstrapping = isBootstrapping,
                            isPingingAll = isPingingAll,
                            isCheckingSecurity = isCheckingSecurity,
                            securityReport = securityReport,
                            onToggleConnection = { selectedServer ->
                                if (tunnel.state == VpnConnectionState.CONNECTED ||
                                    tunnel.state == VpnConnectionState.CONNECTING
                                ) {
                                    disconnectFlow()
                                } else {
                                    startConnectionFlow(selectedServer)
                                }
                            },
                            onConnectFastest = { allServers ->
                                viewModel.connectFastestServer(allServers) { fastest ->
                                    startConnectionFlow(fastest)
                                }
                            },
                            onRunSecurityCheck = { viewModel.runSecurityCheck() },
                            onNavigateToServers = { currentTab = AegisTab.SERVERS },
                            onRetryBootstrap = { viewModel.bootstrapDefaults() }
                        )

                        AegisTab.SERVERS -> ServersScreen(
                            ownerId = currentUserId,
                            serversState = serversState,
                            settings = settings,
                            tunnel = tunnel,
                            isPingingAll = isPingingAll,
                            pingingServerIds = pingingServerIds,
                            onConnectServer = { server ->
                                if (tunnel.state == VpnConnectionState.CONNECTED &&
                                    tunnel.activeServerId == server.id
                                ) {
                                    disconnectFlow()
                                } else {
                                    startConnectionFlow(server)
                                    currentTab = AegisTab.TUNNEL
                                }
                            },
                            onPingServer = { viewModel.pingServer(it) },
                            onPingAll = { viewModel.pingAllServers(it) },
                            onToggleServerActive = { server, active ->
                                viewModel.toggleServerActive(server, active)
                                if (!active && tunnel.activeServerId == server.id) {
                                    disconnectFlow()
                                }
                            },
                            onSaveServer = { server, isEdit ->
                                viewModel.saveServer(server, isEdit)
                            },
                            onDeleteServer = { server ->
                                if (tunnel.activeServerId == server.id) {
                                    disconnectFlow()
                                }
                                viewModel.deleteServer(server)
                            },
                            onExportYaml = { exportYamlLauncher.launch("aegis-vpn-servers.yml") },
                            onImportYaml = { importYamlLauncher.launch(arrayOf("*/*")) },
                            onCopyServerYaml = { viewModel.exportSingleServerYaml(it) },
                            onShowMessage = { viewModel.showFeedback(it) }
                        )

                        AegisTab.OWNER -> OwnerControlScreen(
                            ownerEmail = ownerEmail,
                            ownerName = ownerName,
                            ownerId = currentUserId,
                            settings = settings,
                            logsState = logsState,
                            onUpdateSettings = { updated, note ->
                                viewModel.updateSystemSettings(updated, note)
                            },
                            onToggleBypassedPackage = { pkg, bypassed ->
                                viewModel.toggleBypassedPackage(settings, pkg, bypassed)
                            },
                            onExportYaml = { exportYamlLauncher.launch("aegis-vpn-servers.yml") },
                            onImportYaml = { importYamlLauncher.launch(arrayOf("*/*")) },
                            onSignOut = onSignOut
                        )
                    }
                }
            }
        }
    }
}

private fun tabIcon(tab: AegisTab, selected: Boolean) = when (tab) {
    AegisTab.TUNNEL -> if (selected) Icons.Filled.Shield else Icons.Outlined.Shield
    AegisTab.SERVERS -> if (selected) Icons.Filled.Dns else Icons.Outlined.Dns
    AegisTab.OWNER -> if (selected) Icons.Filled.AdminPanelSettings else Icons.Outlined.AdminPanelSettings
}
