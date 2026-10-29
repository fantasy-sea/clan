package com.fantaysea.clan.media

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.WindowManager
import com.fantaysea.clan.data.model.IceCandidate as RemoteIceCandidate
import com.fantaysea.clan.data.model.SessionRole
import com.fantaysea.clan.data.repository.ClanRepository
import com.fantaysea.clan.data.repository.RemoteEvent
import com.fantaysea.clan.service.ScreenCaptureService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate as NativeIceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

/** 媒体链路状态；UI 用它展示连接进度与失败原因。 */
sealed interface WebRtcState {
    data object Idle : WebRtcState

    /** 受控端等待前台服务/采集就绪；控制端准备 PeerConnection。 */
    data object Preparing : WebRtcState

    /** offer/answer/ICE 交换中。 */
    data object Negotiating : WebRtcState

    data object Connected : WebRtcState

    /** PeerConnection 进入 DISCONNECTED，等待自动恢复。 */
    data object Reconnecting : WebRtcState

    data class Failed(val reason: String) : WebRtcState
}

/** 远端视频帧尺寸，供控制端计算等比显示区域。 */
data class RemoteVideoSize(val width: Int, val height: Int)

enum class WebRtcStartResult {
    STARTED,
    ALREADY_STARTED,
    NEEDS_CAPTURE_CONSENT,
    UNAVAILABLE,
}

/**
 * 应用级 WebRTC 媒体管理器。
 *
 * 职责：
 * - 按服务端契约建立单条视频 PeerConnection：控制端发 offer，被控端回 answer；
 * - 被控端用 MediaProjection 采集屏幕，前台服务保证 Android 14+ 的投影生命周期；
 * - 收发 SDP/ICE（通过 [ClanRepository] 的 `/ws` 信令），在 PeerConnection 就绪前按
 *   sessionId 缓冲信令；
 * - 控制端把远端视频轨绑定到 [SurfaceViewRenderer]，并按首帧更新画面尺寸。
 *
 * 线程模型：所有 org.webrtc 对象只在单线程 [webRtcExecutor] 上创建/操作/销毁；
 * 渲染器的 init/release 固定主线程；状态通过 [StateFlow] 暴露。
 *
 * 同一时间只支持一条媒体会话；会话结束/失败后必须重新 start。
 */
