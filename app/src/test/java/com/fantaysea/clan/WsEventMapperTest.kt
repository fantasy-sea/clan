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

    private fun frame(
        type: String,
        requestId: String? = null,
        payload: JsonObject? = null,
    ) = WsFrame(type = type, requestId = requestId, payload = payload)
}
