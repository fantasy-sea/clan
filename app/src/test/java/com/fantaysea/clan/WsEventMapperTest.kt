package com.fantaysea.clan

import com.fantaysea.clan.data.repository.RemoteEvent
import com.fantaysea.clan.data.ws.WsEventMapper
import com.fantaysea.clan.data.ws.WsFrame
import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
class WsEventMapperTest {

    @Test
    fun `pairing request maps requester summary`() {
        val event = WsEventMapper.map(
            frame(
                type = "PAIRING_REQUEST",
                requestId = "pairreq_1",
                payload = buildJsonObject {
                    put("expiresAt", "2026-01-01T00:01:00Z")
                    putJsonObject("requester") {
                        put("deviceId", "dev_requester")
                        put("deviceName", "妈妈的手机")
                        put("platform", "ANDROID")
                        put("model", "Pixel 8")
                        put("androidVersion", "16")
                    }
                },
            ),
        ) as RemoteEvent.MatchRequest

        assertEquals("pairreq_1", event.requestId)
        assertEquals("dev_requester", event.requester.id)
        assertEquals("妈妈的手机", event.requester.deviceName)
        assertEquals("Pixel 8", event.requester.model)
    }

    @Test
    fun `accepted result does not pretend to carry a device`() {
        val event = WsEventMapper.map(
            frame("PAIRING_RESULT", "pairreq_1", buildJsonObject { put("result", "ACCEPTED") }),
        ) as RemoteEvent.MatchResult

        assertTrue(event.accepted)
        assertNull(event.pairedDevice)
        assertEquals(false, event.expired)
    }

    @Test
    fun `rejected and expired results preserve the reason`() {
        val rejected = WsEventMapper.map(
            frame("PAIRING_RESULT", "pairreq_1", buildJsonObject { put("result", "REJECTED") }),
        ) as RemoteEvent.MatchResult
        val expired = WsEventMapper.map(
            frame("PAIRING_RESULT", "pairreq_2", buildJsonObject { put("result", "EXPIRED") }),
        ) as RemoteEvent.MatchResult

        assertEquals(false, rejected.accepted)
        assertEquals(false, rejected.expired)
        assertEquals(false, expired.accepted)
        assertEquals(true, expired.expired)
    }

    @Test
    fun `pairing succeeded maps the peer device`() {
        val event = WsEventMapper.map(
            frame(
                type = "PAIRING_SUCCEEDED",
                requestId = "pairreq_1",
                payload = buildJsonObject {
                    putJsonObject("device") {
                        put("deviceId", "dev_peer")
                        put("deviceName", "爸爸的手机")
                        put("platform", "ANDROID")
                        put("model", "Pixel 8")
                    }
                },
            ),
        ) as RemoteEvent.PairingSucceeded

        assertEquals("dev_peer", event.pairedDevice.id)
        assertEquals("爸爸的手机", event.pairedDevice.deviceName)
    }

    @Test
    fun `auth success becomes realtime ready`() {
        assertEquals(RemoteEvent.RealtimeReady, WsEventMapper.map(WsFrame(type = "AUTH_SUCCESS")))
    }

    @Test
    fun `device presence maps online status and last seen time`() {
        val online = WsEventMapper.map(
            frame(
                type = "DEVICE_ONLINE",
                payload = buildJsonObject {
                    put("deviceId", "dev_peer")
                    put("lastSeenAt", "2026-09-13T02:03:04Z")
                },
            ),
        ) as RemoteEvent.DevicePresence

        assertEquals("dev_peer", online.deviceId)
        assertTrue(online.online)
        assertEquals(Instant.parse("2026-09-13T02:03:04Z").toEpochMilli(), online.lastSeenAt)

        val offline = WsEventMapper.map(
            frame(
                type = "DEVICE_OFFLINE",
                payload = buildJsonObject {
                    put("deviceId", "dev_peer")
                    put("lastSeenAt", "2026-09-13T02:04:05Z")
                },
            ),
        ) as RemoteEvent.DevicePresence

        assertEquals("dev_peer", offline.deviceId)
        assertFalse(offline.online)
        assertEquals(Instant.parse("2026-09-13T02:04:05Z").toEpochMilli(), offline.lastSeenAt)
    }

