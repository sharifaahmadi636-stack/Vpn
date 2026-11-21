package com.example

import com.example.data.model.SystemSettings
import com.example.data.model.VpnProtocol
import com.example.data.model.VpnServer
import com.example.util.YamlConfigCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YamlConfigCodecTest {

    @Test
    fun `export and parse yaml round trip preserves server fields`() {
        val sampleServers = listOf(
            VpnServer(
                name = "Frankfurt Core-01",
                country = "آلمان",
                countryCode = "DE",
                city = "Frankfurt",
                region = "Europe",
                host = "1.1.1.1",
                port = 51820L,
                protocol = VpnProtocol.WIREGUARD.name,
                cipher = "ChaCha20-Poly1305",
                dns = "1.1.1.1",
                tunnelAddress = "10.8.0.2",
                mtu = 1420L,
                publicKey = "abc123key",
                active = true,
                recommended = true
            )
        )
        val settings = SystemSettings(
            ownerId = "owner_1",
            bypassedPackages = listOf("com.android.vending")
        )

        val yaml = YamlConfigCodec.exportToYaml(sampleServers, settings)
        assertTrue(yaml.contains("servers:"))
        assertTrue(yaml.contains("bypassedPackages:"))
        assertTrue(yaml.contains("com.android.vending"))

        val parsed = YamlConfigCodec.parseServersFromYaml(yaml, "owner_1")
        assertEquals(1, parsed.size)
        assertEquals("Frankfurt Core-01", parsed[0].name)
        assertEquals("DE", parsed[0].countryCode)
        assertEquals("1.1.1.1", parsed[0].host)
        assertEquals(51820L, parsed[0].port)
        assertEquals(VpnProtocol.WIREGUARD.name, parsed[0].protocol)
        assertEquals(1420L, parsed[0].mtu)
        assertTrue(parsed[0].active)
        assertTrue(parsed[0].recommended)
    }
}
