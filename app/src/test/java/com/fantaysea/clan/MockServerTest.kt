package com.fantaysea.clan

import com.fantaysea.clan.data.mock.MockServer
import com.fantaysea.clan.data.repository.DeviceOfflineException
import com.fantaysea.clan.data.repository.DeviceRegistration
import com.fantaysea.clan.data.repository.InvalidPairingCodeException
import com.fantaysea.clan.data.repository.RemoteEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MockServerTest {

    private val registration = DeviceRegistration("Pixel 7", "Pixel 7", "14")

    @Test
    fun `invalid code throws`() {
        runBlocking {
            val server = MockServer(delayMillis = 0)
            assertThrows(InvalidPairingCodeException::class.java) {
                runBlocking { server.submitPairingCode("abc12") }
            }
        }
    }

    @Test
    fun `unknown code throws`() {
        runBlocking {
            val server = MockServer(delayMillis = 0)
            server.initializeSelf(registration)
            server.createPairingCode()
            assertThrows(InvalidPairingCodeException::class.java) {
                runBlocking { server.submitPairingCode("999999") }
            }
        }
    }

    @Test
    fun `valid submit emits match request`() = runBlocking {
        val server = MockServer(delayMillis = 0)
        server.initializeSelf(registration)
        val pc = server.createPairingCode()
        val requestId = server.submitPairingCode(pc.code)

        assertTrue(requestId.isNotBlank())
        val match = withTimeout(2000) {
            server.events.first { it is RemoteEvent.MatchRequest && it.requestId == requestId }
        }
        assertTrue(match is RemoteEvent.MatchRequest)
    }

    @Test
    fun `accept match adds paired device to list`() = runBlocking {
        val server = MockServer(delayMillis = 0)
        server.initializeSelf(registration)
        val pc = server.createPairingCode()
        val requestId = server.submitPairingCode(pc.code)

        withTimeout(2000) { server.events.first { it is RemoteEvent.MatchRequest } }
        server.respondMatch(requestId, accept = true)

        val result = withTimeout(2000) {
            server.events.first { it is RemoteEvent.MatchResult && it.requestId == requestId }
        } as RemoteEvent.MatchResult
        assertTrue(result.accepted)
        withTimeout(2000) { server.events.first { it is RemoteEvent.PairingSucceeded } }

        // 种子 2 台 + 新配对 1 台
        val devices = server.fetchDevices()
        assertEquals(3, devices.size)
        assertTrue(devices.any { it.device.deviceName == "家人的手机" && it.online })
    }

    @Test
    fun `reject match does not add device`() = runBlocking {
        val server = MockServer(delayMillis = 0)
        server.initializeSelf(registration)
        val pc = server.createPairingCode()
        val requestId = server.submitPairingCode(pc.code)

        withTimeout(2000) { server.events.first { it is RemoteEvent.MatchRequest } }
        server.respondMatch(requestId, accept = false)

        val result = withTimeout(2000) {
            server.events.first { it is RemoteEvent.MatchResult && it.requestId == requestId }
        } as RemoteEvent.MatchResult
        assertTrue(!result.accepted)
        assertEquals(2, server.fetchDevices().size)
    }

    @Test
    fun `offline device control throws`() {
        runBlocking {
            val server = MockServer(delayMillis = 0)
            server.initializeSelf(registration)
            assertThrows(DeviceOfflineException::class.java) {
                runBlocking { server.sendControlRequest("dev_mock_dad") } // 爸爸的手机(离线)
            }
        }
    }

    @Test
    fun `control accept emits session started`() = runBlocking {
        val server = MockServer(delayMillis = 0)
        server.initializeSelf(registration)
        val requestId = server.sendControlRequest("dev_mock_mom") // 妈妈的手机(在线)

        withTimeout(2000) { server.events.first { it is RemoteEvent.ControlRequest } }
        server.respondControl(requestId, accept = true)

        val result = withTimeout(2000) {
            server.events.first { it is RemoteEvent.ControlResult && it.requestId == requestId }
        } as RemoteEvent.ControlResult
        assertTrue(result.accepted)
        val session = withTimeout(2000) {
            server.events.first { it is RemoteEvent.SessionStarted }
        } as RemoteEvent.SessionStarted
        assertEquals("dev_mock_mom", session.controlled.id)
    }

    @Test
    fun `unbind removes device`() = runBlocking {
        val server = MockServer(delayMillis = 0)
        server.initializeSelf(registration)
        server.unbindDevice("dev_mock_mom")
        val devices = server.fetchDevices()
        assertEquals(1, devices.size)
        assertEquals("爸爸的手机", devices.first().device.deviceName)
    }

    @Test
    fun `findDevice returns paired device`() = runBlocking {
        val server = MockServer(delayMillis = 0)
        val device = server.findDevice("dev_mock_mom") // 妈妈的手机(种子设备)
        assertEquals("dev_mock_mom", device?.id)
        assertEquals("妈妈的手机", device?.deviceName)
    }

    @Test
    fun `findDevice returns self device`() = runBlocking {
        val server = MockServer(delayMillis = 0)
        server.initializeSelf(registration)
        val device = server.findDevice(MockServer.SELF_ID)
        assertEquals(MockServer.SELF_ID, device?.id)
        assertEquals("Pixel 7", device?.deviceName)
    }

    @Test
    fun `findDevice returns null for unknown or unbound device`() = runBlocking {
        val server = MockServer(delayMillis = 0)
        assertEquals(null, server.findDevice("dev_missing"))
        server.unbindDevice("dev_mock_mom")
        assertEquals(null, server.findDevice("dev_mock_mom"))
    }
}