    @Test
    fun `presence keeps online state when last seen is malformed`() {
        val offline = WsEventMapper.map(
            frame(
                type = "DEVICE_OFFLINE",
                payload = buildJsonObject {
                    put("deviceId", "dev_peer")
                    put("lastSeenAt", "not-a-time")
                },
            ),
        ) as RemoteEvent.DevicePresence

        assertFalse(offline.online)
        assertNull(offline.lastSeenAt)
    }

    @Test
    fun `unknown and malformed frames are ignored`() {
        assertNull(WsEventMapper.map(WsFrame(type = "UNKNOWN_EVENT")))
        assertNull(WsEventMapper.map(WsFrame(type = "DEVICE_ONLINE")))
        assertNull(
            WsEventMapper.map(
                frame(
                    type = "DEVICE_OFFLINE",
                    payload = buildJsonObject { put("lastSeenAt", "2026-09-13T02:03:04Z") },
                ),
            ),
        )
        assertNull(WsEventMapper.map(WsFrame(type = "PAIRING_REQUEST", requestId = "pairreq_1")))
        assertNull(WsEventMapper.map(WsFrame(type = "PAIRING_RESULT", requestId = "pairreq_1", payload = buildJsonObject { })))
    }

    @Test
    fun `control request uses session id and controller summary`() {
        val event = WsEventMapper.map(
            frame(
                type = "CONTROL_REQUEST",
                sessionId = "session_1",
                payload = buildJsonObject {
                    put("expiresAt", "2026-01-01T00:01:00Z")
                    putJsonObject("controller") {
                        put("deviceId", "dev_controller")
                        put("deviceName", "爸爸的手机")
                        put("platform", "ANDROID")
                        put("model", "Pixel 8")
                    }
                },
            ),
        ) as RemoteEvent.ControlRequest

        assertEquals("session_1", event.sessionId)
        assertEquals("dev_controller", event.controller.id)
        assertEquals(Instant.parse("2026-01-01T00:01:00Z").toEpochMilli(), event.expiresAt)
    }

    @Test
    fun `session lifecycle events map by session id`() {
        val started = WsEventMapper.map(
            frame(
                type = "SESSION_STARTED",
                sessionId = "session_1",
                payload = buildJsonObject { put("connectExpiresAt", "2026-01-01T00:02:00Z") },
            ),
        ) as RemoteEvent.SessionStarted
        assertEquals("session_1", started.sessionId)
        assertEquals(Instant.parse("2026-01-01T00:02:00Z").toEpochMilli(), started.connectExpiresAt)

        val connected = WsEventMapper.map(
            frame(
                type = "SESSION_CONNECTED",
                sessionId = "session_1",
                payload = buildJsonObject { put("connectedAt", "2026-01-01T00:03:00Z") },
            ),
        ) as RemoteEvent.SessionConnected
        assertEquals("session_1", connected.sessionId)
        assertEquals(Instant.parse("2026-01-01T00:03:00Z").toEpochMilli(), connected.connectedAt)

        val ended = WsEventMapper.map(
            frame("SESSION_ENDED", sessionId = "session_1", payload = buildJsonObject { put("reason", "ENDED_BY_DEVICE") }),
        ) as RemoteEvent.SessionEnded
        assertEquals(false, ended.failed)
        assertEquals("ENDED_BY_DEVICE", ended.reason)

        val failed = WsEventMapper.map(
            frame("SESSION_FAILED", sessionId = "session_1", payload = buildJsonObject { put("reason", "DEVICE_OFFLINE") }),
        ) as RemoteEvent.SessionEnded
        assertEquals(true, failed.failed)
        assertEquals("DEVICE_OFFLINE", failed.reason)
    }

