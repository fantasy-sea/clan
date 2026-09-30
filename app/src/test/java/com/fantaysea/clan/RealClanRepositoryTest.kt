package com.fantaysea.clan

import com.fantaysea.clan.data.DeviceCredentialStore
import com.fantaysea.clan.data.DeviceCredentials
import com.fantaysea.clan.data.repository.BackendCapability
import com.fantaysea.clan.data.repository.BackendCapabilityUnavailableException
import com.fantaysea.clan.data.repository.ClanApiException
import com.fantaysea.clan.data.repository.DeviceRegistration
import com.fantaysea.clan.data.repository.RealClanRepository
import java.time.Instant
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RealClanRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var credentials: FakeCredentialStore
    private val registration = DeviceRegistration("Pixel 8", "Pixel 8", "16")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        credentials = FakeCredentialStore()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `first initialization registers saves credentials and loads me`() = runBlocking {
        server.enqueue(jsonResponse(registerEnvelope("dev_01TEST", "secret-token")))
        server.enqueue(jsonResponse(deviceEnvelope("dev_01TEST")))

        val device = repository().initializeSelf(registration)

        assertEquals("dev_01TEST", device.id)
        assertEquals("16", device.androidVersion)
        assertEquals(Instant.parse("2026-09-13T02:03:04Z").toEpochMilli(), device.createdAt)
        assertNull(device.lastOnlineAt) // malformed time is deliberately tolerated
        assertEquals(DeviceCredentials("dev_01TEST", "secret-token"), credentials.value)
        assertEquals(1, credentials.saved.size)

        val register = server.takeRequest()
        assertEquals("/api/v1/devices/register", register.path)
        val requestJson = register.body.readUtf8()
        assertTrue(requestJson.contains("\"platform\":\"ANDROID\""))
        assertTrue(requestJson.contains("\"androidVersion\":\"16\""))

        val me = server.takeRequest()
        assertEquals("/api/v1/devices/me", me.path)
        assertEquals("Bearer secret-token", me.getHeader("Authorization"))
        assertFalse(device.toString().contains("secret-token"))
    }

    @Test
    fun `restart reuses credentials and only queries me`() = runBlocking {
        credentials.value = DeviceCredentials("dev_existing", "persisted-token")
        server.enqueue(jsonResponse(deviceEnvelope("dev_existing")))

        repository().initializeSelf(registration)

        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("Bearer persisted-token", request.getHeader("Authorization"))
        assertTrue(credentials.saved.isEmpty())
    }

    @Test
    fun `40002 clears credentials and re-registers exactly once`() = runBlocking {
        credentials.value = DeviceCredentials("dev_old", "expired-token")
        server.enqueue(jsonResponse("""{"code":40002,"message":"Invalid device token","data":null}""", 401))
        server.enqueue(jsonResponse(registerEnvelope("dev_new", "new-token")))
        server.enqueue(jsonResponse(deviceEnvelope("dev_new")))

        val device = repository().initializeSelf(registration)

        assertEquals("dev_new", device.id)
        assertEquals(1, credentials.clearCount)
        assertEquals(DeviceCredentials("dev_new", "new-token"), credentials.value)
        assertEquals(3, server.requestCount)
        server.takeRequest()
        server.takeRequest()
        assertEquals("Bearer new-token", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun `server errors do not clear credentials or expose token`() {
        credentials.value = DeviceCredentials("dev_existing", "do-not-log-me")
        server.enqueue(jsonResponse("""{"code":50000,"message":"Internal error","data":null}""", 500))

        val error = assertThrows(ClanApiException::class.java) {
            runBlocking { repository().initializeSelf(registration) }
        }

        assertEquals(0, credentials.clearCount)
        assertEquals(DeviceCredentials("dev_existing", "do-not-log-me"), credentials.value)
        assertFalse(error.toString().contains("do-not-log-me"))
    }

    @Test
    fun `malformed successful json becomes typed error and preserves credentials`() {
        credentials.value = DeviceCredentials("dev_existing", "valid-token")
        server.enqueue(jsonResponse("{not-json"))

        val error = assertThrows(ClanApiException::class.java) {
            runBlocking { repository().initializeSelf(registration) }
        }

        assertEquals("服务响应格式错误", error.message)
        assertEquals(0, credentials.clearCount)
    }

    @Test
    fun `successful envelope with business error is rejected without clearing credentials`() {
        credentials.value = DeviceCredentials("dev_existing", "valid-token")
        server.enqueue(jsonResponse("""{"code":41000,"message":"business rejected","data":null}"""))

        val error = assertThrows(ClanApiException::class.java) {
            runBlocking { repository().initializeSelf(registration) }
        }

        assertEquals(41000, error.businessCode)
        assertEquals("business rejected", error.message)
        assertEquals(0, credentials.clearCount)
    }

    @Test
    fun `successful envelope with empty data is rejected`() {
        credentials.value = DeviceCredentials("dev_existing", "valid-token")
        server.enqueue(jsonResponse("""{"code":0,"message":"success","data":null}"""))

        val error = assertThrows(ClanApiException::class.java) {
            runBlocking { repository().initializeSelf(registration) }
        }

        assertEquals("服务响应缺少数据", error.message)
        assertEquals(0, credentials.clearCount)
    }

    @Test
    fun `missing capabilities fail locally without HTTP requests`() {
        val repository = repository()

        val error = assertThrows(BackendCapabilityUnavailableException::class.java) {
            runBlocking { repository.fetchDevices() }
        }

        assertEquals(BackendCapability.DEVICE_BINDINGS, error.capability)
        assertEquals("服务端暂未支持", error.message)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `legacy generated token is ignored and removed before registration`() = runBlocking {
        credentials.legacyToken = "67d9279e-2fbc-4b0d-b912-18f72c02210a"
        server.enqueue(jsonResponse(registerEnvelope("dev_migrated", "server-token")))
        server.enqueue(jsonResponse(deviceEnvelope("dev_migrated")))

        repository().initializeSelf(registration)

        assertNull(credentials.legacyToken)
        assertEquals(DeviceCredentials("dev_migrated", "server-token"), credentials.saved.single())
    }

    private fun repository() = RealClanRepository.create(server.url("/").toString(), credentials)

    private fun jsonResponse(body: String, status: Int = 200) = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun registerEnvelope(id: String, token: String) =
        """{"code":0,"message":"success","data":{"deviceId":"$id","deviceToken":"$token"}}"""

    private fun deviceEnvelope(id: String) =
        """{"code":0,"message":"success","data":{"deviceId":"$id","deviceName":"Pixel 8","platform":"ANDROID","model":"Pixel 8","androidVersion":"16","online":true,"lastSeenAt":"invalid-time","createdAt":"2026-09-13T02:03:04Z"}}"""

    private class FakeCredentialStore : DeviceCredentialStore {
        var value: DeviceCredentials? = null
        var legacyToken: String? = null
        val saved = mutableListOf<DeviceCredentials>()
        var clearCount = 0

        override suspend fun load(): DeviceCredentials? {
            legacyToken = null
            return value
        }

        override suspend fun save(credentials: DeviceCredentials) {
            saved += credentials
            value = credentials
        }

        override suspend fun clear() {
            clearCount++
            value = null
        }
    }
}
