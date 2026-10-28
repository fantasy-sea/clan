package com.fantaysea.clan

import com.fantaysea.clan.data.model.ControlCommand
import com.fantaysea.clan.data.model.IceCandidate
import com.fantaysea.clan.data.ws.WsProtocol
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
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

    @Test
    fun `control message carries session request id and normalized payload`() {
        val frame = WsProtocol.parse(
            WsProtocol.controlMessage("session_1", "ctl_1", ControlCommand.Tap(0.5, 0.25)),
        )!!

        assertEquals("CONTROL", frame.type)
        assertEquals("ctl_1", frame.requestId)
        assertEquals("session_1", frame.sessionId)
        assertEquals("TAP", frame.payload!!["action"]!!.jsonPrimitive.content)
        assertEquals(0.5, frame.payload!!["x"]!!.jsonPrimitive.double, 0.0)
        assertEquals(0.25, frame.payload!!["y"]!!.jsonPrimitive.double, 0.0)
    }

    @Test
    fun `swipe message carries duration and endpoints`() {
        val frame = WsProtocol.parse(
            WsProtocol.controlMessage(
                "session_1",
                "ctl_2",
                ControlCommand.Swipe(0.1, 0.2, 0.3, 0.4, duration = 500),
            ),
        )!!

        assertEquals("SWIPE", frame.payload!!["action"]!!.jsonPrimitive.content)
        assertEquals(0.1, frame.payload!!["startX"]!!.jsonPrimitive.double, 0.0)
        assertEquals(0.4, frame.payload!!["endY"]!!.jsonPrimitive.double, 0.0)
        assertEquals(500, frame.payload!!["duration"]!!.jsonPrimitive.int)
    }

    @Test
    fun `control result uses COMMAND scope`() {
        val frame = WsProtocol.parse(
            WsProtocol.controlResultMessage("session_1", "ctl_1", ok = false, reason = "ACCESSIBILITY_DENIED"),
        )!!

        assertEquals("CONTROL_RESULT", frame.type)
        assertEquals("session_1", frame.sessionId)
        assertEquals("COMMAND", frame.payload!!["scope"]!!.jsonPrimitive.content)
        assertEquals("FAILED", frame.payload!!["result"]!!.jsonPrimitive.content)
        assertEquals("ACCESSIBILITY_DENIED", frame.payload!!["reason"]!!.jsonPrimitive.content)
    }

    @Test
    fun `permission status and session connected messages are encoded`() {
        val permission = WsProtocol.parse(
            WsProtocol.permissionStatusMessage("session_1", screenCapture = true, accessibility = false),
        )!!
        assertEquals("PERMISSION_STATUS", permission.type)
        assertEquals(true, permission.payload!!["screenCapture"]!!.jsonPrimitive.boolean)
        assertEquals(false, permission.payload!!["accessibility"]!!.jsonPrimitive.boolean)

        val connected = WsProtocol.parse(WsProtocol.sessionConnectedMessage("session_1"))!!
        assertEquals("SESSION_CONNECTED", connected.type)
        assertEquals("session_1", connected.sessionId)
    }

    @Test
    fun `ice candidate omits absent optional fields`() {
        val frame = WsProtocol.parse(
            WsProtocol.iceCandidateMessage(
                "session_1",
                "ice_1",
                IceCandidate("candidate:1 1 UDP 1 192.0.2.1 5000 typ host", sdpMid = "0", sdpMLineIndex = 0),
            ),
        )!!

        assertEquals("ICE_CANDIDATE", frame.type)
        assertEquals("0", frame.payload!!["sdpMid"]!!.jsonPrimitive.content)
        assertEquals(0, frame.payload!!["sdpMLineIndex"]!!.jsonPrimitive.int)
        assertNull(frame.payload!!["usernameFragment"])
    }
}
