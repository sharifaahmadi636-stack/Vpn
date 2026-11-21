package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.model.SystemSettings
import com.example.data.model.UiState
import com.example.data.model.VpnServer
import com.example.ui.theme.AmberConnecting
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyanCyber
import com.example.ui.theme.EmeraldSecure
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianCardElevated
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurface
import com.example.vpn.NetworkSecurityReport
import com.example.vpn.VpnConnectionState
import com.example.vpn.VpnTunnelSnapshot
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TunnelScreen(
    serversState: UiState<List<VpnServer>>,
    settings: SystemSettings?,
    tunnel: VpnTunnelSnapshot,
    isBootstrapping: Boolean,
    isPingingAll: Boolean,
    isCheckingSecurity: Boolean,
    securityReport: NetworkSecurityReport?,
    onToggleConnection: (VpnServer?) -> Unit,
    onConnectFastest: (List<VpnServer>) -> Unit,
    onRunSecurityCheck: () -> Unit,
    onNavigateToServers: () -> Unit,
    onRetryBootstrap: () -> Unit,
) {
    val servers = (serversState as? UiState.Success)?.data.orEmpty()
    val selectedServer = remember(servers, settings, tunnel.activeServerId) {
        servers.firstOrNull { it.id == settings?.selectedServerId }
            ?: servers.firstOrNull { it.id == tunnel.activeServerId }
            ?: servers.firstOrNull { it.active }
            ?: servers.firstOrNull()
    }

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(tunnel.state, tunnel.connectedSinceMs) {
        if (tunnel.state == VpnConnectionState.CONNECTED && tunnel.connectedSinceMs > 0L) {
            while (true) {
                nowMs = System.currentTimeMillis()
                delay(1000L)
            }
        }
    }

    val elapsedSec = if (tunnel.state == VpnConnectionState.CONNECTED && tunnel.connectedSinceMs > 0L) {
        ((nowMs - tunnel.connectedSinceMs) / 1000L).coerceAtLeast(0L)
    } else 0L

    val systemEnabled = settings?.systemEnabled ?: true

    val accentColor by animateColorAsState(
        targetValue = when {
            !systemEnabled -> CrimsonAlert
            tunnel.state == VpnConnectionState.CONNECTED -> EmeraldSecure
            tunnel.state == VpnConnectionState.CONNECTING -> AmberConnecting
            tunnel.state == VpnConnectionState.ERROR -> CrimsonAlert
            else -> CyanCyber
        },
        label = "tunnelAccent"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseFraction by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top System Status Pill
            Surface(
                color = accentColor.copy(alpha = 0.14f),
                shape = RoundedCornerShape(50.dp),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Text(
                        text = when {
                            !systemEnabled -> "سیستم توسط مالک قفل شده است"
                            tunnel.state == VpnConnectionState.CONNECTED -> "اتصال امن برقرار است"
                            tunnel.state == VpnConnectionState.CONNECTING -> "در حال برقراری تونل امن..."
                            tunnel.state == VpnConnectionState.ERROR -> "خطا در برقراری تونل"
                            else -> "تونل آماده فعال‌سازی"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = accentColor
                    )
                }
            }

            // Hero Animated Shield Orb
            Box(
                modifier = Modifier.size(220.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val baseRadius = size.minDimension / 2f
                    val scale = if (tunnel.state == VpnConnectionState.CONNECTED || tunnel.state == VpnConnectionState.CONNECTING) {
                        pulseFraction
                    } else 0.94f

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(accentColor.copy(alpha = 0.24f), Color.Transparent)
                        ),
                        radius = baseRadius * scale
                    )
                    drawCircle(
                        color = accentColor.copy(alpha = 0.22f),
                        radius = baseRadius * 0.86f * scale,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = accentColor.copy(alpha = 0.45f),
                        radius = baseRadius * 0.72f,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }

                Surface(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !isBootstrapping) {
                            onToggleConnection(selectedServer)
                        }
                        .testTag("vpn_connect_button"),
                    shape = CircleShape,
                    color = ObsidianCardElevated,
                    border = BorderStroke(2.5.dp, accentColor),
                    shadowElevation = 12.dp
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (tunnel.state == VpnConnectionState.CONNECTING || isBootstrapping) {
                            CircularProgressIndicator(
                                color = accentColor,
                                modifier = Modifier.size(38.dp),
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.PowerSettingsNew,
                                contentDescription = if (tunnel.state == VpnConnectionState.CONNECTED) "قطع اتصال VPN" else "اتصال VPN",
                                tint = accentColor,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = when (tunnel.state) {
                                VpnConnectionState.CONNECTED -> "قطع اتصال"
                                VpnConnectionState.CONNECTING -> "در حال اتصال"
                                else -> "فعال‌سازی VPN"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Session Timer & Status Note
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = formatDuration(elapsedSec),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (tunnel.state == VpnConnectionState.CONNECTED) EmeraldSecure else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tunnel.statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Quick Actions: Fastest Server + Security Audit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onConnectFastest(servers) },
                    enabled = !isPingingAll && servers.isNotEmpty() && systemEnabled,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanCyber.copy(alpha = 0.16f),
                        contentColor = CyanCyber
                    ),
                    border = BorderStroke(1.dp, CyanCyber.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("smart_fastest_button")
                ) {
                    if (isPingingAll) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = CyanCyber)
                    } else {
                        Icon(Icons.Filled.FlashOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("اتصال سریع‌ترین سرور", maxLines = 1)
                }

                OutlinedButton(
                    onClick = onRunSecurityCheck,
                    enabled = !isCheckingSecurity,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, EmeraldSecure.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("security_check_button")
                ) {
                    if (isCheckingSecurity) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = EmeraldSecure)
                    } else {
                        Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = EmeraldSecure, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("بررسی امنیت و IP", color = EmeraldSecure, maxLines = 1)
                }
            }

            if (securityReport != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    border = BorderStroke(1.dp, EmeraldSecure.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("نتیجه بررسی امنیتی شبکه", style = MaterialTheme.typography.titleMedium, color = EmeraldSecure)
                            Text(
                                text = if (securityReport.dnsReachable) "DNS و TLS رمزنگاری‌شده فعال است" else "DNS پاسخ نداد",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = securityReport.publicIp,
                                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = CyanCyber
                                )
                                Text(
                                    text = "${securityReport.datacenter} • ${securityReport.tlsVersion} • ${securityReport.rttMs}ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (serversState is UiState.Error) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRetryBootstrap() }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = CrimsonAlert)
                        Text(
                            text = "${serversState.message} (برای تلاش مجدد لمس کنید)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Active Server Selector Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onNavigateToServers() }
                    .testTag("selected_server_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCardElevated),
                border = BorderStroke(1.dp, ObsidianOutline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            color = CyanCyber.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = selectedServer?.countryCode ?: "--",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = CyanCyber
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = selectedServer?.let { "${it.country} • ${it.name}" }
                                    ?: "در حال همگام‌سازی با Firebase...",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(
                                    text = selectedServer?.let {
                                        "${it.protocolEnum.label} • ${it.host}:${it.port} • ${it.latencyMs} ms"
                                    } ?: "Firebase Firestore",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "تغییر سرور",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Live Throughput Sparkline Chart
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                border = BorderStroke(1.dp, ObsidianOutline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("نمودار زنده پهنای باند تونل", style = MaterialTheme.typography.titleMedium)
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "↓ %.1f kbps  ↑ %.1f kbps",
                                    tunnel.downloadSpeedKbps,
                                    tunnel.uploadSpeedKbps
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldSecure
                            )
                        }
                    }

                    val samples = if (tunnel.speedHistoryKbps.size >= 2) {
                        tunnel.speedHistoryKbps
                    } else {
                        listOf(0.5f, 1.0f, 0.7f, 1.2f, 0.8f, 0.9f)
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(68.dp)
                    ) {
                        val maxVal = (samples.maxOrNull() ?: 10f).coerceAtLeast(10f)
                        val stepX = size.width / (samples.size - 1).coerceAtLeast(1)
                        val linePath = Path()
                        val fillPath = Path()

                        samples.forEachIndexed { idx, value ->
                            val x = idx * stepX
                            val normalized = (value / maxVal).coerceIn(0f, 1f)
                            val y = size.height - (normalized * (size.height - 10.dp.toPx())) - 5.dp.toPx()
                            if (idx == 0) {
                                linePath.moveTo(x, y)
                                fillPath.moveTo(x, size.height)
                                fillPath.lineTo(x, y)
                            } else {
                                linePath.lineTo(x, y)
                                fillPath.lineTo(x, y)
                            }
                            if (idx == samples.lastIndex) {
                                fillPath.lineTo(x, size.height)
                                fillPath.close()
                            }
                        }

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(accentColor.copy(alpha = 0.28f), Color.Transparent)
                            )
                        )
                        drawPath(
                            path = linePath,
                            color = accentColor,
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
            }

            // Telemetry Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TelemetryMetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Speed,
                    label = "تأخیر (پینگ)",
                    value = "${if (tunnel.measuredLatencyMs > 0) tunnel.measuredLatencyMs else (selectedServer?.latencyMs ?: 0)} ms",
                    accent = EmeraldSecure
                )
                TelemetryMetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.SwapVert,
                    label = "ترافیک / بسته‌ها",
                    value = "${formatBytes(tunnel.bytesIn + tunnel.bytesOut)} • ${tunnel.packetsIn + tunnel.packetsOut}p",
                    accent = CyanCyber
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TelemetryMetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Lock,
                    label = "رمزنگاری فعال",
                    value = selectedServer?.cipher ?: "ChaCha20-Poly1305",
                    accent = EmeraldSecure
                )
                TelemetryMetricCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Public,
                    label = "آدرس TUN / DNS",
                    value = "${tunnel.localTunnelIp} • ${settings?.primaryDns ?: "1.1.1.1"}",
                    accent = CyanCyber
                )
            }

            // Active Policy Badges
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SecurityBadgeChip(
                    icon = Icons.Filled.Shield,
                    text = if (settings?.killSwitch != false) "Kill Switch فعال" else "Kill Switch غیرفعال",
                    enabled = settings?.killSwitch != false
                )
                SecurityBadgeChip(
                    icon = Icons.Filled.Dns,
                    text = if (settings?.dnsProtection != false) "محافظت نشت DNS" else "DNS استاندارد",
                    enabled = settings?.dnsProtection != false
                )
                SecurityBadgeChip(
                    icon = Icons.Filled.NotificationsActive,
                    text = if (settings?.persistentNotification != false) "اعلان پس‌زمینه فعال" else "اعلان حداقل",
                    enabled = settings?.persistentNotification != false
                )
                SecurityBadgeChip(
                    icon = Icons.Filled.Security,
                    text = if ((settings?.bypassedPackages?.size ?: 0) > 0) "${settings?.bypassedPackages?.size} برنامه مستثنی" else "بدون استثنای برنامه",
                    enabled = true
                )
                SecurityBadgeChip(
                    icon = Icons.Filled.Bolt,
                    text = if (settings?.fullTunnel == true) "تونل کامل 0.0.0.0/0" else "تونل هوشمند",
                    enabled = true
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun TelemetryMetricCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        border = BorderStroke(1.dp, ObsidianOutline)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SecurityBadgeChip(
    icon: ImageVector,
    text: String,
    enabled: Boolean,
) {
    val tint = if (enabled) EmeraldSecure else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (enabled) EmeraldSecure.copy(alpha = 0.35f) else ObsidianOutline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
            Text(text = text, style = MaterialTheme.typography.bodyMedium, color = tint)
        }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
}
