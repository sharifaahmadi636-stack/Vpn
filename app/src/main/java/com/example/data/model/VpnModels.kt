package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class VpnProtocol(val label: String, val defaultCipher: String, val defaultPort: Int) {
    WIREGUARD("WireGuard", "ChaCha20-Poly1305", 51820),
    IKEV2("IKEv2/IPSec", "AES-256-GCM", 500),
    OPENVPN("OpenVPN", "AES-256-GCM", 1194),
    VLESS_TLS("VLESS TLS", "TLS 1.3 / XTLS", 443);

    companion object {
        fun fromRaw(raw: String?): VpnProtocol =
            entries.firstOrNull { it.name == raw } ?: WIREGUARD
    }
}

enum class PreferredProtocol(val label: String) {
    AUTO("خودکار (هوشمند)"),
    WIREGUARD("WireGuard"),
    IKEV2("IKEv2/IPSec"),
    OPENVPN("OpenVPN"),
    VLESS_TLS("VLESS TLS");

    companion object {
        fun fromRaw(raw: String?): PreferredProtocol =
            entries.firstOrNull { it.name == raw } ?: WIREGUARD
    }
}

enum class LogEventType(val labelFa: String) {
    CONNECTED("اتصال برقرار شد"),
    DISCONNECTED("قطع اتصال"),
    SYSTEM_UPDATED("تغییر تنظیمات مالک"),
    SERVER_ADDED("افزودن سرور جدید"),
    SERVER_TOGGLED("تغییر وضعیت سرور"),
    ERROR("خطای تونل");

    companion object {
        fun fromRaw(raw: String?): LogEventType =
            entries.firstOrNull { it.name == raw } ?: SYSTEM_UPDATED
    }
}

@Keep
@IgnoreExtraProperties
data class VpnServer(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val name: String = "",
    val country: String = "",
    val countryCode: String = "",
    val city: String = "",
    val host: String = "",
    val port: Long = 51820L,
    val protocol: String = VpnProtocol.WIREGUARD.name,
    val cipher: String = "ChaCha20-Poly1305",
    val dns: String = "1.1.1.1",
    val tunnelAddress: String = "10.8.0.2",
    val mtu: Long = 1420L,
    val publicKey: String = "",
    val region: String = "Europe",
    val loadPercent: Long = 20L,
    val latencyMs: Long = 45L,
    val active: Boolean = true,
    val recommended: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val protocolEnum: VpnProtocol
        get() = VpnProtocol.fromRaw(protocol)

    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "ownerId" to uid,
        "name" to name.trim().take(60),
        "country" to country.trim().take(40),
        "countryCode" to countryCode.trim().uppercase().take(4),
        "city" to city.trim().take(40),
        "host" to host.trim().take(120),
        "port" to port.coerceIn(1L, 65535L),
        "protocol" to VpnProtocol.fromRaw(protocol).name,
        "cipher" to cipher.trim().take(40),
        "dns" to dns.trim().take(64),
        "tunnelAddress" to tunnelAddress.trim().take(32),
        "mtu" to mtu.coerceIn(1200L, 1500L),
        "publicKey" to publicKey.trim().take(128),
        "region" to region.trim().take(30),
        "loadPercent" to loadPercent.coerceIn(0L, 100L),
        "latencyMs" to latencyMs.coerceIn(0L, 10000L),
        "active" to active,
        "recommended" to recommended,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "name" to name.trim().take(60),
        "country" to country.trim().take(40),
        "countryCode" to countryCode.trim().uppercase().take(4),
        "city" to city.trim().take(40),
        "host" to host.trim().take(120),
        "port" to port.coerceIn(1L, 65535L),
        "protocol" to VpnProtocol.fromRaw(protocol).name,
        "cipher" to cipher.trim().take(40),
        "dns" to dns.trim().take(64),
        "tunnelAddress" to tunnelAddress.trim().take(32),
        "mtu" to mtu.coerceIn(1200L, 1500L),
        "publicKey" to publicKey.trim().take(128),
        "region" to region.trim().take(30),
        "loadPercent" to loadPercent.coerceIn(0L, 100L),
        "latencyMs" to latencyMs.coerceIn(0L, 10000L),
        "active" to active,
        "recommended" to recommended,
        "updatedAt" to FieldValue.serverTimestamp(),
    )
}

