package com.bettercaper.api

import com.bettercaper.api.model.SessionResponse
import com.google.gson.Gson
import org.junit.Test
import org.junit.Assert.*

class SessionParsingTest {

    private val gson = Gson()

    @Test
    fun parseSessionResponse_parsesCorrectly() {
        val json = """
            {
                "interface": {
                    "name": "wlan0",
                    "mac": "AA:BB:CC:DD:EE:FF",
                    "ipv4": "192.168.1.100"
                },
                "gateway": {
                    "ipv4": "192.168.1.1",
                    "mac": "11:22:33:44:55:66"
                },
                "modules": [
                    {
                        "name": "arp.spoof",
                        "running": true,
                        "description": "ARP Spoofing"
                    }
                ],
                "lan": {
                    "hosts": [
                        {
                            "ipv4": "192.168.1.50",
                            "mac": "00:11:22:33:44:55"
                        }
                    ]
                }
            }
        """.trimIndent()

        val response = gson.fromJson(json, SessionResponse::class.java)

        assertNotNull(response)
        assertEquals("wlan0", response.iface?.name)
        assertEquals("192.168.1.100", response.iface?.ipv4)
        assertEquals("192.168.1.1", response.gateway?.ipv4)
        
        val modules = response.modules
        assertNotNull(modules)
        assertEquals(1, modules?.size)
        assertEquals("arp.spoof", modules?.first()?.name)
        assertTrue(modules?.first()?.running == true)

        val lan = response.lan
        assertNotNull(lan)
        assertEquals(1, lan?.hosts?.size)
        assertEquals("192.168.1.50", lan?.hosts?.first()?.ipv4)
    }
}
