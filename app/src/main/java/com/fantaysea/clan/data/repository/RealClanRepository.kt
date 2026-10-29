package com.fantaysea.clan.data.repository

import com.fantaysea.clan.data.DeviceCredentialStore
import com.fantaysea.clan.data.DeviceCredentials
import com.fantaysea.clan.data.model.ActiveSession
import com.fantaysea.clan.data.model.ControlCommand
import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.data.model.IceCandidate
import com.fantaysea.clan.data.model.LocalPermissions
import com.fantaysea.clan.data.model.PairingCode
import com.fantaysea.clan.data.model.SessionInfo
import com.fantaysea.clan.data.model.SessionRole
import com.fantaysea.clan.data.model.SessionStatus
import com.fantaysea.clan.data.remote.ApiEnvelope
import com.fantaysea.clan.data.remote.BindingResponse
import com.fantaysea.clan.data.remote.ClanApi
import com.fantaysea.clan.data.remote.CreateSessionRequest
import com.fantaysea.clan.data.remote.DeviceResponse
import com.fantaysea.clan.data.remote.PairingVerifyRequest
import com.fantaysea.clan.data.remote.RegisterDeviceRequest
import com.fantaysea.clan.data.remote.RenameDeviceRequest
import com.fantaysea.clan.data.remote.SessionResponse
import com.fantaysea.clan.data.ws.OkHttpWebSocketClient
import com.fantaysea.clan.data.ws.WsEventMapper
import com.fantaysea.clan.data.ws.WsFailure
import com.fantaysea.clan.data.ws.WsProtocol
import com.fantaysea.clan.data.ws.WsState
import com.fantaysea.clan.data.ws.WebSocketClient
import java.time.Instant
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlin.time.Duration.Companion.milliseconds