class WebRtcManager(
    context: Context,
    private val repository: ClanRepository,
    private val screenCapture: ScreenCaptureManager,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val webRtcExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ClanWebRtc").apply { isDaemon = true }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val initLock = Any()
    private val sessionLock = Any()

    private var eglBase: EglBase? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var initializationAttempted = false

    private var currentSession: MediaSession? = null
    private val requestSequence = AtomicLong()
    private val signalBuffer = WebRtcSignalBuffer()
    private val displayManager =
        appContext.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private val _state = MutableStateFlow<WebRtcState>(WebRtcState.Idle)
    val state: StateFlow<WebRtcState> = _state.asStateFlow()

    private val _remoteVideoSize = MutableStateFlow<RemoteVideoSize?>(null)
    val remoteVideoSize: StateFlow<RemoteVideoSize?> = _remoteVideoSize.asStateFlow()

    private val remoteVideoHealth = RemoteVideoHealthTracker()
    private val _remoteVideoState = MutableStateFlow<RemoteVideoState>(RemoteVideoState.Idle)

    /**
     * 控制端远端画面可用状态；UI 必须等它变成 [RemoteVideoState.Streaming] 才允许操作。
     * 被控端会话没有“接收画面”的概念，保持 [RemoteVideoState.Idle]。
     */
    val remoteVideoState: StateFlow<RemoteVideoState> = _remoteVideoState.asStateFlow()

    /** 只在主线程访问；[disposePeer] 会通过 volatile 读取后移除 sink。 */
    @Volatile
    private var renderer: SurfaceViewRenderer? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) {
            if (displayId != Display.DEFAULT_DISPLAY) return
            webRtcExecutor.execute { updateControlledCaptureSize() }
        }
    }

    init {
        observeRepository()
        startRemoteVideoHealthWatchdog()
    }

    /** 控制端会话页：创建 PeerConnection 并发 offer。 */
    fun startController(sessionId: String): WebRtcStartResult {
        if (!hasActiveSession(sessionId)) return WebRtcStartResult.UNAVAILABLE
        val existing = synchronized(sessionLock) { currentSession }
        if (existing != null &&
            existing.sessionId == sessionId &&
            existing.role == SessionRole.CONTROLLER &&
            !existing.closing
        ) {
            return WebRtcStartResult.ALREADY_STARTED
        }
        existing?.let { teardown(it, WebRtcState.Idle, endSession = false) }

        Log.i(TAG, "start controller session=$sessionId")
        val session = MediaSession(sessionId, SessionRole.CONTROLLER)
        synchronized(sessionLock) {
            val latest = currentSession
            if (latest != null && latest.sessionId == sessionId &&
                latest.role == SessionRole.CONTROLLER && !latest.closing
            ) {
                return WebRtcStartResult.ALREADY_STARTED
            }
            currentSession = session
        }
        _state.value = WebRtcState.Preparing
        _remoteVideoSize.value = null
        remoteVideoHealth.reset()
        _remoteVideoState.value = RemoteVideoState.WaitingFirstFrame
        webRtcExecutor.execute { beginController(session) }
        return WebRtcStartResult.STARTED
    }

    /**
     * 被控端会话页：先确认有可用的一次性投影授权，再启动 mediaProjection 前台服务。
     * 服务真正进入前台后，[onCaptureServiceStarted] 才会创建 MediaProjection 与 PeerConnection。
     */
    fun startControlled(sessionId: String): WebRtcStartResult {
        if (!hasActiveSession(sessionId)) return WebRtcStartResult.UNAVAILABLE
        val existing = synchronized(sessionLock) { currentSession }
        if (existing != null &&
            existing.sessionId == sessionId &&
            existing.role == SessionRole.CONTROLLED &&
            !existing.closing
        ) {
            return WebRtcStartResult.ALREADY_STARTED
        }
        // 只认「待消费的一次性令牌」；Starting/Capturing 但 currentSession 已丢失时不能复用旧状态，
        // 否则会误以为仍有可用授权而静默不启动媒体。
        if (!screenCapture.hasPendingConsent()) {
            return WebRtcStartResult.NEEDS_CAPTURE_CONSENT
        }
        existing?.let { teardown(it, WebRtcState.Idle, endSession = false) }

        Log.i(TAG, "start controlled session=$sessionId")
        val session = MediaSession(sessionId, SessionRole.CONTROLLED)
        synchronized(sessionLock) {
            val latest = currentSession
            if (latest != null && latest.sessionId == sessionId &&
                latest.role == SessionRole.CONTROLLED && !latest.closing
            ) {
                return WebRtcStartResult.ALREADY_STARTED
            }
            currentSession = session
        }
        _state.value = WebRtcState.Preparing
        _remoteVideoSize.value = null
        remoteVideoHealth.reset()
        _remoteVideoState.value = RemoteVideoState.Idle
        try {
            ScreenCaptureService.start(appContext, sessionId)
        } catch (error: Exception) {
            Log.e(TAG, "start screen capture service failed", error)
            failSession(session, "无法启动屏幕共享服务")
        }
        return WebRtcStartResult.STARTED
    }

    /**
     * 由 ScreenCaptureService 在 startForeground 成功后回调。
     * 返回 false 表示会话已被结束/替换，服务应立即 stopSelf，避免遗留前台通知。
     */
    internal fun onCaptureServiceStarted(sessionId: String): Boolean {
        val session = synchronized(sessionLock) {
            currentSession?.takeIf {
                it.sessionId == sessionId && it.role == SessionRole.CONTROLLED
            }
        } ?: return false
        webRtcExecutor.execute { beginControlledCapture(session) }
        return true
    }

    /** 前台服务被系统回收/异常停止；主动 stop 时 currentSession 已清空，不会重复处理。 */
    internal fun onCaptureServiceDestroyed(sessionId: String, reason: String) {
        val session = synchronized(sessionLock) {
            currentSession?.takeIf {
                it.sessionId == sessionId && it.role == SessionRole.CONTROLLED
            }
        } ?: return
        webRtcExecutor.execute { failSession(session, reason) }
    }

    /**
     * 控制端绑定渲染器；可早于 PeerConnection 创建，远端视频轨到达时会自动 addSink。
     *
     * VideoTrack 内部的 sink 表不是线程安全的，所有 addSink/removeSink 都投递到同一个
     * [webRtcExecutor]，保证与 attachRemoteTrack/disposePeer 串行。
     */
    fun bindRenderer(renderer: SurfaceViewRenderer) {
        this.renderer = renderer
        webRtcExecutor.execute {
            val track = synchronized(sessionLock) { currentSession?.peer?.remoteVideoTrack }
            if (track != null) {
                runCatching { track.addSink(renderer) }
            }
        }
    }

    /**
     * 解绑渲染器。用有界等待确保 removeSink 先于调用方 release() 完成，
     * 避免已释放的渲染器继续收到帧。
     */
    fun unbindRenderer(renderer: SurfaceViewRenderer) {
        if (this.renderer === renderer) this.renderer = null
        val removal = webRtcExecutor.submit(Runnable {
            val track = synchronized(sessionLock) { currentSession?.peer?.remoteVideoTrack }
            if (track != null) {
                runCatching { track.removeSink(renderer) }
            }
        })
        runCatching { removal.get(1, TimeUnit.SECONDS) }
    }

    /** 停止当前媒体会话（UI 主动结束/返回时调用；不负责调用 REST endSession）。 */
    fun stop() {
        val session = synchronized(sessionLock) { currentSession } ?: return
        teardown(session, WebRtcState.Idle, endSession = false)
    }

    /** 只停止指定会话，避免旧页面的延迟回调误停新会话。 */
    fun stopFor(sessionId: String) {
        val session = synchronized(sessionLock) {
            currentSession?.takeIf { it.sessionId == sessionId }
        } ?: return
        teardown(session, WebRtcState.Idle, endSession = false)
    }

    /** 幂等初始化 PeerConnectionFactory / EglBase；首次可能阻塞约百毫秒。 */
    fun ensureInitialized() {
        ensureInitializedInternal()
    }

    /** 供渲染器 init 使用的共享 EGL context。 */
    val eglBaseContext: EglBase.Context
        get() {
            ensureInitializedInternal()
            return checkNotNull(eglBase) { "WebRTC EGL 未初始化" }.eglBaseContext
        }

    // ── 初始化与 PeerConnection 创建 ──────────────────────────

    private fun ensureInitializedInternal() {
        synchronized(initLock) {
            if (peerConnectionFactory != null) return
            check(!initializationAttempted) { "WebRTC 初始化失败，无法重试" }
            initializationAttempted = true
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(appContext)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions(),
            )
            val egl = EglBase.create()
            val encoderFactory = DefaultVideoEncoderFactory(egl.eglBaseContext, true, true)
            val decoderFactory = DefaultVideoDecoderFactory(egl.eglBaseContext)
            val factory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory()
            eglBase = egl
            peerConnectionFactory = factory
        }
    }

    private fun createPeer(session: MediaSession): PeerHandle {
        val factory = checkNotNull(peerConnectionFactory) { "WebRTC 未初始化" }
        val handle = PeerHandle(session)
        val iceServers = WebRtcConfig.iceServers
        Log.i(
            TAG,
            "create peer session=${session.sessionId} role=${session.role} iceServers=${iceServers.size}",
        )
        val configuration = PeerConnection.RTCConfiguration(iceServers).apply {
            // Java 包装层的默认值仍是 PLAN_B，必须显式切到 Unified Plan 才能用 transceiver。
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            enableCpuOveruseDetection = false
            screencastMinBitrate = WebRtcConfig.MIN_VIDEO_BITRATE_BPS
        }
        val peerConnection = factory.createPeerConnection(configuration, createPeerObserver(session))
            ?: error("创建 PeerConnection 失败")
        handle.peerConnection = peerConnection
        return handle
    }

    private fun beginController(session: MediaSession) {
        if (!isCurrent(session)) return
        try {
            Log.i(TAG, "begin controller session=${session.sessionId}")
            ensureInitializedInternal()
            val handle = createPeer(session)
            session.peer = handle
            handle.peerConnection.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY),
            )
            synchronized(sessionLock) {
                if (currentSession === session && !session.closing) {
                    _state.value = WebRtcState.Negotiating
                }
            }
            createOffer(session, handle)
            drainBufferedSignals(session)
            Log.i(TAG, "controller media ready session=${session.sessionId}")
        } catch (error: Throwable) {
            Log.e(TAG, "begin controller failed", error)
            failSession(session, "无法建立视频连接")
        }
    }

    private fun beginControlledCapture(session: MediaSession) {
        if (!isCurrent(session) || session.peer != null) return
        Log.i(TAG, "begin controlled session=${session.sessionId}")
        val consent = screenCapture.takeConsent()
        if (consent == null) {
            failSession(session, "屏幕捕获授权已失效，请重新授权")
            return
        }
        try {
            ensureInitializedInternal()
            val factory = checkNotNull(peerConnectionFactory)
            val egl = checkNotNull(eglBase)
            val (width, height) = captureSize()
            val handle = createPeer(session)
            session.peer = handle

            val capturer = ScreenCapturerAndroid(
                consent.resultData,
                createMediaProjectionCallback(session),
            )
            // 逐项登记已创建资源，任一步骤失败时 disposePeer 都能完整回收。
            handle.capturer = capturer
            val surfaceTextureHelper = checkNotNull(
                SurfaceTextureHelper.create("ClanScreenCapture", egl.eglBaseContext),
            ) { "无法创建屏幕采集纹理线程" }
            handle.surfaceTextureHelper = surfaceTextureHelper
            val videoSource = factory.createVideoSource(capturer.isScreencast)
            handle.videoSource = videoSource
            capturer.initialize(surfaceTextureHelper, appContext, videoSource.capturerObserver)
            val videoTrack = factory.createVideoTrack("clan-screen", videoSource)
            handle.localVideoTrack = videoTrack
            handle.captureWidth = width
            handle.captureHeight = height

            handle.peerConnection.addTrack(videoTrack)
            videoSource.adaptOutputFormat(width, height, WebRtcConfig.CAPTURE_FPS)
            capturer.startCapture(width, height, WebRtcConfig.CAPTURE_FPS)

            displayManager.registerDisplayListener(displayListener, mainHandler)
            handle.displayListenerRegistered = true

            // 采集创建期间会话可能已被结束/替换：在 sessionLock 内一次性确认并提交状态，
            // 避免与 teardown 交错留下「已授权/Connecting」的幽灵状态。
            var aborted = false
            synchronized(sessionLock) {
                if (currentSession !== session || session.closing) {
                    aborted = true
                } else {
                    screenCapture.markCapturing()
                    updateScreenCapturePermission(active = true)
                    _state.value = WebRtcState.Negotiating
                }
            }
            if (aborted) {
                disposePeer(session)
                screenCapture.markStopped()
                updateScreenCapturePermission(active = false)
                ScreenCaptureService.stop(appContext)
                return
            }
            drainBufferedSignals(session)
            armNegotiationWatchdog(session, handle)
            Log.i(
                TAG,
                "controlled media ready session=${session.sessionId} size=${width}x$height",
            )
        } catch (error: Throwable) {
            Log.e(TAG, "begin controlled capture failed", error)
            screenCapture.markError("无法启动屏幕采集")
            failSession(session, "无法启动屏幕采集")
        }
    }

    // ── 信令 ───────────────────────────────────────────────────

    private fun observeRepository() {
        scope.launch {
            repository.events.collect { event ->
                when (event) {
                    is RemoteEvent.Signal -> webRtcExecutor.execute { onSignal(event) }
                    is RemoteEvent.SessionEnded -> handleSessionEnded(event.sessionId)
                    is RemoteEvent.RealtimeReady -> onRealtimeReady()
                    // 旧版客户端会在进入会话页时直接上报占位 CONNECTED；此时还没有任何 SDP，
                    // 记录一条警告便于用 logcat 区分「旧包」与真正的 ICE 失败。
                    is RemoteEvent.SessionConnected ->
                        webRtcExecutor.execute { warnIfConnectedBeforeSdp(event.sessionId) }

                    else -> Unit
                }
            }
        }
    }

    /**
     * 控制端视频健康看门狗。
     *
     * 帧回调只记录“最近一帧时间”，这里统一把时间差映射为可观察状态：超过阈值没有新帧
     * 就进入 [RemoteVideoState.Stalled] 让 UI 锁定操作；恢复收帧后下一次帧回调会立刻回到
     * [RemoteVideoState.Streaming]。
     */
    private fun startRemoteVideoHealthWatchdog() {
        scope.launch {
            while (isActive) {
                delay(WebRtcConfig.REMOTE_VIDEO_HEALTH_CHECK_INTERVAL_MS)
                val session = synchronized(sessionLock) { currentSession }
                if (session?.role != SessionRole.CONTROLLER) {
                    if (_remoteVideoState.value != RemoteVideoState.Idle) {
                        _remoteVideoState.value = RemoteVideoState.Idle
                    }
                    continue
                }
                val next = remoteVideoHealth.evaluate(
                    nowMs = SystemClock.elapsedRealtime(),
                    hasActiveSession = true,
                    mediaConnected = _state.value is WebRtcState.Connected,
                )
                if (_remoteVideoState.value != next) {
                    _remoteVideoState.value = next
                }
            }
        }
    }

    private fun handleSessionEnded(sessionId: String) {
        signalBuffer.clear(sessionId)
        val session = synchronized(sessionLock) {
            currentSession?.takeIf { it.sessionId == sessionId }
        }
        if (session != null) teardown(session, WebRtcState.Idle, endSession = false)
    }

    /** 对端声称已连通、但本端还没收到任何远端 SDP：极大概率是旧包占位上报。 */
    private fun warnIfConnectedBeforeSdp(sessionId: String) {
        val session = synchronized(sessionLock) { currentSession } ?: return
        if (session.sessionId != sessionId) return
        val handle = session.peer ?: return
        if (handle.remoteNegotiationSeen) return
        Log.w(
            TAG,
            "SESSION_CONNECTED before remote SDP; peer may be an old build " +
                "session=$sessionId role=${session.role}",
        )
    }

    private fun onRealtimeReady() {
        val session = synchronized(sessionLock) { currentSession } ?: return
        // 重连期间可能漏掉 SESSION_ENDED：仓库在 AUTH_SUCCESS 后已按 sessionId 对账，
        // 这里若发现会话已不在活动列表，直接收敛媒体（不重复调用 endSession）。
        val active = repository.activeSession.value
        if (active == null || active.info.sessionId != session.sessionId) {
            teardown(session, WebRtcState.Idle, endSession = false)
            return
        }
        if (_state.value !is WebRtcState.Connected) return
        val handle = session.peer ?: return
        handle.connectedReported.set(false)
        reportConnected(session, handle)
    }

    /** 事件已按提交顺序进入单线程 executor，因此这里的缓冲/处理不会和 start 并发。 */
    private fun onSignal(event: RemoteEvent.Signal) {
        val session = synchronized(sessionLock) { currentSession }
        if (session == null || session.sessionId != event.sessionId || session.closing) {
            signalBuffer.put(event.sessionId, event)
            return
        }
        val handle = session.peer
        if (handle == null) {
            signalBuffer.put(event.sessionId, event)
            return
        }
        when (event) {
            is RemoteEvent.WebRtcOffer -> {
                if (session.role != SessionRole.CONTROLLED) return
                Log.i(TAG, "recv offer session=${event.sessionId} chars=${event.sdp.length}")
                markRemoteNegotiationSeen(handle)
                enqueueRemoteDescription(session, handle, SessionDescription.Type.OFFER, event.sdp)
            }

            is RemoteEvent.WebRtcAnswer -> {
                if (session.role != SessionRole.CONTROLLER) return
                Log.i(TAG, "recv answer session=${event.sessionId} chars=${event.sdp.length}")
                markRemoteNegotiationSeen(handle)
                enqueueRemoteDescription(session, handle, SessionDescription.Type.ANSWER, event.sdp)
            }

            is RemoteEvent.IceCandidateEvent -> {
                Log.d(
                    TAG,
                    "recv ice session=${event.sessionId} mid=${event.candidate.sdpMid} " +
                        "index=${event.candidate.sdpMLineIndex}",
                )
                handleRemoteIce(handle, event.candidate)
            }
        }
    }

    private fun drainBufferedSignals(session: MediaSession) {
        signalBuffer.take(session.sessionId).forEach { onSignal(it) }
    }

    /**
     * 启动 SDP 协商看门狗。
     *
     * 旧版客户端不会创建 PeerConnection，因此既不会发 offer 也不会回 answer；而此时它已经
     * 提前上报了占位的 SESSION_CONNECTED，服务端不会再用 120s 连接超时兜底。没有这个定时器，
     * 新客户端会永久停在“正在建立画面连接…”。
     */
    private fun armNegotiationWatchdog(session: MediaSession, handle: PeerHandle) {
        if (!isCurrent(session) || session.peer !== handle) return
        if (handle.remoteNegotiationSeen || handle.negotiationJob != null) return
        val timeoutMs = when (session.role) {
            SessionRole.CONTROLLER -> WebRtcConfig.CONTROLLER_ANSWER_TIMEOUT_MS
            SessionRole.CONTROLLED -> WebRtcConfig.CONTROLLED_OFFER_TIMEOUT_MS
        }
        Log.i(
            TAG,
            "arm negotiation watchdog session=${session.sessionId} role=${session.role} " +
                "timeoutMs=$timeoutMs",
        )
        handle.negotiationJob = scope.launch {
            delay(timeoutMs)
            webRtcExecutor.execute {
                if (!isCurrent(session) || session.peer !== handle) return@execute
                if (handle.remoteNegotiationSeen || handle.remoteDescriptionReady) return@execute
                val reason = when (session.role) {
                    SessionRole.CONTROLLER ->
                        "等待被控端画面应答超时，请确认被控端已安装最新版本并完成屏幕授权"

                    SessionRole.CONTROLLED ->
                        "未收到控制端的画面请求，请确认控制端已安装最新版本"
                }
                Log.w(
                    TAG,
                    "negotiation timeout session=${session.sessionId} role=${session.role}",
                )
                failSession(session, reason)
            }
        }
    }

    private fun cancelNegotiationWatchdog(handle: PeerHandle) {
        handle.negotiationJob?.cancel()
        handle.negotiationJob = null
    }

    private fun markRemoteNegotiationSeen(handle: PeerHandle) {
        handle.remoteNegotiationSeen = true
        cancelNegotiationWatchdog(handle)
    }

    private fun enqueueRemoteDescription(
        session: MediaSession,
        handle: PeerHandle,
        type: SessionDescription.Type,
        sdp: String,
    ) {
        if (!isCurrent(session) || session.peer !== handle) return
        handle.pendingRemote = PendingRemote(type, sdp)
        if (handle.remoteApplyBusy || handle.answerBusy) return
        applyNextRemoteDescription(session, handle)
    }

    /**
     * 串行应用远端 SDP：一次只允许一个 setRemoteDescription；被控端在成功应用 offer 后
     * createAnswer，answer 发完才继续处理下一个 pending（支持 limited renegotiation）。
     */
    private fun applyNextRemoteDescription(session: MediaSession, handle: PeerHandle) {
        if (!isCurrent(session) || session.peer !== handle) return
        val next = handle.pendingRemote
        if (next == null) {
            handle.remoteApplyBusy = false
            return
        }
        handle.pendingRemote = null
        handle.remoteApplyBusy = true
        handle.peerConnection.setRemoteDescription(
            object : SdpObserverAdapter() {
                override fun onSetSuccess() {
                    webRtcExecutor.execute {
                        if (!isCurrent(session) || session.peer !== handle) return@execute
                        Log.i(TAG, "apply remote ${next.type} ok session=${session.sessionId}")
                        handle.remoteApplyBusy = false
                        handle.remoteDescriptionReady = true
                        markRemoteNegotiationSeen(handle)
                        drainRemoteIce(handle)
                        if (next.type == SessionDescription.Type.OFFER) {
                            handle.answerBusy = true
                            createAnswer(session, handle)
                        } else {
                            applyNextRemoteDescription(session, handle)
                        }
                    }
                }

                override fun onSetFailure(error: String?) {
                    webRtcExecutor.execute {
                        Log.w(
                            TAG,
                            "setRemoteDescription ${next.type} failed session=${session.sessionId}: " +
                                error?.take(120),
                        )
                        failSession(session, "处理远端 SDP 失败")
                    }
                }
            },
            SessionDescription(next.type, next.sdp),
        )
    }

    private fun createOffer(session: MediaSession, handle: PeerHandle) {
        if (!isCurrent(session) || session.peer !== handle) return
        handle.peerConnection.createOffer(
            object : SdpObserverAdapter() {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    if (sdp == null) {
                        webRtcExecutor.execute { failSession(session, "创建 offer 失败") }
                        return
                    }
                    webRtcExecutor.execute {
                        if (!isCurrent(session) || session.peer !== handle) return@execute
                        handle.peerConnection.setLocalDescription(
                            object : SdpObserverAdapter() {
                                override fun onSetSuccess() {
                                    webRtcExecutor.execute {
                                        if (!isCurrent(session)) return@execute
                                        sendSdp(session, sdp.description, isOffer = true) {
                                            armNegotiationWatchdog(session, handle)
                                        }
                                    }
                                }

                                override fun onSetFailure(error: String?) {
                                    webRtcExecutor.execute {
                                        failSession(session, "设置本地 offer 失败")
                                    }
                                }
                            },
                            sdp,
                        )
                    }
                }

                override fun onCreateFailure(error: String?) {
                    webRtcExecutor.execute { failSession(session, "创建 offer 失败") }
                }
            },
            MediaConstraints(),
        )
    }

    private fun createAnswer(session: MediaSession, handle: PeerHandle) {
        if (!isCurrent(session) || session.peer !== handle) return
        handle.peerConnection.createAnswer(
            object : SdpObserverAdapter() {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    if (sdp == null) {
                        webRtcExecutor.execute { failSession(session, "创建 answer 失败") }
                        return
                    }
                    webRtcExecutor.execute {
                        if (!isCurrent(session) || session.peer !== handle) return@execute
                        handle.peerConnection.setLocalDescription(
                            object : SdpObserverAdapter() {
                                override fun onSetSuccess() {
                                    webRtcExecutor.execute {
                                        if (!isCurrent(session)) return@execute
                                        sendSdp(session, sdp.description, isOffer = false) {
                                            handle.answerBusy = false
                                            applyNextRemoteDescription(session, handle)
                                        }
                                    }
                                }

                                override fun onSetFailure(error: String?) {
                                    webRtcExecutor.execute {
                                        failSession(session, "设置本地 answer 失败")
                                    }
                                }
                            },
                            sdp,
                        )
                    }
                }

                override fun onCreateFailure(error: String?) {
                    webRtcExecutor.execute { failSession(session, "创建 answer 失败") }
                }
            },
            MediaConstraints(),
        )
    }

    private fun sendSdp(
        session: MediaSession,
        sdp: String,
        isOffer: Boolean,
        after: () -> Unit = {},
    ) {
        val requestId = nextRequestId(if (isOffer) "sig" else "ans")
        scope.launch {
            try {
                Log.i(
                    TAG,
                    "send ${if (isOffer) "offer" else "answer"} session=${session.sessionId} " +
                        "chars=${sdp.length}",
                )
                if (isOffer) {
                    repository.sendWebRtcOffer(session.sessionId, requestId, sdp)
                } else {
                    repository.sendWebRtcAnswer(session.sessionId, requestId, sdp)
                }
                webRtcExecutor.execute {
                    if (isCurrent(session)) after()
                }
            } catch (error: Exception) {
                Log.w(TAG, "send sdp failed: ${error.message}")
                failSession(session, "信令发送失败")
            }
        }
    }

    private fun handleRemoteIce(handle: PeerHandle, candidate: RemoteIceCandidate) {
        if (!handle.remoteDescriptionReady) {
            handle.iceBuffer.add(candidate)
            return
        }
        addNativeIce(handle, candidate)
    }

    private fun drainRemoteIce(handle: PeerHandle) {
        handle.iceBuffer.markRemoteDescriptionReady().forEach { addNativeIce(handle, it) }
    }

    private fun addNativeIce(handle: PeerHandle, candidate: RemoteIceCandidate) {
        if (candidate.candidate.isBlank()) return
        runCatching {
            handle.peerConnection.addIceCandidate(
                NativeIceCandidate(
                    candidate.sdpMid ?: "",
                    candidate.sdpMLineIndex ?: 0,
                    candidate.candidate,
                ),
            )
        }.onFailure { Log.w(TAG, "addIceCandidate failed: ${it.message}") }
    }

    // ── PeerConnection 回调 ───────────────────────────────────

    private fun createPeerObserver(session: MediaSession): PeerConnection.Observer =
        object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState?) = Unit

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                when (newState) {
                    PeerConnection.IceConnectionState.DISCONNECTED ->
                        postConnectionState(session, PeerConnection.PeerConnectionState.DISCONNECTED)

                    PeerConnection.IceConnectionState.FAILED ->
                        postConnectionState(session, PeerConnection.PeerConnectionState.FAILED)

                    else -> Unit
                }
            }

            override fun onStandardizedIceConnectionChange(
                newState: PeerConnection.IceConnectionState?,
            ) = Unit

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                if (newState != null) postConnectionState(session, newState)
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit

            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) = Unit

            override fun onIceCandidate(candidate: NativeIceCandidate?) {
                val nativeCandidate = candidate ?: return
                val sdp = nativeCandidate.sdp?.takeIf { it.isNotBlank() } ?: return
                if (!isCurrent(session)) return
                Log.d(
                    TAG,
                    "send ice session=${session.sessionId} mid=${nativeCandidate.sdpMid} " +
                        "index=${nativeCandidate.sdpMLineIndex}",
                )
                val requestId = nextRequestId("ice")
                val remoteCandidate = RemoteIceCandidate(
                    candidate = sdp,
                    sdpMid = nativeCandidate.sdpMid,
                    sdpMLineIndex = nativeCandidate.sdpMLineIndex,
                )
                scope.launch {
                    runCatching {
                        repository.sendIceCandidate(session.sessionId, requestId, remoteCandidate)
                    }.onFailure { Log.w(TAG, "send ice candidate failed: ${it.message}") }
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out NativeIceCandidate>?) = Unit

            override fun onAddStream(stream: MediaStream?) = Unit

            override fun onRemoveStream(stream: MediaStream?) = Unit

            override fun onDataChannel(channel: DataChannel?) = Unit

            override fun onRenegotiationNeeded() = Unit

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                attachRemoteTrack(session, receiver?.track())
            }

            override fun onRemoveTrack(receiver: RtpReceiver?) = Unit

            override fun onTrack(transceiver: RtpTransceiver?) {
                attachRemoteTrack(session, transceiver?.receiver?.track())
            }
        }

    private fun postConnectionState(
        session: MediaSession,
        newState: PeerConnection.PeerConnectionState,
    ) {
        webRtcExecutor.execute { handleConnectionState(session, newState) }
    }

    private fun handleConnectionState(
        session: MediaSession,
        newState: PeerConnection.PeerConnectionState,
    ) {
        if (!isCurrent(session)) return
        val handle = session.peer ?: return
        Log.i(
            TAG,
            "peer connection state=$newState session=${session.sessionId} role=${session.role}",
        )
        when (newState) {
            PeerConnection.PeerConnectionState.CONNECTED -> {
                cancelNegotiationWatchdog(handle)
                handle.reconnectJob?.cancel()
                handle.reconnectJob = null
                _state.value = WebRtcState.Connected
                if (session.role == SessionRole.CONTROLLER &&
                    _remoteVideoState.value == RemoteVideoState.Idle
                ) {
                    // 连通只是操作的前置条件之一；等待首帧期间仍然锁定 UI。
                    _remoteVideoState.value = RemoteVideoState.WaitingFirstFrame
                }
                if (session.role == SessionRole.CONTROLLED) {
                    runCatching {
                        handle.peerConnection.setBitrate(
                            WebRtcConfig.MIN_VIDEO_BITRATE_BPS,
                            WebRtcConfig.START_VIDEO_BITRATE_BPS,
                            WebRtcConfig.MAX_VIDEO_BITRATE_BPS,
                        )
                    }
                }
                reportConnected(session, handle)
            }

            PeerConnection.PeerConnectionState.DISCONNECTED -> {
                if (handle.reconnectJob != null) return
                if (session.role == SessionRole.CONTROLLER) {
                    // 断开后旧画面随时可能冻结，立即收回操作权，等重连后的新帧再解锁。
                    remoteVideoHealth.reset()
                    _remoteVideoState.value = RemoteVideoState.WaitingFirstFrame
                }
                _state.value = WebRtcState.Reconnecting
                handle.reconnectJob = scope.launch {
                    delay(WebRtcConfig.RECONNECT_GRACE_MS)
                    webRtcExecutor.execute {
                        if (isCurrent(session) &&
                            session.peer === handle &&
                            _state.value is WebRtcState.Reconnecting
                        ) {
                            failSession(session, "视频连接中断")
                        }
                    }
                }
            }

            PeerConnection.PeerConnectionState.FAILED,
            PeerConnection.PeerConnectionState.CLOSED -> failSession(session, "视频连接失败")

            else -> Unit
        }
    }

    private fun attachRemoteTrack(session: MediaSession, track: MediaStreamTrack?) {
        if (track !is VideoTrack) return
        webRtcExecutor.execute {
            if (!isCurrent(session)) return@execute
            val handle = session.peer ?: return@execute
            if (handle.remoteVideoTrack?.id() == track.id()) return@execute

            // 换轨后旧轨道的“最近收帧时间”不再可信，先回到等待首帧。
            if (session.role == SessionRole.CONTROLLER) {
                remoteVideoHealth.reset()
                _remoteVideoState.value = RemoteVideoState.WaitingFirstFrame
            }
            handle.remoteVideoTrack?.let { old ->
                handle.resolutionSink?.let { sink -> runCatching { old.removeSink(sink) } }
                renderer?.let { currentRenderer -> runCatching { old.removeSink(currentRenderer) } }
            }
            val resolutionSink = object : VideoSink {
                override fun onFrame(frame: VideoFrame?) {
                    // 帧的所有权归 WebRTC 的 Java sink 包装器（onFrame 返回后自动 release），
                    // 这里只读取尺寸，不能额外调用 frame.release()。
                    if (frame == null) return
                    // 会话结束后仍可能有少量在途帧，不能借它们把状态改回“可操作”。
                    if (!isCurrent(session)) return
                    remoteVideoHealth.onFrame(SystemClock.elapsedRealtime())
                    if (session.role == SessionRole.CONTROLLER &&
                        _state.value is WebRtcState.Connected &&
                        _remoteVideoState.value != RemoteVideoState.Streaming
                    ) {
                        _remoteVideoState.value = RemoteVideoState.Streaming
                    }
                    val width = frame.rotatedWidth
                    val height = frame.rotatedHeight
                    val previous = _remoteVideoSize.value
                    if (previous == null ||
                        previous.width != width ||
                        previous.height != height
                    ) {
                        _remoteVideoSize.value = RemoteVideoSize(width, height)
                    }
                }
            }
            handle.resolutionSink = resolutionSink
            handle.remoteVideoTrack = track
            track.addSink(resolutionSink)
            renderer?.let { currentRenderer ->
                runCatching { track.addSink(currentRenderer) }
            }
        }
    }

    private fun reportConnected(session: MediaSession, handle: PeerHandle) {
        if (!isCurrent(session)) return
        if (!handle.connectedReported.compareAndSet(false, true)) return
        Log.i(TAG, "report SESSION_CONNECTED session=${session.sessionId} role=${session.role}")
        scope.launch {
            try {
                repository.sendSessionConnected(session.sessionId)
            } catch (error: Exception) {
                Log.w(TAG, "send SESSION_CONNECTED failed: ${error.message}")
                handle.connectedReported.set(false)
            }
        }
    }

    // ── 采集尺寸 ──────────────────────────────────────────────

    private fun captureSize(): Pair<Int, Int> {
        val bounds = runCatching {
            val windowManager =
                appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.maximumWindowMetrics.bounds
        }.getOrNull()

        var width = bounds?.width() ?: 0
        var height = bounds?.height() ?: 0
        if (width <= 0 || height <= 0) {
            val metrics = appContext.resources.displayMetrics
            width = metrics.widthPixels
            height = metrics.heightPixels
        }
        val longSide = maxOf(width, height).coerceAtLeast(1)
        val scale = if (longSide > WebRtcConfig.MAX_CAPTURE_LONG_SIDE) {
            WebRtcConfig.MAX_CAPTURE_LONG_SIDE.toFloat() / longSide
        } else {
            1f
        }
        return evenDimension((width * scale).roundToInt()) to
            evenDimension((height * scale).roundToInt())
    }

    private fun evenDimension(value: Int): Int =
        ((value.coerceAtLeast(2)) / 2 * 2).coerceAtLeast(2)

    private fun updateControlledCaptureSize() {
        val session = synchronized(sessionLock) { currentSession } ?: return
        if (session.role != SessionRole.CONTROLLED) return
        val handle = session.peer ?: return
        val (width, height) = captureSize()
        if (width == handle.captureWidth && height == handle.captureHeight) return
        handle.captureWidth = width
        handle.captureHeight = height
        runCatching {
            handle.capturer?.changeCaptureFormat(width, height, WebRtcConfig.CAPTURE_FPS)
        }
        runCatching {
            handle.videoSource?.adaptOutputFormat(width, height, WebRtcConfig.CAPTURE_FPS)
        }
    }

    private fun createMediaProjectionCallback(session: MediaSession): MediaProjection.Callback =
        object : MediaProjection.Callback() {
            override fun onStop() {
                webRtcExecutor.execute { failSession(session, "屏幕捕获已停止") }
            }
        }

    // ── 生命周期 ───────────────────────────────────────────────

    private fun hasActiveSession(sessionId: String): Boolean {
        val active = repository.activeSession.value ?: return false
        return active.info.sessionId == sessionId && active.info.status.isActive
    }

    private fun isCurrent(session: MediaSession): Boolean =
        synchronized(sessionLock) { currentSession === session && !session.closing }

    /**
     * 统一收敛入口：清空当前会话、停止采集/服务、更新权限与状态，异步释放 WebRTC 资源。
     * 失败时由 [failSession] 额外通知服务端结束会话。
     */
    private fun teardown(
        session: MediaSession,
        nextState: WebRtcState,
        endSession: Boolean,
    ) {
        synchronized(sessionLock) {
            if (session.closing) return
            session.closing = true
            if (currentSession === session) currentSession = null
        }
        Log.i(
            TAG,
            "teardown session=${session.sessionId} role=${session.role} " +
                "nextState=$nextState endSession=$endSession",
        )
        if (session.role == SessionRole.CONTROLLED) {
            screenCapture.markStopped()
            updateScreenCapturePermission(active = false)
        }
        _remoteVideoSize.value = null
        remoteVideoHealth.reset()
        _remoteVideoState.value = RemoteVideoState.Idle
        _state.value = nextState
        signalBuffer.clear(session.sessionId)
        webRtcExecutor.execute {
            // 先释放 MediaProjection/采集，再停前台服务；避免 FGS 先消失导致投影被系统提前终止。
            disposePeer(session)
            if (session.role == SessionRole.CONTROLLED) {
                // 若已有新的受控会话在复用该服务，不要误停新会话的采集。
                val current = synchronized(sessionLock) { currentSession }
                if (current == null || current.role != SessionRole.CONTROLLED) {
                    ScreenCaptureService.stop(appContext)
                }
            }
        }
        if (endSession) {
            scope.launch {
                runCatching { repository.endSession(session.sessionId) }
                    .onFailure { Log.w(TAG, "end session failed: ${it.message}") }
            }
        }
    }

    private fun failSession(session: MediaSession, reason: String) {
        if (!isCurrent(session)) return
        Log.w(TAG, "media session failed session=${session.sessionId} reason=$reason")
        teardown(session, WebRtcState.Failed(reason), endSession = true)
    }

    private fun disposePeer(session: MediaSession) {
        val handle = session.peer ?: return
        session.peer = null
        cancelNegotiationWatchdog(handle)
        handle.reconnectJob?.cancel()
        handle.reconnectJob = null

        val remoteTrack = handle.remoteVideoTrack
        if (remoteTrack != null) {
            handle.resolutionSink?.let { sink -> runCatching { remoteTrack.removeSink(sink) } }
            renderer?.let { currentRenderer ->
                runCatching { remoteTrack.removeSink(currentRenderer) }
            }
        }
        handle.resolutionSink = null
        handle.remoteVideoTrack = null

        if (handle.displayListenerRegistered) {
            runCatching { displayManager.unregisterDisplayListener(displayListener) }
            handle.displayListenerRegistered = false
        }
        runCatching { handle.capturer?.stopCapture() }
        runCatching { handle.capturer?.dispose() }
        runCatching { handle.surfaceTextureHelper?.dispose() }
        runCatching { handle.videoSource?.dispose() }
        runCatching { handle.localVideoTrack?.dispose() }
        runCatching { handle.peerConnection.close() }
        runCatching { handle.peerConnection.dispose() }
    }

    private fun updateScreenCapturePermission(active: Boolean) {
        val permissions = repository.localPermissions.value
        repository.updateLocalPermissions(
            screenCapture = active,
            accessibility = permissions.accessibility,
        )
    }

    private fun nextRequestId(prefix: String): String =
        "${prefix}_${System.currentTimeMillis()}_${requestSequence.incrementAndGet()}"

    private class MediaSession(
        val sessionId: String,
        val role: SessionRole,
    ) {
        @Volatile
        var peer: PeerHandle? = null

        @Volatile
        var closing: Boolean = false
    }

    /** 一条会话对应的 WebRTC 资源；除标记 volatile 的字段外只在 [webRtcExecutor] 上访问。 */
    private inner class PeerHandle(val session: MediaSession) {
        lateinit var peerConnection: PeerConnection
        @Volatile
        var remoteVideoTrack: VideoTrack? = null
        var localVideoTrack: VideoTrack? = null
        var videoSource: VideoSource? = null
        var capturer: VideoCapturer? = null
        var surfaceTextureHelper: SurfaceTextureHelper? = null
        var resolutionSink: VideoSink? = null
        var displayListenerRegistered = false
        var captureWidth = 0
        var captureHeight = 0

        val iceBuffer = IceCandidateBuffer()
        var remoteDescriptionReady = false
        var pendingRemote: PendingRemote? = null
        var remoteApplyBusy = false
        var answerBusy = false
        var reconnectJob: Job? = null
        var negotiationJob: Job? = null
        var remoteNegotiationSeen = false
        val connectedReported = AtomicBoolean(false)
    }

    private data class PendingRemote(
        val type: SessionDescription.Type,
        val sdp: String,
    )

    private open class SdpObserverAdapter : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetFailure(error: String?) = Unit
    }

    private companion object {
        const val TAG = "ClanWebRtc"
    }
}