    @Test
    fun `session request result keeps accepted rejected and expired`() {
        val accepted = WsEventMapper.map(
            frame(
                type = "CONTROL_RESULT",
                sessionId = "session_1",
                payload = buildJsonObject {
                    put("scope", "SESSION_REQUEST")
                    put("result", "ACCEPTED")
                },
            ),
        ) as RemoteEvent.ControlResult
        assertEquals(RemoteEvent.SessionRequestResult.ACCEPTED, accepted.result)

        val rejected = WsEventMapper.map(
            frame(
                type = "CONTROL_RESULT",
                sessionId = "session_1",
                payload = buildJsonObject {
                    put("scope", "SESSION_REQUEST")
                    put("result", "REJECTED")
                },
            ),
        ) as RemoteEvent.ControlResult
        assertEquals(RemoteEvent.SessionRequestResult.REJECTED, rejected.result)
    }

    @Test
    fun `control command and command result map`() {
        val command = WsEventMapper.map(
            frame(
                type = "CONTROL",
                requestId = "ctl_1",
                sessionId = "session_1",
                payload = buildJsonObject {
                    put("action", "TAP")
                    put("x", 0.5)
                    put("y", 0.25)
                },
            ),
        ) as RemoteEvent.ControlCommandEvent
        assertEquals("ctl_1", command.requestId)
        assertEquals("session_1", command.sessionId)
        assertEquals(com.fantaysea.clan.data.model.ControlCommand.Tap(0.5, 0.25), command.command)

        val result = WsEventMapper.map(
            frame(
                type = "CONTROL_RESULT",
                requestId = "ctl_1",
                sessionId = "session_1",
                payload = buildJsonObject {
                    put("scope", "COMMAND")
                    put("result", "FAILED")
                    put("reason", "ACCESSIBILITY_DENIED")
                },
            ),
        ) as RemoteEvent.ControlCommandResult
        assertEquals(RemoteEvent.ControlResultStatus.FAILED, result.result)
        assertEquals("ACCESSIBILITY_DENIED", result.reason)
    }

    @Test
    fun `permission status and error frames map`() {
        val permission = WsEventMapper.map(
            frame(
                type = "PERMISSION_STATUS",
                sessionId = "session_1",
                payload = buildJsonObject {
                    put("screenCapture", true)
                    put("accessibility", false)
                },
            ),
        ) as RemoteEvent.PermissionStatus
        assertEquals(true, permission.screenCapture)
        assertEquals(false, permission.accessibility)

        val error = WsEventMapper.map(
            frame(
                type = "ERROR",
                requestId = "ctl_1",
                sessionId = "session_1",
                payload = buildJsonObject {
                    put("code", "CONTROL_NOT_ALLOWED")
                    put("message", "Control not allowed")
                    put("retryAfter", 3)
                },
            ),
        ) as RemoteEvent.SessionError
        assertEquals("CONTROL_NOT_ALLOWED", error.code)
        assertEquals(3L, error.retryAfter)
    }

    @Test
    fun `webrtc signaling frames map`() {
        val offer = WsEventMapper.map(
            frame(
                type = "WEBRTC_OFFER",
                requestId = "sig_1",
                sessionId = "session_1",
                payload = buildJsonObject { put("sdp", "v=0-offer") },
            ),
        ) as RemoteEvent.WebRtcOffer
        assertEquals("v=0-offer", offer.sdp)

        val ice = WsEventMapper.map(
            frame(
                type = "ICE_CANDIDATE",
                sessionId = "session_1",
                payload = buildJsonObject {
                    put("candidate", "candidate:1")
                    put("sdpMid", "0")
                    put("sdpMLineIndex", 0)
                },
            ),
        ) as RemoteEvent.IceCandidateEvent
        assertEquals("candidate:1", ice.candidate.candidate)
        assertEquals(0, ice.candidate.sdpMLineIndex)
    }

    private fun frame(
        type: String,
        requestId: String? = null,
        payload: JsonObject? = null,
        sessionId: String? = null,
    ) = WsFrame(type = type, requestId = requestId, sessionId = sessionId, payload = payload)
}
