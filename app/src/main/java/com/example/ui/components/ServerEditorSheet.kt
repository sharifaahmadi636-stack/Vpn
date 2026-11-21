package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.VpnProtocol
import com.example.data.model.VpnServer
import com.example.ui.theme.ObsidianCardElevated

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ServerEditorSheet(
    initialServer: VpnServer?,
    ownerId: String,
    onDismiss: () -> Unit,
    onSave: (VpnServer, Boolean) -> Unit,
) {
    val isEdit = initialServer != null
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf(initialServer?.name ?: "") }
    var country by remember { mutableStateOf(initialServer?.country ?: "") }
    var countryCode by remember { mutableStateOf(initialServer?.countryCode ?: "DE") }
    var city by remember { mutableStateOf(initialServer?.city ?: "") }
    var host by remember { mutableStateOf(initialServer?.host ?: "") }
    var portText by remember { mutableStateOf((initialServer?.port ?: 51820L).toString()) }
    var protocol by remember { mutableStateOf(initialServer?.protocolEnum ?: VpnProtocol.WIREGUARD) }
    var cipher by remember { mutableStateOf(initialServer?.cipher ?: VpnProtocol.WIREGUARD.defaultCipher) }
    var dns by remember { mutableStateOf(initialServer?.dns ?: "1.1.1.1") }
    var tunnelAddress by remember { mutableStateOf(initialServer?.tunnelAddress ?: "10.8.0.2") }
    var mtuText by remember { mutableStateOf((initialServer?.mtu ?: 1420L).toString()) }
    var publicKey by remember { mutableStateOf(initialServer?.publicKey ?: "") }
    var region by remember { mutableStateOf(initialServer?.region ?: "Europe") }
    var active by remember { mutableStateOf(initialServer?.active ?: true) }
    var recommended by remember { mutableStateOf(initialServer?.recommended ?: false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianCardElevated
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (isEdit) "ویرایش سرور VPN در Firebase" else "افزودن سرور جدید به Firebase",
                style = MaterialTheme.typography.headlineSmall
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("نام سرور (مثلاً Frankfurt Core-02)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("server_name_input")
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = country,
                    onValueChange = { country = it },
                    label = { Text("کشور (فارسی)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = countryCode,
                    onValueChange = { countryCode = it.uppercase().take(4) },
                    label = { Text("کد (DE/US)") },
                    singleLine = true,
                    modifier = Modifier.weight(0.6f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("شهر") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = region,
                    onValueChange = { region = it },
                    label = { Text("منطقه (Europe/Asia)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("IP یا دامنه سرور") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1.4f)
                        .testTag("server_host_input")
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                    label = { Text("پورت") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(0.7f)
                )
            }

            Text(
                text = "پروتکل استاندارد جهانی",
                style = MaterialTheme.typography.titleMedium
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                VpnProtocol.entries.forEach { item ->
                    FilterChip(
                        selected = protocol == item,
                        onClick = {
                            protocol = item
                            cipher = item.defaultCipher
                            if (!isEdit) {
                                portText = item.defaultPort.toString()
                            }
                        },
                        label = { Text(item.label) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = dns,
                    onValueChange = { dns = it },
                    label = { Text("DNS (مثلاً 1.1.1.1)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = mtuText,
                    onValueChange = { mtuText = it.filter(Char::isDigit).take(4) },
                    label = { Text("MTU") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(0.7f)
                )
            }

            OutlinedTextField(
                value = publicKey,
                onValueChange = { publicKey = it },
                label = { Text("کلید عمومی / اثرانگشت رمزنگاری (اختیاری)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("سرور فعال باشد", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = active, onCheckedChange = { active = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("نشانه‌گذاری به‌عنوان پیشنهادی", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = recommended, onCheckedChange = { recommended = it })
            }

            if (!validationError.isNullOrBlank()) {
                Text(
                    text = validationError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Button(
                onClick = {
                    val port = portText.toLongOrNull() ?: 0L
                    val mtu = mtuText.toLongOrNull() ?: 1420L
                    when {
                        name.isBlank() -> validationError = "نام سرور را وارد کنید."
                        country.isBlank() -> validationError = "نام کشور را وارد کنید."
                        countryCode.length !in 2..4 -> validationError = "کد کشور باید ۲ تا ۴ حرف باشد."
                        city.isBlank() -> validationError = "نام شهر را وارد کنید."
                        host.trim().length < 3 -> validationError = "آدرس هاست یا IP معتبر نیست."
                        port !in 1L..65535L -> validationError = "پورت باید بین ۱ تا ۶۵۵۳۵ باشد."
                        mtu !in 1200L..1500L -> validationError = "MTU باید بین ۱۲۰۰ تا ۱۵۰۰ باشد."
                        else -> {
                            validationError = null
                            val payload = VpnServer(
                                id = initialServer?.id ?: "",
                                ownerId = initialServer?.ownerId?.ifBlank { ownerId } ?: ownerId,
                                name = name.trim(),
                                country = country.trim(),
                                countryCode = countryCode.trim().uppercase(),
                                city = city.trim(),
                                host = host.trim(),
                                port = port,
                                protocol = protocol.name,
                                cipher = cipher.ifBlank { protocol.defaultCipher },
                                dns = dns.trim().ifBlank { "1.1.1.1" },
                                tunnelAddress = tunnelAddress.trim().ifBlank { "10.8.0.2" },
                                mtu = mtu,
                                publicKey = publicKey.trim(),
                                region = region.trim().ifBlank { "Europe" },
                                loadPercent = initialServer?.loadPercent ?: 20L,
                                latencyMs = initialServer?.latencyMs ?: 45L,
                                active = active,
                                recommended = recommended,
                                createdAt = initialServer?.createdAt,
                                updatedAt = initialServer?.updatedAt
                            )
                            onSave(payload, isEdit)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_server_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(if (isEdit) "ذخیره تغییرات در Firebase" else "ثبت سرور در Firebase")
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
