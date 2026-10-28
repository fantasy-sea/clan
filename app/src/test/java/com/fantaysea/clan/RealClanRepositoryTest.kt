package com.fantaysea.clan

import com.fantaysea.clan.data.DeviceCredentialStore
import com.fantaysea.clan.data.DeviceCredentials
import com.fantaysea.clan.data.model.ControlCommand
import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.data.model.SessionRole
import com.fantaysea.clan.data.model.SessionStatus
import com.fantaysea.clan.data.repository.BackendCapability
import com.fantaysea.clan.data.repository.ClanApiException
import com.fantaysea.clan.data.repository.DeviceOfflineException
import com.fantaysea.clan.data.repository.DeviceRegistration
import com.fantaysea.clan.data.repository.ExpiredPairingCodeException
import com.fantaysea.clan.data.repository.InvalidPairingCodeException
import com.fantaysea.clan.data.repository.RealClanRepository
import com.fantaysea.clan.data.ws.WsFailure
import com.fantaysea.clan.data.ws.WsFrame
import com.fantaysea.clan.data.ws.WsState
import com.fantaysea.clan.data.ws.WebSocketClient
import java.time.Instant
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
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
    private lateinit var realtime: FakeWebSocketClient
    private val registration = DeviceRegistration("Pixel 8", "Pixel 8", "16")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        credentials = FakeCredentialStore()
        realtime = FakeWebSocketClient()
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
    fun `binding pairing control session and realtime capabilities are enabled`() {
        val repository = repository()

        assertTrue(BackendCapability.DEVICE_BINDINGS in repository.capabilities)
        assertTrue(BackendCapability.PAIRING in repository.capabilities)
        assertTrue(BackendCapability.CONTROL_SESSIONS in repository.capabilities)
        assertTrue(BackendCapability.REALTIME_SIGNALING in repository.capabilities)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `sendControlRequest creates a session and tracks controller role`() = runBlocking {
        val repository = initializedRepository()
        server.enqueue(jsonResponse(sessionEnvelope("session_1", "dev_self", "dev_peer", "REQUESTING")))
        val peer = peer("dev_peer")

        val info = repository.sendControlRequest(peer)

        assertEquals("session_1", info.sessionId)
        assertEquals(SessionStatus.REQUESTING, info.status)
        val active = repository.activeSession.value!!
        assertEquals(SessionRole.CONTROLLER, active.role)
        assertEquals("dev_peer", active.peer.id)
        assertEquals("session_1", active.info.sessionId)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/sessions", request.path)
        assertEquals("Bearer token", request.getHeader("Authorization"))
        assertTrue(request.body.readUtf8().contains("\"targetDeviceId\":\"dev_peer\""))
    }

    @Test
    fun `respondControl accept tracks controlled role from bound controller`() = runBlocking {
        val repository = initializedRepository(selfId = "dev_controlled")
        server.enqueue(jsonResponse(sessionEnvelope("session_1", "dev_controller", "dev_controlled", "CONNECTING")))
        server.enqueue(jsonResponse(dataEnvelope(bindingJson("dev_controller"))))

        val info = repository.respondControl("session_1", accept = true)

        assertEquals(SessionStatus.CONNECTING, info.status)
        val active = repository.activeSession.value!!
        assertEquals(SessionRole.CONTROLLED, active.role)
        assertEquals("dev_controller", active.peer.id)

        assertEquals("/api/v1/sessions/session_1/accept", server.takeRequest().path)
        assertEquals("/api/v1/bindings/dev_controller", server.takeRequest().path)
    }

    @Test
    fun `rejectControl clears active session`() = runBlocking {
        val repository = initializedRepository(selfId = "dev_controlled")
        server.enqueue(jsonResponse(sessionEnvelope("session_1", "dev_controller", "dev_controlled", "REJECTED")))

        repository.respondControl("session_1", accept = false)

        assertNull(repository.activeSession.value)
        assertEquals("/api/v1/sessions/session_1/reject", server.takeRequest().path)
    }

    @Test
    fun `endSession clears active session and tolerates already ended`() = runBlocking {
        val repository = initializedRepository()
        server.enqueue(jsonResponse(sessionEnvelope("session_1", "dev_self", "dev_peer", "REQUESTING")))
        repository.sendControlRequest(peer("dev_peer"))
        assertEquals("session_1", repository.activeSession.value?.info?.sessionId)

        server.enqueue(jsonResponse(sessionEnvelope("session_1", "dev_self", "dev_peer", "ENDED")))
        repository.endSession("session_1")
        assertNull(repository.activeSession.value)
        assertEquals("/api/v1/sessions/session_1/end", server.takeRequest().path)

        // 已结束再调用一次按幂等成功处理，不向 UI 抛异常。
        server.enqueue(jsonResponse("""{"code":40302,"message":"Session is in an invalid state","data":null}""", 409))
        repository.endSession("session_1")
    }

    @Test
    fun `sendControl writes a normalized CONTROL frame`() = runBlocking {
        val repository = initializedRepository()

        val requestId = repository.sendControl("session_1", ControlCommand.Tap(0.5, 0.25))

        assertTrue(requestId.startsWith("ctl_"))
        val frame = com.fantaysea.clan.data.ws.WsProtocol.parse(realtime.sent.single())!!
        assertEquals("CONTROL", frame.type)
        assertEquals("session_1", frame.sessionId)
        assertEquals(requestId, frame.requestId)
        assertEquals("TAP", frame.payload!!["action"]!!.jsonPrimitive.content)
        assertEquals(0.5, frame.payload!!["x"]!!.jsonPrimitive.double, 0.0)
    }

    @Test
    fun `sendPermissionStatus and control result are queued`() = runBlocking {
        val repository = initializedRepository()

        repository.sendPermissionStatus("session_1", screenCapture = true, accessibility = false)
        repository.sendControlResult("session_1", "ctl_1", ok = true)

        assertEquals(2, realtime.sent.size)
        val permission = com.fantaysea.clan.data.ws.WsProtocol.parse(realtime.sent[0])!!
        assertEquals("PERMISSION_STATUS", permission.type)
        val result = com.fantaysea.clan.data.ws.WsProtocol.parse(realtime.sent[1])!!
        assertEquals("CONTROL_RESULT", result.type)
        assertEquals("COMMAND", result.payload!!["scope"]!!.jsonPrimitive.content)
    }

    @Test
    fun `fetchDevices maps binding fields and sends the device token`() = runBlocking {
        val repository = initializedRepository()
        server.enqueue(jsonResponse(listEnvelope(bindingJson("dev_peer"), bindingJson("dev_offline", online = false))))

        val devices = repository.fetchDevices()

        assertEquals(2, devices.size)
        assertEquals("dev_peer", devices[0].device.id)
        assertEquals("妈妈的手机", devices[0].device.deviceName)
        assertEquals("Pixel 8", devices[0].device.model)
        assertTrue(devices[0].online)
        assertEquals(Instant.parse("2026-09-13T02:03:04Z").toEpochMilli(), devices[0].device.lastOnlineAt)
        assertEquals(Instant.parse("2026-09-01T00:00:00Z").toEpochMilli(), devices[0].device.createdAt)
        assertFalse(devices[1].online)

        val request = server.takeRequest()
        assertEquals("/api/v1/bindings", request.path)
        assertEquals("Bearer token", request.getHeader("Authorization"))
    }

    @Test
    fun `findDevice returns a bound peer from the single binding endpoint`() = runBlocking {
        val repository = initializedRepository()
        server.enqueue(jsonResponse(dataEnvelope(bindingJson("dev_peer"))))

        val device = repository.findDevice("dev_peer")

        assertEquals("dev_peer", device?.id)
        assertEquals("妈妈的手机", device?.deviceName)
        val request = server.takeRequest()
        assertEquals("/api/v1/bindings/dev_peer", request.path)
        assertEquals("Bearer token", request.getHeader("Authorization"))
    }

    @Test
    fun `findDevice returns self without an HTTP request`() = runBlocking {
        val repository = initializedRepository()
        val requestCount = server.requestCount

        val device = repository.findDevice("dev_self")

        assertEquals("dev_self", device?.id)
        assertEquals(requestCount, server.requestCount)
    }

    @Test
    fun `findDevice returns null when the peer is not bound`() = runBlocking {
        val repository = initializedRepository()
        server.enqueue(jsonResponse("""{"code":40201,"message":"Binding not found","data":null}""", 404))

        assertNull(repository.findDevice("dev_missing"))
        assertEquals("/api/v1/bindings/dev_missing", server.takeRequest().path)
    }

    @Test
    fun `unbindDevice accepts a null data payload`() = runBlocking {
        val repository = initializedRepository()
        server.enqueue(jsonResponse("""{"code":0,"message":"success","data":null}"""))

        repository.unbindDevice("dev_peer")

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/bindings/dev_peer", request.path)
        assertEquals("Bearer token", request.getHeader("Authorization"))
    }

    @Test
    fun `unbindDevice treats an already removed binding as success`() = runBlocking {
        val repository = initializedRepository()
        server.enqueue(jsonResponse("""{"code":40201,"message":"Binding not found","data":null}""", 404))

        repository.unbindDevice("dev_peer")

        assertEquals("/api/v1/bindings/dev_peer", server.takeRequest().path)
    }

    @Test
    fun `binding server errors are propagated`() {
        val repository = runBlocking { initializedRepository() }
        server.enqueue(jsonResponse("""{"code":50001,"message":"Internal error","data":null}""", 500))

        val error = assertThrows(ClanApiException::class.java) {
            runBlocking { repository.findDevice("dev_peer") }
        }

        assertEquals(50001, error.businessCode)
    }

    @Test
    fun `createPairingCode starts realtime and maps server expiry`() = runBlocking {
        val repository = initializedRepository()
        server.enqueue(jsonResponse(dataEnvelope("""{"pairingCode":"123456","expiresAt":"2026-09-13T02:04:04Z"}""")))

        val code = repository.createPairingCode()

        assertEquals("123456", code.code)
        assertEquals("dev_self", code.deviceId)
        assertEquals(Instant.parse("2026-09-13T02:04:04Z").toEpochMilli(), code.expiresAt)
        assertEquals("ws://localhost:${server.port}/ws", realtime.startedUrl)
        assertEquals("token", realtime.startedToken)
        assertTrue(BackendCapability.PAIRING in repository.capabilities)
        assertTrue(BackendCapability.REALTIME_SIGNALING in repository.capabilities)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/pairing/codes", request.path)
        assertEquals("Bearer token", request.getHeader("Authorization"))
    }

    @Test
    fun `submitPairingCode posts the code and returns request id`() = runBlocking {
        val repository = initializedRepository()
        server.enqueue(jsonResponse(dataEnvelope("""{"requestId":"pairreq_1","expiresAt":"2026-09-13T02:04:04Z"}""")))

        val requestId = repository.submitPairingCode("123456")

        assertEquals("pairreq_1", requestId)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/pairing/verify", request.path)
        assertTrue(request.body.readUtf8().contains("\"pairingCode\":\"123456\""))
    }

    @Test
    fun `pairing code errors map to typed exceptions`() {
        val repository = runBlocking { initializedRepository() }
        server.enqueue(jsonResponse("""{"code":40101,"message":"Pairing code invalid","data":null}""", 400))

        val invalid = assertThrows(InvalidPairingCodeException::class.java) {
            runBlocking { repository.submitPairingCode("123456") }
        }
        assertEquals("配对码错误，请重新输入", invalid.message)

        server.enqueue(jsonResponse("""{"code":40102,"message":"Pairing code expired","data":null}""", 410))
        val expired = assertThrows(ExpiredPairingCodeException::class.java) {
            runBlocking { repository.submitPairingCode("123456") }
        }
        assertEquals("配对码已过期，请重新获取", expired.message)

        server.enqueue(jsonResponse("""{"code":40004,"message":"Device offline","data":null}""", 409))
        val offline = assertThrows(DeviceOfflineException::class.java) {
            runBlocking { repository.submitPairingCode("123456") }
        }
        assertTrue(offline.message!!.contains("不在线"))
    }

    @Test
    fun `respondMatch uses accept and reject endpoints`() = runBlocking {
        val repository = initializedRepository()
        server.enqueue(jsonResponse(dataEnvelope("""{"requestId":"pairreq_1","status":"ACCEPTED","expiresAt":"2026-09-13T02:04:04Z","handledAt":"2026-09-13T02:04:05Z"}""")))
        server.enqueue(jsonResponse(dataEnvelope("""{"requestId":"pairreq_2","status":"REJECTED","expiresAt":"2026-09-13T02:04:04Z","handledAt":"2026-09-13T02:04:05Z"}""")))

        repository.respondMatch("pairreq_1", accept = true)
        repository.respondMatch("pairreq_2", accept = false)

        val accept = server.takeRequest()
        assertEquals("/api/v1/pairing/requests/pairreq_1/accept", accept.path)
        val reject = server.takeRequest()
        assertEquals("/api/v1/pairing/requests/pairreq_2/reject", reject.path)
    }

    @Test
    fun `cancelPairingRequest posts cancel and tolerates already ended request`() = runBlocking {
        val repository = initializedRepository()
        server.enqueue(jsonResponse(dataEnvelope("""{"requestId":"pairreq_1","status":"REJECTED","expiresAt":"2026-09-13T02:04:04Z","handledAt":"2026-09-13T02:04:05Z"}""")))
        repository.cancelPairingRequest("pairreq_1")

        assertEquals("/api/v1/pairing/requests/pairreq_1/cancel", server.takeRequest().path)

        server.enqueue(jsonResponse("""{"code":40107,"message":"Pairing request already handled","data":null}""", 409))
        repository.cancelPairingRequest("pairreq_1")
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

    private fun peer(id: String) = Device(
        id = id,
        deviceName = "妈妈的手机",
        platform = "ANDROID",
        model = "Pixel 8",
        androidVersion = "16",
        lastOnlineAt = null,
        createdAt = null,
    )

    private fun sessionEnvelope(id: String, controller: String, controlled: String, status: String) =
        dataEnvelope(
            """{"sessionId":"$id","controllerDeviceId":"$controller","controlledDeviceId":"$controlled","status":"$status","expiresAt":"2026-09-13T02:04:04Z","createdAt":"2026-09-13T02:03:04Z","acceptedAt":null,"connectExpiresAt":"2026-09-13T02:05:04Z","connectedAt":null,"rejectedAt":null,"endedAt":null,"failedAt":null,"failureReason":null}""",
        )

    private fun repository() = RealClanRepository.create(server.url("/").toString(), credentials, realtime)

    private suspend fun initializedRepository(
        selfId: String = "dev_self",
        token: String = "token",
    ): RealClanRepository {
        credentials.value = DeviceCredentials(selfId, token)
        server.enqueue(jsonResponse(deviceEnvelope(selfId)))
        val repository = repository()
        repository.initializeSelf(registration)
        assertEquals("/api/v1/devices/me", server.takeRequest().path)
        return repository
    }

    private fun bindingJson(id: String, online: Boolean = true) =
        """{"deviceId":"$id","deviceName":"妈妈的手机","platform":"ANDROID","model":"Pixel 8","androidVersion":"16","online":$online,"lastSeenAt":"2026-09-13T02:03:04Z","boundAt":"2026-09-02T00:00:00Z","createdAt":"2026-09-01T00:00:00Z"}"""

    private fun listEnvelope(vararg bindings: String) =
        """{"code":0,"message":"success","data":[${bindings.joinToString(",")}]}"""

    private fun dataEnvelope(data: String) =
        """{"code":0,"message":"success","data":$data}"""

    private fun jsonResponse(body: String, status: Int = 200) = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun registerEnvelope(id: String, token: String) =
        """{"code":0,"message":"success","data":{"deviceId":"$id","deviceToken":"$token"}}"""

    private fun deviceEnvelope(id: String) =
        """{"code":0,"message":"success","data":{"deviceId":"$id","deviceName":"Pixel 8","platform":"ANDROID","model":"Pixel 8","androidVersion":"16","online":true,"lastSeenAt":"invalid-time","createdAt":"2026-09-13T02:03:04Z"}}"""

    private class FakeWebSocketClient : WebSocketClient {
        override val state: StateFlow<WsState> = MutableStateFlow(WsState.Connected)
        override val failure: StateFlow<WsFailure?> = MutableStateFlow(null)
        private val frames = MutableSharedFlow<WsFrame>(extraBufferCapacity = 8)
        override val incoming = frames.asSharedFlow()
        var startedUrl: String? = null
        var startedToken: String? = null
        val sent = mutableListOf<String>()

        override fun start(url: String, token: String) {
            startedUrl = url
            startedToken = token
        }

        override fun stop() = Unit

        override fun send(text: String): Boolean {
            sent += text
            return true
        }
    }

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
