package com.fantaysea.clan.data.ws

import android.util.Log
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * OkHttp WebSocket 实现。
 *
 * - 连接建立后立即发送 AUTH；5 秒内未收到 AUTH_SUCCESS 视为失败。
 * - 认证成功后每 20 秒发送应用层 PING（后端按文本帧处理）。
 * - 断线后按 3s、6s… 退避重连，封顶 30s；同一 URL + token 的 start 幂等。
 * - AUTH_FAILED（令牌失效/被限流）不重试，直接停在 Disconnected 并把原因写进 [failure]：
 *   令牌失效重试多少次都不会成功，交给上层给出「请重新打开应用」这类提示。
 * - 握手失败、连不上等原因写入 [failure] 后继续退避重连，网络恢复即可自愈。
 * - AUTH_SUCCESS 也会进入 [incoming]，供上层触发绑定列表对账。
 *
 * 日志只记录连接地址、状态和错误码，绝不记录设备令牌或完整帧。
 */
class OkHttpWebSocketClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
) : WebSocketClient {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(WsState.Disconnected)
    override val state: StateFlow<WsState> = _state.asStateFlow()
    private val _failure = MutableStateFlow<WsFailure?>(null)
    override val failure: StateFlow<WsFailure?> = _failure.asStateFlow()
    private val _incoming = MutableSharedFlow<WsFrame>(extraBufferCapacity = 64)
    override val incoming: SharedFlow<WsFrame> = _incoming.asSharedFlow()

    private val lock = Any()
    private var currentUrl: String? = null
    private var currentToken: String? = null
    private var connectionJob: Job? = null
    private var heartbeatJob: Job? = null
    private var webSocket: WebSocket? = null

    override fun start(url: String, deviceToken: String) {
        synchronized(lock) {
            if (currentUrl == url && currentToken == deviceToken && connectionJob?.isActive == true) return
            connectionJob?.cancel()
            heartbeatJob?.cancel()
            heartbeatJob = null
            webSocket?.cancel()
            webSocket = null
            currentUrl = url
            currentToken = deviceToken
            _failure.value = null
            _state.value = WsState.Connecting
            Log.i(TAG, "connecting to $url")
            connectionJob = scope.launch { connectionLoop(url, deviceToken) }
        }
    }

    override fun send(text: String): Boolean {
        val socket = synchronized(lock) { webSocket } ?: return false
        return socket.send(text)
    }

    override fun stop() {
        synchronized(lock) {
            currentUrl = null
            currentToken = null
            connectionJob?.cancel()
            connectionJob = null
            heartbeatJob?.cancel()
            heartbeatJob = null
            webSocket?.close(1000, "client stop")
            webSocket?.cancel()
            webSocket = null
            _state.value = WsState.Disconnected
        }
    }

    private suspend fun connectionLoop(url: String, token: String) {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            _state.value = if (attempt == 0) WsState.Connecting else WsState.Reconnecting
            val authResult = CompletableDeferred<Boolean>()
            val closed = CompletableDeferred<Unit>()
            val socket = httpClient.newWebSocket(
                Request.Builder().url(url).build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.i(TAG, "connected, sending AUTH")
                        if (!webSocket.send(WsProtocol.authMessage(token, "auth-${System.currentTimeMillis()}"))) {
                            Log.w(TAG, "AUTH frame was not queued")
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val frame = WsProtocol.parse(text)
                        if (frame == null) {
                            Log.w(TAG, "dropped unparsable frame")
                            return
                        }
                        when (frame.type) {
                            "AUTH_SUCCESS" -> {
                                Log.i(TAG, "AUTH_SUCCESS")
                                _failure.value = null
                                _state.value = WsState.Connected
                                _incoming.tryEmit(frame)
                                authResult.complete(true)
                            }

                            "AUTH_FAILED" -> {
                                // 帧内容来自服务端，解析失败不能影响连接状态机。
                                val code = runCatching {
                                    frame.payload?.get("code")?.jsonPrimitive?.intOrNull
                                }.getOrNull()
                                val message = runCatching {
                                    frame.payload?.get("message")?.jsonPrimitive?.contentOrNull
                                }.getOrNull()
                                Log.w(TAG, "AUTH_FAILED code=$code")
                                _failure.value = WsFailure.AuthRejected(code, message)
                                authResult.complete(false)
                                webSocket.close(AUTH_REJECTED_CLOSE_CODE, "authentication failed")
                            }

                            "PONG" -> Unit
                            else -> _incoming.tryEmit(frame)
                        }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.i(TAG, "closed code=$code")
                        authResult.complete(false)
                        closed.complete(Unit)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        val reason = describeFailure(t, response)
                        Log.w(TAG, "connection failed: $reason")
                        if (_failure.value !is WsFailure.AuthRejected) {
                            _failure.value = WsFailure.Transport(reason)
                        }
                        authResult.complete(false)
                        closed.complete(Unit)
                    }
                },
            )
            synchronized(lock) { webSocket = socket }

            val authenticated = withTimeoutOrNull(AUTH_TIMEOUT_MS) { authResult.await() } ?: false
            if (!authenticated) {
                socket.cancel()
                synchronized(lock) { if (webSocket === socket) webSocket = null }
                val failure = _failure.value
                if (failure is WsFailure.AuthRejected) {
                    // 令牌失效/被限流：继续重连只会无限刷服务端，停在这里等上层处理。
                    Log.w(TAG, "authentication rejected, stop reconnecting")
                    _state.value = WsState.Disconnected
                    return
                }
                if (failure == null) {
                    // 握手成功但服务端一直没回 AUTH_SUCCESS：多半是服务端处理 AUTH 时异常，
                    // 需要对照服务端日志看 /ws 的 AUTH 处理。
                    Log.w(TAG, "timed out waiting for AUTH_SUCCESS")
                    _failure.value = WsFailure.Transport("等待 AUTH_SUCCESS 超时")
                }
                _state.value = WsState.Reconnecting
                attempt += 1
                delay(backoffMillis(attempt))
                continue
            }

            _state.value = WsState.Connected
            startHeartbeat(socket)
            closed.await()
            heartbeatJob?.cancel()
            heartbeatJob = null
            synchronized(lock) { if (webSocket === socket) webSocket = null }
            _state.value = WsState.Reconnecting
            attempt = 1
            delay(backoffMillis(attempt))
        }
    }

    private fun startHeartbeat(socket: WebSocket) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            var sequence = 0
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (!socket.send(WsProtocol.pingMessage("ping-${++sequence}"))) break
            }
        }
    }

    /** 把底层异常翻译成人能看懂的原因，便于定位「服务端没开 / 反向代理没转发 /ws」等情况。 */
    private fun describeFailure(error: Throwable, response: Response?): String = when {
        response != null -> "服务端拒绝握手（HTTP ${response.code}）"
        error is java.net.UnknownHostException -> "无法解析服务端地址"
        error is java.net.ConnectException -> "无法连接服务端"
        error is java.net.SocketTimeoutException -> "连接服务端超时"
        else -> error.message?.takeIf { it.isNotBlank() } ?: (error::class.simpleName ?: "网络异常")
    }

    private fun backoffMillis(attempt: Int): Long =
        (RECONNECT_STEP_MS * attempt.coerceAtLeast(1)).coerceAtMost(RECONNECT_MAX_MS)

    companion object {
        private const val TAG = "ClanWs"
        private const val AUTH_TIMEOUT_MS = 5_000L
        private const val HEARTBEAT_INTERVAL_MS = 20_000L
        private const val RECONNECT_STEP_MS = 3_000L
        private const val RECONNECT_MAX_MS = 30_000L
        private const val AUTH_REJECTED_CLOSE_CODE = 1008

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }
}
