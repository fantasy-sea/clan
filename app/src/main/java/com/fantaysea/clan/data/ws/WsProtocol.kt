package com.fantaysea.clan.data.ws

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

    fun authMessage(deviceToken: String, requestId: String): String = json.encodeToString(
        WsFrame(
            type = "AUTH",
            requestId = requestId,
            timestamp = System.currentTimeMillis(),
            payload = buildJsonObject { put("deviceToken", deviceToken) },
        ),
    )

    fun pingMessage(requestId: String): String = json.encodeToString(
        WsFrame(
            type = "PING",
            requestId = requestId,
            timestamp = System.currentTimeMillis(),
            payload = buildJsonObject { },
        ),
    )

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
