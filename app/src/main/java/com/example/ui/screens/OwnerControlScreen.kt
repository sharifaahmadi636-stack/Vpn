package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.data.model.ConnectionLog
import com.example.data.model.PreferredProtocol
import com.example.data.model.SystemSettings
import com.example.data.model.UiState
import com.example.ui.components.SplitTunnelSheet
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.CyanCyber
import com.example.ui.theme.EmeraldSecure
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ObsidianCardElevated
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurface

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OwnerControlScreen(
    ownerEmail: String,
    ownerName: String,
    ownerId: String,
    settings: SystemSettings?,
    logsState: UiState<List<ConnectionLog>>,
    onUpdateSettings: (SystemSettings, String) -> Unit,
    onToggleBypassedPackage: (String, Boolean) -> Unit,
    onExportYaml: () -> Unit,
    onImportYaml: () -> Unit,
    onSignOut: () -> Unit,
) {
    val effectiveSettings = settings ?: SystemSettings(ownerId = ownerId)
    var primaryDns by remember(effectiveSettings.primaryDns) { mutableStateOf(effectiveSettings.primaryDns) }
    var secondaryDns by remember(effectiveSettings.secondaryDns) { mutableStateOf(effectiveSettings.secondaryDns) }
    var showSplitTunnelSheet by remember { mutableStateOf(false) }

    LaunchedEffect(effectiveSettings.primaryDns, effectiveSettings.secondaryDns) {
        primaryDns = effectiveSettings.primaryDns
        secondaryDns = effectiveSettings.secondaryDns
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Owner Identity Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCardElevated),
                border = BorderStroke(1.dp, EmeraldSecure.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                color = EmeraldSecure.copy(alpha = 0.16f),
                                shape = CircleShape
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AdminPanelSettings,
                                    contentDescription = "مالک سیستم",
                                    tint = EmeraldSecure,
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .size(26.dp)
                                )
                            }
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = ownerName.ifBlank { "مالک سامانه" },
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.VerifiedUser,
                                        contentDescription = "تأیید شده",
                                        tint = EmeraldSecure,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    Text(
                                        text = ownerEmail,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CyanCyber
                                    )
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onSignOut,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonAlert),
                        border = BorderStroke(1.dp, CrimsonAlert.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("owner_signout_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("خروج امن مالک")
                    }
                }
            }

            // Master System & Tunnel Security Controls
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCardElevated),
                border = BorderStroke(1.dp, ObsidianOutline)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.Security, contentDescription = null, tint = CyanCyber)
                        Text("کنترل مرکزی سیستم VPN", style = MaterialTheme.typography.titleLarge)
                    }

                    OwnerSwitchRow(
                        title = "وضعیت کلی سیستم (فعال / قفل مالک)",
                        subtitle = "در صورت غیرفعال‌سازی، تمام اتصالات تونل فوراً متوقف می‌شوند.",
                        checked = effectiveSettings.systemEnabled,
                        testTag = "master_system_switch",
                        onCheckedChange = { enabled ->
                            onUpdateSettings(
                                effectiveSettings.copy(systemEnabled = enabled),
                                if (enabled) "سامانه VPN توسط مالک فعال شد" else "سامانه VPN توسط مالک قفل شد"
                            )
                        }
                    )
                    HorizontalDivider(color = ObsidianOutline)

                    OwnerSwitchRow(
                        title = "اتصال خودکار پس از راه‌اندازی (Boot)",
                        subtitle = "برقراری خودکار تونل در پس‌زمینه پس از روشن شدن دستگاه",
                        checked = effectiveSettings.autoConnect,
                        onCheckedChange = { checked ->
                            onUpdateSettings(
                                effectiveSettings.copy(autoConnect = checked),
                                if (checked) "اتصال خودکار Boot فعال شد" else "اتصال خودکار Boot غیرفعال شد"
                            )
                        }
                    )
                    HorizontalDivider(color = ObsidianOutline)

                    OwnerSwitchRow(
                        title = "سوئیچ هوشمند به سرور پشتیبان (Failover)",
                        subtitle = "در صورت خطای سرور فعلی، انتخاب خودکار سریع‌ترین سرور فعال",
                        checked = effectiveSettings.autoFailover,
                        onCheckedChange = { checked ->
                            onUpdateSettings(
                                effectiveSettings.copy(autoFailover = checked),
                                if (checked) "سوئیچ خودکار Failover فعال شد" else "Failover غیرفعال شد"
                            )
                        }
                    )
                    HorizontalDivider(color = ObsidianOutline)

                    OwnerSwitchRow(
                        title = "Kill Switch (قطع اضطراری ترافیک)",
                        subtitle = "جلوگیری از نشت ترافیک در زمان قطع ناگهانی تونل",
                        checked = effectiveSettings.killSwitch,
                        onCheckedChange = { checked ->
                            onUpdateSettings(
                                effectiveSettings.copy(killSwitch = checked),
                                if (checked) "Kill Switch فعال شد" else "Kill Switch غیرفعال شد"
                            )
                        }
                    )
                    HorizontalDivider(color = ObsidianOutline)

                    OwnerSwitchRow(
                        title = "محافظت نشت DNS",
                        subtitle = "هدایت تمام درخواست‌های DNS از مسیر رمزنگاری‌شده",
                        checked = effectiveSettings.dnsProtection,
                        onCheckedChange = { checked ->
                            onUpdateSettings(
                                effectiveSettings.copy(dnsProtection = checked),
                                if (checked) "محافظت DNS فعال شد" else "محافظت DNS غیرفعال شد"
                            )
                        }
                    )
                    HorizontalDivider(color = ObsidianOutline)

                    OwnerSwitchRow(
                        title = "اعلان پایدار پس‌زمینه",
                        subtitle = "نمایش مداوم وضعیت تونل و دکمه قطع اتصال در نوار اعلانات",
                        checked = effectiveSettings.persistentNotification,
                        onCheckedChange = { checked ->
                            onUpdateSettings(
                                effectiveSettings.copy(persistentNotification = checked),
                                "تنظیم اعلان پس‌زمینه به‌روزرسانی شد"
                            )
                        }
                    )
                    HorizontalDivider(color = ObsidianOutline)

                    OwnerSwitchRow(
                        title = "تونل کامل دستگاه (0.0.0.0/0)",
                        subtitle = "در حالت خاموش از تونل هوشمند برای پایداری کنترل‌پلین Firebase استفاده می‌شود",
                        checked = effectiveSettings.fullTunnel,
                        onCheckedChange = { checked ->
                            onUpdateSettings(
                                effectiveSettings.copy(fullTunnel = checked),
                                if (checked) "حالت تونل کامل 0.0.0.0/0 فعال شد" else "حالت تونل هوشمند فعال شد"
                            )
                        }
                    )
                }
            }

            // Split Tunneling & YAML Backup Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCardElevated),
                border = BorderStroke(1.dp, ObsidianOutline)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("استثنای برنامه‌ها و فایل .yml", style = MaterialTheme.typography.titleMedium)

                    OutlinedButton(
                        onClick = { showSplitTunnelSheet = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("split_tunnel_button")
                    ) {
                        Icon(Icons.Filled.Apps, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("مدیریت Split Tunneling (${effectiveSettings.bypassedPackages.size} برنامه مستثنی)")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onImportYaml,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ورود .yml")
                        }
                        Button(
                            onClick = onExportYaml,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("خروجی .yml")
                        }
                    }
                }
            }

            // Protocol & DNS Policy
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCardElevated),
                border = BorderStroke(1.dp, ObsidianOutline)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("پروتکل ترجیحی و DNS اختصاصی", style = MaterialTheme.typography.titleMedium)

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PreferredProtocol.entries.forEach { proto ->
                            FilterChip(
                                selected = effectiveSettings.preferredProtocolEnum == proto,
                                onClick = {
                                    onUpdateSettings(
                                        effectiveSettings.copy(preferredProtocol = proto.name),
                                        "پروتکل پیش‌فرض روی ${proto.label} تنظیم شد"
                                    )
                                },
                                label = { Text(proto.label) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = primaryDns,
                            onValueChange = { primaryDns = it },
                            label = { Text("DNS اصلی") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = secondaryDns,
                            onValueChange = { secondaryDns = it },
                            label = { Text("DNS جایگزین") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Button(
                        onClick = {
                            onUpdateSettings(
                                effectiveSettings.copy(
                                    primaryDns = primaryDns.trim().ifBlank { "1.1.1.1" },
                                    secondaryDns = secondaryDns.trim().ifBlank { "9.9.9.9" }
                                ),
                                "آدرس‌های DNS در Firebase ذخیره شد"
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ذخیره تنظیمات DNS در Firebase")
                    }
                }
            }

            // Recent Firebase Audit Logs
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = ObsidianCardElevated),
                border = BorderStroke(1.dp, ObsidianOutline)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.History, contentDescription = null, tint = EmeraldSecure)
                        Text("گزارش رویدادها و کنترل مالک", style = MaterialTheme.typography.titleMedium)
                    }

                    val logs = (logsState as? UiState.Success)?.data.orEmpty()
                    if (logs.isEmpty()) {
                        Text(
                            text = "هنوز رویدادی ثبت نشده است.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        logs.take(10).forEach { log ->
                            Surface(
                                color = ObsidianSurface,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = log.eventTypeEnum.labelFa,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = EmeraldSecure
                                        )
                                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                            Text(
                                                text = "${log.countryCode} • ${log.protocol}",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.Medium
                                                ),
                                                color = CyanCyber
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${log.serverName}: ${log.note}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showSplitTunnelSheet) {
        SplitTunnelSheet(
            bypassedPackages = effectiveSettings.bypassedPackages,
            onTogglePackage = onToggleBypassedPackage,
            onDismiss = { showSplitTunnelSheet = false }
        )
    }
}

@Composable
private fun OwnerSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String? = null,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier
        )
    }
}