@Keep
@IgnoreExtraProperties
data class SystemSettings(
    @DocumentId val id: String = "global",
    val ownerId: String = "",
    val systemEnabled: Boolean = true,
    val autoConnect: Boolean = false,
    val killSwitch: Boolean = true,
    val dnsProtection: Boolean = true,
    val fullTunnel: Boolean = false,
    val persistentNotification: Boolean = true,
    val autoFailover: Boolean = true,
    val bypassedPackages: List<String> = emptyList(),
    val selectedServerId: String = "",
    val preferredProtocol: String = PreferredProtocol.WIREGUARD.name,
    val primaryDns: String = "1.1.1.1",
    val secondaryDns: String = "9.9.9.9",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    val preferredProtocolEnum: PreferredProtocol
        get() = PreferredProtocol.fromRaw(preferredProtocol)

    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "ownerId" to uid,
        "systemEnabled" to systemEnabled,
        "autoConnect" to autoConnect,
        "killSwitch" to killSwitch,
        "dnsProtection" to dnsProtection,
        "fullTunnel" to fullTunnel,
        "persistentNotification" to persistentNotification,
        "autoFailover" to autoFailover,
        "bypassedPackages" to bypassedPackages.distinct().take(50),
        "selectedServerId" to selectedServerId.trim().take(128),
        "preferredProtocol" to PreferredProtocol.fromRaw(preferredProtocol).name,
        "primaryDns" to primaryDns.trim().take(64),
        "secondaryDns" to secondaryDns.trim().take(64),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "systemEnabled" to systemEnabled,
        "autoConnect" to autoConnect,
        "killSwitch" to killSwitch,
        "dnsProtection" to dnsProtection,
        "fullTunnel" to fullTunnel,
        "persistentNotification" to persistentNotification,
        "autoFailover" to autoFailover,
        "bypassedPackages" to bypassedPackages.distinct().take(50),
        "selectedServerId" to selectedServerId.trim().take(128),
        "preferredProtocol" to PreferredProtocol.fromRaw(preferredProtocol).name,
        "primaryDns" to primaryDns.trim().take(64),
        "secondaryDns" to secondaryDns.trim().take(64),
        "updatedAt" to FieldValue.serverTimestamp(),
    )
}

@Keep
@IgnoreExtraProperties
data class ConnectionLog(
    @DocumentId val id: String = "",
    val ownerId: String = "",
    val serverId: String = "",
    val serverName: String = "",
    val countryCode: String = "",
    val protocol: String = "",
    val eventType: String = LogEventType.CONNECTED.name,
    val durationSec: Long = 0L,
    val bytesTransferred: Long = 0L,
    val latencyMs: Long = 0L,
    val note: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
) {
    val eventTypeEnum: LogEventType
        get() = LogEventType.fromRaw(eventType)

    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "ownerId" to uid,
        "serverId" to serverId.trim().ifBlank { "system" }.take(128),
        "serverName" to serverName.trim().ifBlank { "Aegis System" }.take(80),
        "countryCode" to countryCode.trim().uppercase().ifBlank { "GL" }.take(4),
        "protocol" to protocol.trim().ifBlank { "WIREGUARD" }.take(24),
        "eventType" to LogEventType.fromRaw(eventType).name,
        "durationSec" to durationSec.coerceIn(0L, 8640000L),
        "bytesTransferred" to bytesTransferred.coerceAtLeast(0L),
        "latencyMs" to latencyMs.coerceIn(0L, 10000L),
        "note" to note.trim().take(240),
        "createdAt" to FieldValue.serverTimestamp(),
    )
}
