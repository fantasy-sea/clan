package com.fantaysea.clan.data.ws

import com.fantaysea.clan.data.model.ControlCommand
import com.fantaysea.clan.data.model.IceCandidate
import java.net.URI
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 服务端 WebSocket 帧：{type, requestId, sessionId, timestamp, payload}。
 * 客户端只依赖 type 和 payload；timestamp 不参与权限/超时判断。
 */
@Serializable
data class WsFrame(
    val type: String,
    val requestId: String? = null,
    val sessionId: String? = null,
    val timestamp: Long = 0,
    val payload: JsonObject? = null,
)

/** 服务端事件中的设备摘要。 */
@Serializable
data class WsDeviceSummary(
    val deviceId: String,
    val deviceName: String,
    val platform: String,
    val model: String? = null,
    val androidVersion: String? = null,
)

@Serializable
data class WsPairingRequestPayload(
    val requester: WsDeviceSummary,
    val expiresAt: String? = null,
)

@Serializable
data class WsPairingResultPayload(
    val result: String,
    val expiresAt: String? = null,
)

@Serializable
data class WsPairingSucceededPayload(
    val device: WsDeviceSummary,
)

/** DEVICE_ONLINE / DEVICE_OFFLINE 的载荷。 */
@Serializable
data class WsPresencePayload(
    val deviceId: String,
    val lastSeenAt: String? = null,
)

/**
 * 后端 WebSocket 协议编解码。
 * 认证方式：连接建立后立即发送 AUTH，token 放在 payload 内，绝不拼到 URL。
 */
object WsProtocol {
    val json: Json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun authMessage(deviceToken: String, requestId: String): String = encodeFrame(
        WsFrame(
            type = "AUTH",
            requestId = requestId,
            timestamp = System.currentTimeMillis(),
            payload = buildJsonObject { put("deviceToken", deviceToken) },
        ),
    )

    fun pingMessage(requestId: String): String = encodeFrame(
        WsFrame(
            type = "PING",
            requestId = requestId,
            timestamp = System.currentTimeMillis(),
            payload = buildJsonObject { },
        ),
    )

    // ── P4：会话 / WebRTC 信令 / 远程控制 ─────────────────────

    fun sessionConnectedMessage(sessionId: String, requestId: String? = null): String =
        encodeFrame(message("SESSION_CONNECTED", requestId, sessionId, buildJsonObject { }))

    fun webrtcOfferMessage(sessionId: String, requestId: String, sdp: String): String =
        encodeFrame(message("WEBRTC_OFFER", requestId, sessionId, buildJsonObject { put("sdp", sdp) }))

    fun webrtcAnswerMessage(sessionId: String, requestId: String, sdp: String): String =
        encodeFrame(message("WEBRTC_ANSWER", requestId, sessionId, buildJsonObject { put("sdp", sdp) }))

    fun iceCandidateMessage(sessionId: String, requestId: String, candidate: IceCandidate): String =
        encodeFrame(
            message(
                type = "ICE_CANDIDATE",
                requestId = requestId,
                sessionId = sessionId,
                payload = buildJsonObject {
                    put("candidate", candidate.candidate)
                    candidate.sdpMid?.let { put("sdpMid", it) }
                    candidate.sdpMLineIndex?.let { put("sdpMLineIndex", it) }
                    candidate.usernameFragment?.let { put("usernameFragment", it) }
                },
            ),
        )

    fun controlMessage(sessionId: String, requestId: String, command: ControlCommand): String =
        encodeFrame(
            message(
                type = "CONTROL",
                requestId = requestId,
                sessionId = sessionId,
                payload = buildJsonObject {
                    put("action", command.action)
                    when (command) {
                        is ControlCommand.Tap -> {
                            put("x", command.x)
                            put("y", command.y)
                        }

                        is ControlCommand.Swipe -> {
                            put("startX", command.startX)
                            put("startY", command.startY)
                            put("endX", command.endX)
                            put("endY", command.endY)
                            put("duration", command.duration)
                        }

                        ControlCommand.Back, ControlCommand.Home, ControlCommand.Recents -> Unit
                    }
                },
            ),
        )

    fun controlResultMessage(
        sessionId: String,
        requestId: String,
        ok: Boolean,
        reason: String? = null,
    ): String = encodeFrame(
        message(
            type = "CONTROL_RESULT",
            requestId = requestId,
            sessionId = sessionId,
            payload = buildJsonObject {
                put("scope", "COMMAND")
                put("result", if (ok) "OK" else "FAILED")
                reason?.let { put("reason", it) }
            },
        ),
    )

    fun permissionStatusMessage(sessionId: String, screenCapture: Boolean, accessibility: Boolean): String =
        encodeFrame(
            message(
                type = "PERMISSION_STATUS",
                requestId = null,
                sessionId = sessionId,
                payload = buildJsonObject {
                    put("screenCapture", screenCapture)
                    put("accessibility", accessibility)
                },
            ),
        )

    private fun message(
        type: String,
        requestId: String?,
        sessionId: String?,
        payload: JsonObject,
    ): WsFrame = WsFrame(
        type = type,
        requestId = requestId,
        sessionId = sessionId,
        timestamp = System.currentTimeMillis(),
        payload = payload,
    )

    private fun encodeFrame(frame: WsFrame): String = json.encodeToString(frame)

    fun parse(text: String): WsFrame? = runCatching { json.decodeFromString<WsFrame>(text) }.getOrNull()

    /** 由 REST baseUrl 推导 `/ws` 根路径；忽略 baseUrl 自带的路径前缀。 */
    fun toWebSocketUrl(baseUrl: String): String {
        val uri = URI(baseUrl)
        val scheme = when (uri.scheme?.lowercase()) {
            "https", "wss" -> "wss"
            "http", "ws" -> "ws"
            else -> throw IllegalArgumentException("Unsupported base URL scheme: ${uri.scheme}")
        }
        val host = uri.host ?: throw IllegalArgumentException("Base URL has no host: $baseUrl")
        return URI(scheme, null, host, uri.port, "/ws", null, null).toString()
    }
}
