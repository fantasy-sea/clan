package com.fantaysea.clan

import com.fantaysea.clan.media.WebRtcConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebRtcConfigTest {

    @Test
    fun `comma separated stun urls are trimmed and empty entries dropped`() {
        val specs = WebRtcConfig.parseIceServers(
            stunUrls = " stun:a.example.com:3478 , ,stun:b.example.com:3478 ",
            turnUrl = "",
            turnUsername = "",
            turnPassword = "",
        )

        assertEquals(1, specs.size)
        assertEquals(
            listOf("stun:a.example.com:3478", "stun:b.example.com:3478"),
            specs.single().urls,
        )
        assertEquals("", specs.single().username)
    }

    @Test
    fun `turn is enabled only when url username and password are all present`() {
        val complete = WebRtcConfig.parseIceServers(
            stunUrls = "",
            turnUrl = "turn:turn.example.com:3478",
            turnUsername = "clan",
            turnPassword = "secret",
        )
        assertEquals(1, complete.size)
        assertEquals(listOf("turn:turn.example.com:3478"), complete.single().urls)
        assertEquals("clan", complete.single().username)
        assertEquals("secret", complete.single().password)

        val missingPassword = WebRtcConfig.parseIceServers(
            stunUrls = "",
            turnUrl = "turn:turn.example.com:3478",
            turnUsername = "clan",
            turnPassword = "",
        )
        assertTrue(missingPassword.isEmpty())
    }

    @Test
    fun `empty configuration yields no ice servers`() {
        assertTrue(
            WebRtcConfig.parseIceServers(
                stunUrls = " ",
                turnUrl = "",
                turnUsername = "",
                turnPassword = "",
            ).isEmpty(),
        )
    }
}
