package com.example.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import com.example.data.model.SystemSettings
import com.example.data.model.VpnServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class VpnConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class VpnTunnelSnapshot(
    val state: VpnConnectionState = VpnConnectionState.DISCONNECTED,
    val activeServerId: String = "",
    val activeServerName: String = "",
    val activeCountry: String = "",
    val activeCountryCode: String = "",
    val activeHost: String = "",
    val activePort: Int = 51820,
    val activeProtocol: String = "WIREGUARD",
    val activeCipher: String = "ChaCha20-Poly1305",
    val localTunnelIp: String = "10.8.0.2",
    val dnsServer: String = "1.1.1.1",
    val fullTunnel: Boolean = false,
    val killSwitch: Boolean = true,
    val bypassedCount: Int = 0,
    val connectedSinceMs: Long = 0L,
    val bytesIn: Long = 0L,
    val bytesOut: Long = 0L,
    val packetsIn: Long = 0L,
    val packetsOut: Long = 0L,
    val downloadSpeedKbps: Float = 0f,
    val uploadSpeedKbps: Float = 0f,
    val speedHistoryKbps: List<Float> = emptyList(),
    val measuredLatencyMs: Long = 0L,
    val statusMessage: String = "آماده اتصال امن"
)

data class NetworkSecurityReport(
    val publicIp: String,
    val datacenter: String,
    val tlsVersion: String,
    val rttMs: Long,
    val dnsReachable: Boolean,
)

object VpnTunnelManager {
    private const val PREFS_NAME = "aegis_vpn_prefs"
    private const val KEY_SERVER_ID = "last_server_id"
    private const val KEY_SERVER_NAME = "last_server_name"
    private const val KEY_COUNTRY = "last_country"
    private const val KEY_COUNTRY_CODE = "last_country_code"
    private const val KEY_HOST = "last_host"
    private const val KEY_PORT = "last_port"
    private const val KEY_PROTOCOL = "last_protocol"
    private const val KEY_CIPHER = "last_cipher"
    private const val KEY_TUNNEL_IP = "last_tunnel_ip"
    private const val KEY_MTU = "last_mtu"
    private const val KEY_PRIMARY_DNS = "last_primary_dns"
    private const val KEY_SECONDARY_DNS = "last_secondary_dns"
    private const val KEY_DNS_PROTECTION = "last_dns_protection"
    private const val KEY_FULL_TUNNEL = "last_full_tunnel"
    private const val KEY_KILL_SWITCH = "last_kill_switch"
    private const val KEY_PERSISTENT_NOTIF = "last_persistent_notif"
    private const val KEY_AUTO_CONNECT = "last_auto_connect"
    private const val KEY_SYSTEM_ENABLED = "last_system_enabled"
    private const val KEY_LATENCY_MS = "last_latency_ms"

    private val _snapshot = MutableStateFlow(VpnTunnelSnapshot())
    val snapshot: StateFlow<VpnTunnelSnapshot> = _snapshot.asStateFlow()

    fun updateState(transform: (VpnTunnelSnapshot) -> VpnTunnelSnapshot) {
        _snapshot.update(transform)
    }

    fun prepareVpnIntent(context: Context): Intent? = VpnService.prepare(context)

