package com.fantaysea.clan

import com.fantaysea.clan.data.ws.WsProtocol
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WsProtocolTest {

    @Test
    fun `derives ws and wss root url from api base url`() {
        assertEquals("ws://192.168.1.2:8080/ws", WsProtocol.toWebSocketUrl("http://192.168.1.2:8080/"))
        assertEquals("wss://clan.example.com/ws", WsProtocol.toWebSocketUrl("https://clan.example.com/api/"))
        assertEquals("ws://localhost:1234/ws", WsProtocol.toWebSocketUrl("http://localhost:1234/base/"))
    }

    @Test
    fun `auth message carries token only inside payload`() {
        val frame = WsProtocol.parse(WsProtocol.authMessage("secret-token", "auth-1"))!!

        assertEquals("AUTH", frame.type)
        assertEquals("auth-1", frame.requestId)
        assertEquals("secret-token", frame.payload!!["deviceToken"]!!.jsonPrimitive.content)
    }

    @Test
    fun `ping message has no token`() {
        val frame = WsProtocol.parse(WsProtocol.pingMessage("ping-1"))!!

        assertEquals("PING", frame.type)
        assertEquals("ping-1", frame.requestId)
        assertNull(frame.payload!!["deviceToken"])
    }

    @Test
    fun `malformed or incomplete frames are ignored`() {
        assertNull(WsProtocol.parse("{not-json"))
        assertNull(WsProtocol.parse("""{"payload":{}}"""))
    }
}
