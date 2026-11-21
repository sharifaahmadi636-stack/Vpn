package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.model.SystemSettings
import com.example.data.model.UiState
import com.example.data.model.VpnServer
import com.example.ui.components.ServerEditorSheet
import com.example.ui.theme.AmberConnecting
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyanCyber
import com.example.ui.theme.EmeraldSecure
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianCardElevated
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurface
import com.example.vpn.VpnConnectionState
import com.example.vpn.VpnTunnelSnapshot

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServersScreen(
    ownerId: String,
    serversState: UiState<List<VpnServer>>,
    settings: SystemSettings?,
    tunnel: VpnTunnelSnapshot,
    isPingingAll: Boolean,
    pingingServerIds: Set<String>,
    onConnectServer: (VpnServer) -> Unit,
    onPingServer: (VpnServer) -> Unit,
    onPingAll: (List<VpnServer>) -> Unit,
    onToggleServerActive: (VpnServer, Boolean) -> Unit,
    onSaveServer: (VpnServer, Boolean) -> Unit,
    onDeleteServer: (VpnServer) -> Unit,
    onExportYaml: () -> Unit,
    onImportYaml: () -> Unit,
    onCopyServerYaml: (VpnServer) -> String,
    onShowMessage: (String) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedRegion by remember { mutableStateOf("ALL") }
    var editingServer by remember { mutableStateOf<VpnServer?>(null) }
    var showCreateSheet by remember { mutableStateOf(false) }

    val servers = (serversState as? UiState.Success)?.data.orEmpty()
    val filteredServers = remember(servers, searchQuery, selectedRegion) {
        servers.filter { server ->
            val matchesRegion = selectedRegion == "ALL" || server.region.equals(selectedRegion, ignoreCase = true)
            val query = searchQuery.trim().lowercase()
            val matchesQuery = query.isEmpty() ||
                server.name.lowercase().contains(query) ||
                server.country.lowercase().contains(query) ||
                server.city.lowercase().contains(query) ||
                server.host.lowercase().contains(query) ||
                server.protocolEnum.label.lowercase().contains(query)
            matchesRegion && matchesQuery
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "سرورهای VPN در Firebase",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "${servers.count { it.active }} سرور فعال از ${servers.size} گره جهانی",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = { showCreateSheet = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("add_server_button")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("افزودن سرور")
                }
            }

            // YAML Import/Export + Ping All Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onImportYaml,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("import_yaml_button")
                ) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("ورود .yml", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onExportYaml,
                    enabled = servers.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_yaml_button")
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("خروجی .yml", maxLines = 1)
                }
                OutlinedButton(
                    onClick = { onPingAll(servers) },
                    enabled = !isPingingAll && servers.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (isPingingAll) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.NetworkPing, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("پینگ همه", maxLines = 1)
                }
            }

            // Search
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("جستجو نام، کشور، IP یا پروتکل...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            // Region Chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "ALL" to "همه مناطق",
                    "Europe" to "اروپا",
                    "North America" to "آمریکا",
                    "Asia" to "آسیا"
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = selectedRegion == key,
                        onClick = { selectedRegion = key },
                        label = { Text(label) }
                    )
                }
            }

            when (serversState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = EmeraldSecure)
                    }
                }
                is UiState.Error -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = serversState.message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                is UiState.Success -> {
                    if (filteredServers.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "سروری با این فیلتر یافت نشد.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(filteredServers, key = { it.id }) { server ->
                                val isConnectedToThis = tunnel.state == VpnConnectionState.CONNECTED &&
                                    tunnel.activeServerId == server.id
                                val isSelected = settings?.selectedServerId == server.id

                                ServerNodeCard(
                                    server = server,
                                    isSelected = isSelected,
                                    isConnected = isConnectedToThis,
                                    isPinging = server.id in pingingServerIds,
                                    onConnect = { onConnectServer(server) },
                                    onPing = { onPingServer(server) },
                                    onCopyYaml = {
                                        val yaml = onCopyServerYaml(server)
                                        clipboard.setText(AnnotatedString(yaml))
                                        onShowMessage("پیکربندی .yml سرور ${server.name} کپی شد.")
                                    },
                                    onToggleActive = { active -> onToggleServerActive(server, active) },
                                    onEdit = { editingServer = server },
                                    onDelete = { onDeleteServer(server) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateSheet || editingServer != null) {
        ServerEditorSheet(
            initialServer = editingServer,
            ownerId = ownerId,
            onDismiss = {
                showCreateSheet = false
                editingServer = null
            },
            onSave = { payload, isEdit ->
                onSaveServer(payload, isEdit)
                showCreateSheet = false
                editingServer = null
            }
        )
    }
}

@Composable
private fun ServerNodeCard(
    server: VpnServer,
    isSelected: Boolean,
    isConnected: Boolean,
    isPinging: Boolean,
    onConnect: () -> Unit,
    onPing: () -> Unit,
    onCopyYaml: () -> Unit,
    onToggleActive: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val latencyColor = when {
        server.latencyMs <= 75L -> EmeraldSecure
        server.latencyMs <= 160L -> AmberConnecting
        else -> CrimsonAlert
    }

    val borderColor = when {
        isConnected -> EmeraldSecure
        isSelected -> CyanCyber
        else -> ObsidianOutline
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCardElevated),
        border = BorderStroke(if (isConnected || isSelected) 1.5.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = ObsidianSurface,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, ObsidianOutline),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = server.countryCode,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = CyanCyber
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${server.country} • ${server.name}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (server.recommended) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = "پیشنهادی",
                                    tint = AmberConnecting,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = "${server.city} • ${server.host}:${server.port}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Switch(
                    checked = server.active,
                    onCheckedChange = onToggleActive
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = ObsidianSurface,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = "${server.protocolEnum.label} • ${server.cipher}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanCyber,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }

                Surface(
                    color = latencyColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            text = "${server.latencyMs} ms • Load ${server.loadPercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = latencyColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onConnect,
                    enabled = server.active,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) EmeraldSecure else CyanCyber,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (isConnected) Icons.Filled.CheckCircle else Icons.Filled.PowerSettingsNew,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            isConnected -> "متصل"
                            !server.active -> "غیرفعال"
                            else -> "اتصال و انتخاب"
                        }
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCopyYaml) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "کپی YAML", tint = EmeraldSecure)
                    }
                    IconButton(onClick = onPing, enabled = !isPinging) {
                        if (isPinging) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.NetworkPing, contentDescription = "تست پینگ", tint = CyanCyber)
                        }
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = "ویرایش سرور", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "حذف سرور", tint = CrimsonAlert)
                    }
                }
            }
        }
    }
}