    fun saveLastPolicy(context: Context, server: VpnServer?, settings: SystemSettings?) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (server != null) {
                putString(KEY_SERVER_ID, server.id)
                putString(KEY_SERVER_NAME, server.name)
                putString(KEY_COUNTRY, server.country)
                putString(KEY_COUNTRY_CODE, server.countryCode)
                putString(KEY_HOST, server.host)
                putInt(KEY_PORT, server.port.toInt())
                putString(KEY_PROTOCOL, server.protocol)
                putString(KEY_CIPHER, server.cipher)
                putString(KEY_TUNNEL_IP, server.tunnelAddress)
                putInt(KEY_MTU, server.mtu.toInt())
                putLong(KEY_LATENCY_MS, server.latencyMs)
            }
            if (settings != null) {
                putString(KEY_PRIMARY_DNS, settings.primaryDns)
                putString(KEY_SECONDARY_DNS, settings.secondaryDns)
                putBoolean(KEY_DNS_PROTECTION, settings.dnsProtection)
                putBoolean(KEY_FULL_TUNNEL, settings.fullTunnel)
                putBoolean(KEY_KILL_SWITCH, settings.killSwitch)
                putBoolean(KEY_PERSISTENT_NOTIF, settings.persistentNotification)
                putBoolean(KEY_AUTO_CONNECT, settings.autoConnect)
                putBoolean(KEY_SYSTEM_ENABLED, settings.systemEnabled)
            }
            apply()
        }
    }

    fun canStartFromBackground(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val systemEnabled = prefs.getBoolean(KEY_SYSTEM_ENABLED, true)
        val host = prefs.getString(KEY_HOST, null)
        return systemEnabled && !host.isNullOrBlank() && VpnService.prepare(context) == null
    }

    fun isAutoConnectEnabled(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_CONNECT, false)
    }

    fun startSavedTunnelFromBackground(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_SYSTEM_ENABLED, true)) return
        val server = VpnServer(
            id = prefs.getString(KEY_SERVER_ID, "srv_de_fra") ?: "srv_de_fra",
            name = prefs.getString(KEY_SERVER_NAME, "Frankfurt Core-01") ?: "Frankfurt Core-01",
            country = prefs.getString(KEY_COUNTRY, "آلمان") ?: "آلمان",
            countryCode = prefs.getString(KEY_COUNTRY_CODE, "DE") ?: "DE",
            host = prefs.getString(KEY_HOST, "1.1.1.1") ?: "1.1.1.1",
            port = prefs.getInt(KEY_PORT, 51820).toLong(),
            protocol = prefs.getString(KEY_PROTOCOL, "WIREGUARD") ?: "WIREGUARD",
            cipher = prefs.getString(KEY_CIPHER, "ChaCha20-Poly1305") ?: "ChaCha20-Poly1305",
            tunnelAddress = prefs.getString(KEY_TUNNEL_IP, "10.8.0.2") ?: "10.8.0.2",
            mtu = prefs.getInt(KEY_MTU, 1420).toLong(),
            latencyMs = prefs.getLong(KEY_LATENCY_MS, 36L)
        )
        val settings = SystemSettings(
            systemEnabled = prefs.getBoolean(KEY_SYSTEM_ENABLED, true),
            autoConnect = prefs.getBoolean(KEY_AUTO_CONNECT, false),
            killSwitch = prefs.getBoolean(KEY_KILL_SWITCH, true),
            dnsProtection = prefs.getBoolean(KEY_DNS_PROTECTION, true),
            fullTunnel = prefs.getBoolean(KEY_FULL_TUNNEL, false),
            persistentNotification = prefs.getBoolean(KEY_PERSISTENT_NOTIF, true),
            primaryDns = prefs.getString(KEY_PRIMARY_DNS, "1.1.1.1") ?: "1.1.1.1",
            secondaryDns = prefs.getString(KEY_SECONDARY_DNS, "9.9.9.9") ?: "9.9.9.9"
        )
        startTunnel(context, server, settings)
    }

    fun startTunnel(context: Context, server: VpnServer, settings: SystemSettings?) {
        saveLastPolicy(context, server, settings)
        val intent = Intent(context, AegisVpnService::class.java).apply {
            action = AegisVpnService.ACTION_CONNECT
            putExtra(AegisVpnService.EXTRA_SERVER_ID, server.id)
            putExtra(AegisVpnService.EXTRA_SERVER_NAME, server.name)
            putExtra(AegisVpnService.EXTRA_COUNTRY, server.country)
            putExtra(AegisVpnService.EXTRA_COUNTRY_CODE, server.countryCode)
            putExtra(AegisVpnService.EXTRA_HOST, server.host)
            putExtra(AegisVpnService.EXTRA_PORT, server.port.toInt())
            putExtra(AegisVpnService.EXTRA_PROTOCOL, server.protocol)
            putExtra(AegisVpnService.EXTRA_CIPHER, server.cipher)
            putExtra(AegisVpnService.EXTRA_TUNNEL_IP, server.tunnelAddress)
            putExtra(AegisVpnService.EXTRA_MTU, server.mtu.toInt())
            putExtra(AegisVpnService.EXTRA_PRIMARY_DNS, settings?.primaryDns?.ifBlank { server.dns } ?: server.dns)
            putExtra(AegisVpnService.EXTRA_SECONDARY_DNS, settings?.secondaryDns ?: "9.9.9.9")
            putExtra(AegisVpnService.EXTRA_DNS_PROTECTION, settings?.dnsProtection ?: true)
            putExtra(AegisVpnService.EXTRA_FULL_TUNNEL, settings?.fullTunnel ?: false)
            putExtra(AegisVpnService.EXTRA_KILL_SWITCH, settings?.killSwitch ?: true)
            putExtra(AegisVpnService.EXTRA_PERSISTENT_NOTIFICATION, settings?.persistentNotification ?: true)
            putExtra(AegisVpnService.EXTRA_LATENCY_MS, server.latencyMs)
            putStringArrayListExtra(
                AegisVpnService.EXTRA_BYPASSED_PACKAGES,
                ArrayList(settings?.bypassedPackages.orEmpty())
            )
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopTunnel(context: Context) {
        val intent = Intent(context, AegisVpnService::class.java).apply {
            action = AegisVpnService.ACTION_DISCONNECT
        }
        context.startService(intent)
    }
}
