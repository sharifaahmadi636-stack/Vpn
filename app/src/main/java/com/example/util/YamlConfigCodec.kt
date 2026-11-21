package com.example.util

import com.example.data.model.SystemSettings
import com.example.data.model.VpnProtocol
import com.example.data.model.VpnServer

object YamlConfigCodec {

    fun exportToYaml(servers: List<VpnServer>, settings: SystemSettings?): String {
        val sb = StringBuilder()
        sb.appendLine("# Aegis VPN - Global Standard Configuration (.yml)")
        sb.appendLine("version: 1")
        sb.appendLine("profile: aegis-vpn-owner-export")
        sb.appendLine("settings:")
        val s = settings ?: SystemSettings()
        sb.appendLine("  systemEnabled: ${s.systemEnabled}")
        sb.appendLine("  autoConnect: ${s.autoConnect}")
        sb.appendLine("  autoFailover: ${s.autoFailover}")
        sb.appendLine("  killSwitch: ${s.killSwitch}")
        sb.appendLine("  dnsProtection: ${s.dnsProtection}")
        sb.appendLine("  fullTunnel: ${s.fullTunnel}")
        sb.appendLine("  persistentNotification: ${s.persistentNotification}")
        sb.appendLine("  preferredProtocol: ${s.preferredProtocol}")
        sb.appendLine("  primaryDns: ${s.primaryDns}")
        sb.appendLine("  secondaryDns: ${s.secondaryDns}")
        if (s.bypassedPackages.isNotEmpty()) {
            sb.appendLine("  bypassedPackages:")
            s.bypassedPackages.forEach { pkg ->
                sb.appendLine("    - ${escapeScalar(pkg)}")
            }
        }
        sb.appendLine()
        sb.appendLine("servers:")
        servers.forEach { server ->
            appendServerBlock(sb, server)
        }
        return sb.toString()
    }

    fun exportSingleServerToYaml(server: VpnServer): String {
        val sb = StringBuilder()
        sb.appendLine("# Aegis VPN Node (.yml)")
        sb.appendLine("version: 1")
        sb.appendLine("servers:")
        appendServerBlock(sb, server)
        return sb.toString()
    }

    private fun appendServerBlock(sb: StringBuilder, server: VpnServer) {
        sb.appendLine("  - name: ${escapeScalar(server.name)}")
        sb.appendLine("    country: ${escapeScalar(server.country)}")
        sb.appendLine("    countryCode: ${escapeScalar(server.countryCode)}")
        sb.appendLine("    city: ${escapeScalar(server.city)}")
        sb.appendLine("    region: ${escapeScalar(server.region)}")
        sb.appendLine("    host: ${escapeScalar(server.host)}")
        sb.appendLine("    port: ${server.port}")
        sb.appendLine("    protocol: ${server.protocol}")
        sb.appendLine("    cipher: ${escapeScalar(server.cipher)}")
        sb.appendLine("    dns: ${escapeScalar(server.dns)}")
        sb.appendLine("    tunnelAddress: ${escapeScalar(server.tunnelAddress)}")
        sb.appendLine("    mtu: ${server.mtu}")
        if (server.publicKey.isNotBlank()) {
            sb.appendLine("    publicKey: ${escapeScalar(server.publicKey)}")
        }
        sb.appendLine("    active: ${server.active}")
        sb.appendLine("    recommended: ${server.recommended}")
    }

    fun parseServersFromYaml(yamlText: String, ownerId: String): List<VpnServer> {
        val result = mutableListOf<VpnServer>()
        var inServersSection = false
        var currentMap: MutableMap<String, String>? = null

        fun flushCurrent() {
            val map = currentMap ?: return
            val name = map["name"]?.trim().orEmpty()
            val host = map["host"]?.trim().orEmpty()
            if (name.isNotBlank() && host.length >= 3) {
                val proto = VpnProtocol.fromRaw(map["protocol"]?.uppercase())
                val port = map["port"]?.toLongOrNull()?.coerceIn(1L, 65535L) ?: proto.defaultPort.toLong()
                val mtu = map["mtu"]?.toLongOrNull()?.coerceIn(1200L, 1500L) ?: 1420L
                result.add(
                    VpnServer(
                        ownerId = ownerId,
                        name = name.take(60),
                        country = map["country"]?.ifBlank { "Global" }?.take(40) ?: "Global",
                        countryCode = map["countryCode"]?.uppercase()?.ifBlank { "GL" }?.take(4) ?: "GL",
                        city = map["city"]?.ifBlank { "Edge" }?.take(40) ?: "Edge",
                        region = map["region"]?.ifBlank { "Europe" }?.take(30) ?: "Europe",
                        host = host.take(120),
                        port = port,
                        protocol = proto.name,
                        cipher = map["cipher"]?.ifBlank { proto.defaultCipher }?.take(40) ?: proto.defaultCipher,
                        dns = map["dns"]?.ifBlank { "1.1.1.1" }?.take(64) ?: "1.1.1.1",
                        tunnelAddress = map["tunnelAddress"]?.ifBlank { "10.8.0.2" }?.take(32) ?: "10.8.0.2",
                        mtu = mtu,
                        publicKey = map["publicKey"]?.take(128) ?: "",
                        loadPercent = 20L,
                        latencyMs = 45L,
                        active = map["active"]?.toBooleanStrictOrNull() ?: true,
                        recommended = map["recommended"]?.toBooleanStrictOrNull() ?: false
                    )
                )
            }
            currentMap = null
        }

        yamlText.lineSequence().forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach

            if (!rawLine.startsWith(" ") && !rawLine.startsWith("\t")) {
                if (trimmed == "servers:") {
                    inServersSection = true
                } else {
                    flushCurrent()
                    inServersSection = false
                }
                return@forEach
            }

            if (!inServersSection) return@forEach

            if (trimmed.startsWith("- ")) {
                flushCurrent()
                currentMap = mutableMapOf()
                val afterDash = trimmed.removePrefix("- ").trim()
                parseKeyValue(afterDash)?.let { (k, v) -> currentMap?.put(k, v) }
            } else {
                parseKeyValue(trimmed)?.let { (k, v) -> currentMap?.put(k, v) }
            }
        }
        flushCurrent()
        return result
    }

    private fun parseKeyValue(line: String): Pair<String, String>? {
        val idx = line.indexOf(':')
        if (idx <= 0) return null
        val key = line.substring(0, idx).trim()
        val rawVal = line.substring(idx + 1).trim()
        val unquoted = rawVal.removeSurrounding("\"").removeSurrounding("'")
        return key to unquoted
    }

    private fun escapeScalar(value: String): String {
        val clean = value.replace("\n", " ").trim()
        return if (clean.contains(":") || clean.contains("#")) "\"$clean\"" else clean
    }
}