class RealClanRepository(
    private val api: ClanApi,
    private val credentialStore: DeviceCredentialStore,
    private val realtime: WebSocketClient = OkHttpWebSocketClient(),
    private val realtimeUrl: String? = null,
    private val json: Json = defaultJson,
) : ClanRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    //开发阶段 静态声明用于告知前端目前服务端支持了哪些功能
    override val capabilities: Set<BackendCapability> = setOf(
        BackendCapability.DEVICE_IDENTITY,
        BackendCapability.DEVICE_BINDINGS,
        BackendCapability.PAIRING,
        BackendCapability.CONTROL_SESSIONS,
        BackendCapability.REALTIME_SIGNALING,
    )
    private val mutableSelf = MutableStateFlow<Device?>(null)
    override val self: StateFlow<Device?> = mutableSelf.asStateFlow()
    private val mutableEvents = MutableSharedFlow<RemoteEvent>(extraBufferCapacity = 32)
    override val events: SharedFlow<RemoteEvent> = mutableEvents.asSharedFlow()

    private val mutableActiveSession = MutableStateFlow<ActiveSession?>(null)
    override val activeSession: StateFlow<ActiveSession?> = mutableActiveSession.asStateFlow()
    private val mutableLocalPermissions = MutableStateFlow(LocalPermissions())
    override val localPermissions: StateFlow<LocalPermissions> = mutableLocalPermissions.asStateFlow()

    /** 被控端收到 CONTROL_REQUEST 后暂存控制方，接受时用于构造 ActiveSession。 */
    private val pendingControlRequests = ConcurrentHashMap<String, Device>()
    private val requestSequence = AtomicLong()

    init {
        scope.launch {
            realtime.incoming.collect { frame ->
                val event = WsEventMapper.map(frame) ?: return@collect
                // 状态同步失败不能中断事件流；可见状态仍可由 REST 对账兜底。
                runCatching { applySessionEvent(event) }
                mutableEvents.emit(event)
            }
        }
    }

    /**
     * 协程互斥锁，用于控制多个协程对共享资源的并发访问，避免多个协程同时修改同一份数据而导致问题
     */
    private val initializationMutex = Mutex()

    override suspend fun initializeSelf(registration: DeviceRegistration): Device =
        //withLock：获取锁执行创建协程执行代码随后自动释放锁
        initializationMutex.withLock {
            //如果已经有设备信息，直接返回
            val cached = mutableSelf.value
            if (cached != null) {
                startRealtime()
                return@withLock cached
            }
            //从本机存储中读取是否有设备凭证，没有则进行注册流程
            var credentials = credentialStore.load() ?: register(registration)
            val device = try {
                //拿到设备凭证后获取当前设备信息
                loadCurrentDevice(credentials.deviceToken)
            } catch (error: ClanApiException) {
                //设备凭据获取设备信息遇到问题，判断是否为无效令牌。是的话重新注册，不是的话问题不简单
                if (error.businessCode != INVALID_DEVICE_TOKEN) throw error
                credentialStore.clear()
                realtime.stop()
                credentials = register(registration)
                try {
                    //重新注册了设备凭据后重新获取设备信息
                    loadCurrentDevice(credentials.deviceToken)
                } catch (retryError: ClanApiException) {
                    //重试还是发生错误，还是令牌无效的话不再重新注册。清空本地的设备凭据
                    if (retryError.businessCode == INVALID_DEVICE_TOKEN) {
                        credentialStore.clear()
                        realtime.stop()
                    }
                    //重试发生了非令牌无效的错误无法解决了
                    throw retryError
                }
            }
            //将获取到的设备信息保存到内存并返回设备信息
            mutableSelf.value = device
            //尽早建立实时连接：配对/控制事件依赖它，等用户点进「共享屏幕」再连会明显变慢。
            startRealtime()
            device
        }

    private suspend fun register(registration: DeviceRegistration): DeviceCredentials {
        //发起设备注册请求
        val response = safeApiCall {
            api.registerDevice(
                RegisterDeviceRequest(
                    deviceName = registration.deviceName,
                    platform = "ANDROID",
                    model = registration.model,
                    androidVersion = registration.androidVersion,
                )
            )
        }.requireData()
        if (response.deviceId.isBlank() || response.deviceToken.isBlank()) {
            throw ClanApiException(null, null, "注册响应缺少设备凭据")
        }
        return DeviceCredentials(
            response.deviceId,
            response.deviceToken
        ).also { credentialStore.save(it) }
    }

    /**
     * 获取指定的设备信息
     */
    private suspend fun loadCurrentDevice(token: String): Device = safeApiCall {
        api.getCurrentDevice("Bearer $token")
    }.requireData().toDeviceDomain()

    override suspend fun fetchDevices(): List<PairedDevice> =
        safeApiCall { api.listBindings(authorization()) }
            .requireData()
            .map { it.toPairedDevice() }

    override suspend fun findDevice(deviceId: String): Device? {
        self.value?.takeIf { it.id == deviceId }?.let { return it }
        return try {
            safeApiCall { api.getBinding(authorization(), deviceId) }
                .requireData()
                .toDevice()
        } catch (error: ClanApiException) {
            // 未绑定 / 已解绑对 App 而言就是“设备不存在”，不向 UI 抛 404。
            if (error.businessCode == BINDING_NOT_FOUND || error.businessCode == DEVICE_NOT_FOUND) null
            else throw error
        }
    }

    override suspend fun unbindDevice(deviceId: String) {
        try {
            safeApiCall { api.deleteBinding(authorization(), deviceId) }.requireSuccess()
        } catch (error: ClanApiException) {
            // 重复解绑按幂等成功处理，重复调用不会报错。
            if (error.businessCode != BINDING_NOT_FOUND) throw error
        }
    }

    override suspend fun updateDeviceName(deviceId: String, deviceName: String) {
        val name = deviceName.trim()
        if (name.isEmpty()) throw ClanServiceException("设备名称不能为空")
        try {
            safeApiCall {
                api.renameBinding(authorization(), deviceId, RenameDeviceRequest(deviceName = name))
            }.requireData()
        } catch (error: ClanApiException) {
            when {
                error.businessCode == BINDING_NOT_FOUND || error.businessCode == DEVICE_NOT_FOUND ->
                    throw ClanServiceException("设备不存在或已解绑", error)

                // 服务端还没有该接口(或网关未放行 PATCH)时给出可读提示, 而不是裸 404。
                error.httpStatus == 404 || error.httpStatus == 405 ->
                    throw ClanServiceException("当前服务端暂不支持修改设备名称", error)

                else -> throw error
            }
        }
    }

    private suspend fun deviceToken(): String =
        credentialStore.load()?.deviceToken
            ?: throw ClanApiException(null, null, "设备未注册")

    private suspend fun authorization(): String = "Bearer ${deviceToken()}"

    /**
     * 后台预热实时连接：不等待结果，失败交给 [WebSocketClient] 自己退避重连。
     * 取不到设备令牌时直接跳过，真正的认证失败会在 [ensureRealtime] 里暴露。
     */
    private suspend fun startRealtime() {
        val url = realtimeUrl ?: return
        val token = credentialStore.load()?.deviceToken ?: return
        realtime.start(url, token)
    }

    /**
     * 配对码校验要求双方都已在服务端在线，所以提交配对码前必须确认本端 AUTH 完成。
     * 客户端在后台自动重连，这里只等结果，并把失败原因翻译成可行动的提示。
     */
    private suspend fun ensureRealtime() {
        if (realtimeUrl == null) return
        startRealtime()
        var waited = 0L
        while (waited < REALTIME_CONNECT_TIMEOUT_MS) {
            if (realtime.state.value == WsState.Connected) return
            val failure = realtime.failure.value
            // 令牌失效/被限流：重试不会成功，直接告诉用户怎么恢复。
            if (failure is WsFailure.AuthRejected) {
                throw ClanApiException(null, null, "${failure.message}，请退出应用后重新打开")
            }
            delay(REALTIME_POLL_INTERVAL_MS.milliseconds)
            waited += REALTIME_POLL_INTERVAL_MS
        }
        val reason = realtime.failure.value?.message
        throw ClanApiException(null, null, reason?.let { "$it，请重试" } ?: "实时连接未就绪，请重试")
    }

    override suspend fun createPairingCode(): PairingCode {
        // 配对码是纯 REST 接口（服务端只落库），不要求本端 WebSocket 已就绪：
        // 这里只顺带预热连接，绝不因为实时连接慢/连不上而让「共享屏幕」显示不出配对码。
        startRealtime()
        val response = try {
            safeApiCall { api.createPairingCode(authorization()) }.requireData()
        } catch (error: ClanApiException) {
            throw deviceError(error)
        }
        if (response.pairingCode.isBlank()) throw ClanApiException(null, null, "配对码响应无效")
        val expiresAt = response.expiresAt.toEpochMillisOrNull()
            ?: throw ClanApiException(null, null, "配对码过期时间无效")
        return PairingCode(
            id = 0,
            code = response.pairingCode,
            deviceId = self.value?.id.orEmpty(),
            expiresAt = expiresAt,
            used = false,
            createdAt = System.currentTimeMillis(),
        )
    }

    override suspend fun submitPairingCode(code: String): String {
        ensureRealtime()
        val response = try {
            safeApiCall { api.verifyPairingCode(authorization(), PairingVerifyRequest(code)) }.requireData()
        } catch (error: ClanApiException) {
            throw pairingCodeError(error)
        }
        if (response.requestId.isBlank()) throw ClanApiException(null, null, "配对请求响应无效")
        return response.requestId
    }

    override suspend fun respondMatch(requestId: String, accept: Boolean) {
        try {
            val response = if (accept) {
                safeApiCall { api.acceptPairingRequest(authorization(), requestId) }
            } else {
                safeApiCall { api.rejectPairingRequest(authorization(), requestId) }
            }
            response.requireSuccess()
        } catch (error: ClanApiException) {
            throw pairingRequestError(error)
        }
    }

    override suspend fun cancelPairingRequest(requestId: String) {
        try {
            safeApiCall { api.cancelPairingRequest(authorization(), requestId) }.requireSuccess()
        } catch (error: ClanApiException) {
            // 已结束/已过期/已处理都视为取消成功，本地弹窗正常关闭。
            val code = error.businessCode
            if (code == null || code !in setOf(
                    PAIRING_REQUEST_NOT_FOUND,
                    PAIRING_REQUEST_EXPIRED,
                    PAIRING_REQUEST_ALREADY_HANDLED,
                )
            ) {
                throw error
            }
        }
    }

    /**
     * 设备身份类错误对用户没有可操作性，翻译成「重新打开应用」这类指引；
     * 应用重启后 [initializeSelf] 会用新令牌重新注册。
     */
    private fun deviceError(error: ClanApiException): Exception = when (error.businessCode) {
        INVALID_DEVICE_TOKEN ->
            ClanApiException(error.businessCode, error.httpStatus, "设备凭据已失效，请退出应用后重新打开", error)
        DEVICE_NOT_FOUND ->
            ClanApiException(error.businessCode, error.httpStatus, "设备已被服务端删除，请退出应用后重新打开", error)
        else -> error
    }

    private fun pairingCodeError(error: ClanApiException): Exception = when (error.businessCode) {
        PAIRING_CODE_INVALID, PAIRING_CODE_USED ->
            InvalidPairingCodeException("配对码错误，请重新输入")
        PAIRING_CODE_EXPIRED ->
            ExpiredPairingCodeException("配对码已过期，请重新获取")
        SELF_PAIRING ->
            InvalidPairingCodeException("不能与本机配对")
        PAIRING_REQUEST_ALREADY_EXISTS ->
            ClanServiceException("已有待处理的配对请求，请稍后重试")
        BINDING_ALREADY_EXISTS ->
            ClanServiceException("该设备已经绑定")
        DEVICE_OFFLINE ->
            DeviceOfflineException("对方或本机实时连接不在线，请稍后重试")
        RATE_LIMITED ->
            ClanServiceException("操作过于频繁，请稍后再试")
        else -> error
    }

    private fun pairingRequestError(error: ClanApiException): Exception = when (error.businessCode) {
        PAIRING_REQUEST_NOT_FOUND -> ClanServiceException("配对请求不存在")
        PAIRING_REQUEST_EXPIRED -> ExpiredPairingCodeException("配对请求已过期")
        PAIRING_REQUEST_ALREADY_HANDLED -> ClanServiceException("配对请求已处理")
        PAIRING_REQUEST_NOT_ALLOWED -> ClanServiceException("无权处理该配对请求")
        else -> error
    }

    override suspend fun sendControlRequest(device: Device): SessionInfo {
        ensureRealtime()
        val response = try {
            safeApiCall { api.createSession(authorization(), CreateSessionRequest(device.id)) }.requireData()
        } catch (error: ClanApiException) {
            throw sessionError(error)
        }
        val info = response.toSessionInfo()
        mutableActiveSession.value = ActiveSession(info, device, SessionRole.CONTROLLER)
        return info
    }

    override suspend fun respondControl(sessionId: String, accept: Boolean): SessionInfo {
        ensureRealtime()
        val response = try {
            safeApiCall {
                if (accept) api.acceptSession(authorization(), sessionId)
                else api.rejectSession(authorization(), sessionId)
            }.requireData()
        } catch (error: ClanApiException) {
            throw sessionError(error)
        }
        val info = response.toSessionInfo()
        if (accept) {
            val peer = pendingControlRequests.remove(sessionId)
                ?: runCatching { findDevice(info.controllerDeviceId) }.getOrNull()
            if (peer != null) {
                mutableActiveSession.value = ActiveSession(info, peer, SessionRole.CONTROLLED)
            }
        } else {
            pendingControlRequests.remove(sessionId)
            if (mutableActiveSession.value?.info?.sessionId == sessionId) {
                mutableActiveSession.value = null
            }
        }
        return info
    }

    override suspend fun endSession(sessionId: String) {
        try {
            safeApiCall { api.endSession(authorization(), sessionId) }.requireSuccess()
        } catch (error: ClanApiException) {
            // 已结束/已过期按幂等成功处理；无权限和网络错误继续抛给 UI。
            if (error.businessCode != SESSION_NOT_FOUND && error.businessCode != SESSION_INVALID_STATE) throw error
        } finally {
            pendingControlRequests.remove(sessionId)
            if (mutableActiveSession.value?.info?.sessionId == sessionId) {
                mutableActiveSession.value = null
            }
        }
    }

    override suspend fun sendControl(sessionId: String, command: ControlCommand): String {
        val requestId = nextRequestId("ctl")
        sendRealtime(WsProtocol.controlMessage(sessionId, requestId, command))
        return requestId
    }

    override suspend fun sendControlResult(
        sessionId: String,
        requestId: String,
        ok: Boolean,
        reason: String?,
    ) {
        sendRealtime(WsProtocol.controlResultMessage(sessionId, requestId, ok, reason))
    }

    override suspend fun sendPermissionStatus(
        sessionId: String,
        screenCapture: Boolean,
        accessibility: Boolean,
    ) {
        sendRealtime(WsProtocol.permissionStatusMessage(sessionId, screenCapture, accessibility))
    }

    override suspend fun sendSessionConnected(sessionId: String) {
        sendRealtime(WsProtocol.sessionConnectedMessage(sessionId))
    }

    override suspend fun sendWebRtcOffer(sessionId: String, requestId: String, sdp: String) {
        sendRealtime(WsProtocol.webrtcOfferMessage(sessionId, requestId, sdp))
    }

    override suspend fun sendWebRtcAnswer(sessionId: String, requestId: String, sdp: String) {
        sendRealtime(WsProtocol.webrtcAnswerMessage(sessionId, requestId, sdp))
    }

    override suspend fun sendIceCandidate(sessionId: String, requestId: String, candidate: IceCandidate) {
        sendRealtime(WsProtocol.iceCandidateMessage(sessionId, requestId, candidate))
    }

    override fun updateLocalPermissions(screenCapture: Boolean, accessibility: Boolean) {
        mutableLocalPermissions.value = LocalPermissions(screenCapture, accessibility)
    }

    private fun sendRealtime(message: String) {
        if (!realtime.send(message)) throw ClanServiceException("实时连接未就绪，请稍后重试")
    }

    private fun nextRequestId(prefix: String): String =
        "${prefix}_${System.currentTimeMillis()}_${requestSequence.incrementAndGet()}"

    /** 把 P4 事件同步到 activeSession，再把事件广播给 UI。 */
    private suspend fun applySessionEvent(event: RemoteEvent) {
        when (event) {
            is RemoteEvent.RealtimeReady -> reconcileActiveSession()

            is RemoteEvent.ControlRequest -> pendingControlRequests[event.sessionId] = event.controller

            is RemoteEvent.SessionStarted -> {
                val current = mutableActiveSession.value
                if (current != null && current.info.sessionId == event.sessionId) {
                    mutableActiveSession.value = current.copy(
                        info = current.info.copy(
                            status = SessionStatus.CONNECTING,
                            connectExpiresAt = event.connectExpiresAt ?: current.info.connectExpiresAt,
                        ),
                    )
                } else {
                    ensureActiveSession(event.sessionId, SessionStatus.CONNECTING, event.connectExpiresAt)
                }
            }

            is RemoteEvent.SessionConnected -> {
                val current = mutableActiveSession.value
                if (current != null && current.info.sessionId == event.sessionId) {
                    mutableActiveSession.value = current.copy(
                        info = current.info.copy(
                            status = SessionStatus.CONNECTED,
                            connectedAt = event.connectedAt ?: current.info.connectedAt,
                        ),
                    )
                } else {
                    ensureActiveSession(event.sessionId, SessionStatus.CONNECTED, connectedAt = event.connectedAt)
                }
            }

            is RemoteEvent.SessionEnded -> {
                pendingControlRequests.remove(event.sessionId)
                if (mutableActiveSession.value?.info?.sessionId == event.sessionId) {
                    mutableActiveSession.value = null
                }
            }

            is RemoteEvent.ControlResult -> {
                if (event.result != RemoteEvent.SessionRequestResult.ACCEPTED) {
                    pendingControlRequests.remove(event.sessionId)
                    if (mutableActiveSession.value?.info?.sessionId == event.sessionId) {
                        mutableActiveSession.value = null
                    }
                }
            }

            else -> Unit
        }
    }

    /**
     * 重连后主动对账当前会话：断线期间可能已经 CONNECTED / ENDED / FAILED，
     * 服务端不重放事件，只能按 sessionId 重新查询。
     */
    private suspend fun reconcileActiveSession() {
        val current = mutableActiveSession.value ?: return
        val response = runCatching {
            safeApiCall { api.getSession(authorization(), current.info.sessionId) }.requireData()
        }.getOrNull() ?: return
        val info = response.toSessionInfo()
        if (info.status.isActive) {
            mutableActiveSession.value = current.copy(info = info)
        } else {
            mutableActiveSession.value = null
        }
    }

    /**
     * 事件先于 REST 响应到达（或进程内 activeSession 丢失）时的兜底：
     * 按 sessionId 查询服务端会话，用本机 id 判断角色，再解析对端绑定。
     */
    private suspend fun ensureActiveSession(
        sessionId: String,
        status: SessionStatus,
        connectExpiresAt: Long? = null,
        connectedAt: Long? = null,
    ) {
        if (mutableActiveSession.value?.info?.sessionId == sessionId) return
        val selfId = self.value?.id ?: return
        val response = runCatching {
            safeApiCall { api.getSession(authorization(), sessionId) }.requireData()
        }.getOrNull() ?: return
        val info = response.toSessionInfo(
            statusOverride = status,
            connectExpiresAtOverride = connectExpiresAt,
            connectedAtOverride = connectedAt,
        )
        val peerId = info.peerId(selfId) ?: return
        val peer = runCatching { findDevice(peerId) }.getOrNull() ?: return
        val role = if (info.controllerDeviceId == selfId) SessionRole.CONTROLLER else SessionRole.CONTROLLED
        if (mutableActiveSession.value == null) {
            mutableActiveSession.value = ActiveSession(info, peer, role)
        }
    }

    private fun SessionResponse.toSessionInfo(
        statusOverride: SessionStatus? = null,
        connectExpiresAtOverride: Long? = null,
        connectedAtOverride: Long? = null,
    ) = SessionInfo(
        sessionId = sessionId,
        controllerDeviceId = controllerDeviceId,
        controlledDeviceId = controlledDeviceId,
        status = statusOverride ?: SessionStatus.fromWire(status) ?: SessionStatus.FAILED,
        expiresAt = expiresAt.toEpochMillisOrNull(),
        createdAt = createdAt.toEpochMillisOrNull(),
        acceptedAt = acceptedAt.toEpochMillisOrNull(),
        connectExpiresAt = connectExpiresAtOverride ?: connectExpiresAt.toEpochMillisOrNull(),
        connectedAt = connectedAtOverride ?: connectedAt.toEpochMillisOrNull(),
        rejectedAt = rejectedAt.toEpochMillisOrNull(),
        endedAt = endedAt.toEpochMillisOrNull(),
        failedAt = failedAt.toEpochMillisOrNull(),
        failureReason = failureReason,
    )

    private fun sessionError(error: ClanApiException): Exception = when (error.businessCode) {
        SESSION_NOT_FOUND -> ClanServiceException("会话不存在或已结束", error)
        SESSION_INVALID_STATE -> ClanServiceException("会话状态已变化，请重试", error)
        SESSION_NOT_ALLOWED -> ClanServiceException("无权操作该会话", error)
        SESSION_CONFLICT -> ClanServiceException("已有进行中的会话", error)
        DEVICES_NOT_BOUND -> ClanServiceException("设备未绑定，无法发起控制", error)
        SESSION_REQUEST_EXPIRED, SESSION_CONNECT_TIMEOUT -> ClanServiceException("控制请求已过期", error)
        DEVICE_OFFLINE -> DeviceOfflineException("对方或本机实时连接不在线，请稍后重试")
        RATE_LIMITED -> ClanServiceException("操作过于频繁，请稍后再试", error)
        else -> error
    }

    override fun close() {
        realtime.stop()
        scope.cancel()
    }

    private fun DeviceResponse.toDeviceDomain() = Device(
        id = deviceId,
        deviceName = deviceName,
        platform = platform,
        model = model.orEmpty(),
        androidVersion = androidVersion,
        lastOnlineAt = lastSeenAt.toEpochMillisOrNull(),
        createdAt = createdAt.toEpochMillisOrNull(),
    )

    private fun BindingResponse.toDevice() = Device(
        id = deviceId,
        deviceName = deviceName,
        platform = platform,
        model = model.orEmpty(),
        androidVersion = androidVersion,
        lastOnlineAt = lastSeenAt.toEpochMillisOrNull(),
        createdAt = createdAt.toEpochMillisOrNull(),
    )

    private fun BindingResponse.toPairedDevice() = PairedDevice(toDevice(), online)

    private fun String?.toEpochMillisOrNull(): Long? =
        this?.let { value -> runCatching { Instant.parse(value).toEpochMilli() }.getOrNull() }

    /**
     * 校验 HTTP 状态与业务码，返回完整信封。
     */
    private fun <T> Response<ApiEnvelope<T>>.requireEnvelope(): ApiEnvelope<T> {
        //响应失败
        if (!isSuccessful) {
            val parsed = errorBody()?.string()?.let { body ->
                try {
                    json.decodeFromString<ApiEnvelope<JsonElement>>(body)
                } catch (_: SerializationException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
            throw ClanApiException(
                parsed?.code,
                code(),
                failureMessage(code(), parsed?.code, parsed?.message)
            )
        }
        //处理响应成功
        //1.判断响应体是否存在
        val envelope = body() ?: throw ClanApiException(null, code(), "服务响应为空")
        //2.判断响应码是否成功
        if (envelope.code != SUCCESS) throw ClanApiException(
            envelope.code,
            code(),
            failureMessage(code(), envelope.code, envelope.message)
        )
        return envelope
    }

    /**
     * 服务端 5xx 只返回英文的 "Internal server error"，对用户没有任何指引，
     * 统一翻译成中文提示；其余情况保留服务端文案，便于定位业务错误。
     */
    private fun failureMessage(httpStatus: Int, businessCode: Int?, serverMessage: String?): String = when {
        httpStatus >= 500 || businessCode == INTERNAL_ERROR -> "服务端内部异常（500），请稍后重试"
        !serverMessage.isNullOrBlank() -> serverMessage
        else -> "服务请求失败 ($httpStatus)"
    }

    /**
     * 处理响应的数据；DELETE 等无 data 的接口使用 [requireSuccess]。
     */
    private fun <T> Response<ApiEnvelope<T>>.requireData(): T {
        val envelope = requireEnvelope()
        return envelope.data ?: throw ClanApiException(envelope.code, code(), "服务响应缺少数据")
    }

    private fun <T> Response<ApiEnvelope<T>>.requireSuccess() {
        requireEnvelope()
    }

    /**
     * 处理传入的请求遇到的异常
     * 若请求正常会将结果原封不动返回
     */
    private suspend fun <T> safeApiCall(call: suspend () -> T): T = try {
        call()
    } catch (error: ClanServiceException) {
        throw error
    } catch (error: SerializationException) {
        throw ClanApiException(null, null, "服务响应格式错误", error)
    } catch (error: IOException) {
        throw ClanApiException(null, null, "无法连接服务端", error)
    } catch (error: RuntimeException) {
        throw ClanApiException(null, null, "服务请求失败", error)
    }

    companion object {
        private const val SUCCESS = 0
        private const val INTERNAL_ERROR = 50001
        private const val INVALID_DEVICE_TOKEN = 40002
        private const val DEVICE_NOT_FOUND = 40003
        private const val DEVICE_OFFLINE = 40004
        private const val RATE_LIMITED = 40005
        private const val PAIRING_CODE_INVALID = 40101
        private const val PAIRING_CODE_EXPIRED = 40102
        private const val PAIRING_CODE_USED = 40103
        private const val SELF_PAIRING = 40104
        private const val PAIRING_REQUEST_NOT_FOUND = 40105
        private const val PAIRING_REQUEST_EXPIRED = 40106
        private const val PAIRING_REQUEST_ALREADY_HANDLED = 40107
        private const val PAIRING_REQUEST_NOT_ALLOWED = 40108
        private const val PAIRING_REQUEST_ALREADY_EXISTS = 40109
        private const val BINDING_NOT_FOUND = 40201
        private const val BINDING_ALREADY_EXISTS = 40202
        private const val SESSION_NOT_FOUND = 40301
        private const val SESSION_INVALID_STATE = 40302
        private const val SESSION_NOT_ALLOWED = 40303
        private const val SESSION_CONFLICT = 40304
        private const val DEVICES_NOT_BOUND = 40305
        private const val SESSION_REQUEST_EXPIRED = 40306
        private const val SESSION_CONNECT_TIMEOUT = 40308

        /** 提交配对码前等待实时连接认证完成的上限。 */
        private const val REALTIME_CONNECT_TIMEOUT_MS = 15_000L
        private const val REALTIME_POLL_INTERVAL_MS = 100L
        val defaultJson: Json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        fun create(
            baseUrl: String,
            credentialStore: DeviceCredentialStore,
            realtime: WebSocketClient = OkHttpWebSocketClient(),
        ): RealClanRepository {
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(defaultJson.asConverterFactory("application/json".toMediaType()))
                .build()
            return RealClanRepository(
                api = retrofit.create(ClanApi::class.java),
                credentialStore = credentialStore,
                realtime = realtime,
                realtimeUrl = WsProtocol.toWebSocketUrl(baseUrl),
            )
        }
    }
}
