package com.fantaysea.clan.data.ws

import com.fantaysea.clan.data.model.ControlCommand
import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.data.model.IceCandidate
import com.fantaysea.clan.data.repository.RemoteEvent
import java.time.Instant
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** 把服务端 WebSocket 帧映射为 UI 可消费的 [RemoteEvent]。 */
object WsEventMapper {

    fun map(frame: WsFrame): RemoteEvent? = when (frame.type) {
        "AUTH_SUCCESS" -> RemoteEvent.RealtimeReady

        "DEVICE_ONLINE" -> frame.toPresence(online = true)
        "DEVICE_OFFLINE" -> frame.toPresence(online = false)

        "PAIRING_REQUEST" -> {
            val requestId = frame.requestId?.takeIf { it.isNotBlank() } ?: return null
            val payload = frame.decodePayload<WsPairingRequestPayload>() ?: return null
            RemoteEvent.MatchRequest(
                requestId = requestId,
                code = "",
                requester = payload.requester.toDevice(),
            )
        }

        "PAIRING_RESULT" -> {
            val requestId = frame.requestId?.takeIf { it.isNotBlank() } ?: return null
            val payload = frame.decodePayload<WsPairingResultPayload>() ?: return null
            RemoteEvent.MatchResult(
                requestId = requestId,
                accepted = payload.result == "ACCEPTED",
                pairedDevice = null,
                expired = payload.result == "EXPIRED",
            )
        }

        "PAIRING_SUCCEEDED" -> {
            val requestId = frame.requestId?.takeIf { it.isNotBlank() } ?: return null
            val payload = frame.decodePayload<WsPairingSucceededPayload>() ?: return null
            RemoteEvent.PairingSucceeded(
                requestId = requestId,
                pairedDevice = payload.device.toDevice(),
            )
        }

        // ── P4：会话 ──────────────────────────────────────────
        "CONTROL_REQUEST" -> {
            val sessionId = frame.sessionId?.takeIf { it.isNotBlank() } ?: return null
            val payload = frame.payload ?: return null
            val controller = payload["controller"]?.toDeviceSummaryOrNull() ?: return null
            RemoteEvent.ControlRequest(
                sessionId = sessionId,
                controller = controller.toDevice(),
                expiresAt = payload.string("expiresAt").toEpochMillisOrNull(),
            )
        }

        "CONTROL_RESULT" -> {
            val sessionId = frame.sessionId?.takeIf { it.isNotBlank() } ?: return null
            val payload = frame.payload ?: return null
            when (payload.string("scope")) {
                "SESSION_REQUEST" -> {
                    val result = RemoteEvent.SessionRequestResult.entries
                        .firstOrNull { it.name == payload.string("result") } ?: return null
                    RemoteEvent.ControlResult(
                        sessionId = sessionId,
                        result = result,
                        reason = payload.string("reason"),
                    )
                }

                "COMMAND" -> {
                    val requestId = frame.requestId?.takeIf { it.isNotBlank() } ?: return null
                    val result = RemoteEvent.ControlResultStatus.entries
                        .firstOrNull { it.name == payload.string("result") } ?: return null
                    RemoteEvent.ControlCommandResult(
                        sessionId = sessionId,
                        requestId = requestId,
                        result = result,
                        reason = payload.string("reason"),
                    )
                }

                else -> null
            }
        }

        "SESSION_STARTED" -> {
            val sessionId = frame.sessionId?.takeIf { it.isNotBlank() } ?: return null
            RemoteEvent.SessionStarted(
                sessionId = sessionId,
                connectExpiresAt = frame.payload?.string("connectExpiresAt").toEpochMillisOrNull(),
            )
        }

        "SESSION_CONNECTED" -> {
            val sessionId = frame.sessionId?.takeIf { it.isNotBlank() } ?: return null
            RemoteEvent.SessionConnected(
                sessionId = sessionId,
                connectedAt = frame.payload?.string("connectedAt").toEpochMillisOrNull(),
            )
        }

        "SESSION_ENDED", "SESSION_FAILED" -> {
            val sessionId = frame.sessionId?.takeIf { it.isNotBlank() } ?: return null
            RemoteEvent.SessionEnded(
                sessionId = sessionId,
                reason = frame.payload?.string("reason"),
                failed = frame.type == "SESSION_FAILED",
            )
        }

        "CONTROL" -> {
            val sessionId = frame.sessionId?.takeIf { it.isNotBlank() } ?: return null
            val requestId = frame.requestId?.takeIf { it.isNotBlank() } ?: return null
            val payload = frame.payload ?: return null
            val command = ControlCommand.fromWire(
                action = payload.string("action") ?: return null,
                x = payload.double("x"),
                y = payload.double("y"),
                startX = payload.double("startX"),
                startY = payload.double("startY"),
                endX = payload.double("endX"),
                endY = payload.double("endY"),
                duration = payload.int("duration"),
            ) ?: return null
            RemoteEvent.ControlCommandEvent(sessionId, requestId, command)
        }

        "PERMISSION_STATUS" -> {
            val sessionId = frame.sessionId?.takeIf { it.isNotBlank() } ?: return null
            val payload = frame.payload ?: return null
            RemoteEvent.PermissionStatus(
                sessionId = sessionId,
                screenCapture = payload.boolean("screenCapture") ?: return null,
                accessibility = payload.boolean("accessibility") ?: return null,
            )
        }

        "WEBRTC_OFFER" -> {
            val sessionId = frame.sessionId?.takeIf { it.isNotBlank() } ?: return null
            val sdp = frame.payload?.string("sdp")?.takeIf { it.isNotBlank() } ?: return null
            RemoteEvent.WebRtcOffer(sessionId, frame.requestId?.takeIf { it.isNotBlank() }, sdp)
        }

        "WEBRTC_ANSWER" -> {
            val sessionId = frame.sessionId?.takeIf { it.isNotBlank() } ?: return null
            val sdp = frame.payload?.string("sdp")?.takeIf { it.isNotBlank() } ?: return null
            RemoteEvent.WebRtcAnswer(sessionId, frame.requestId?.takeIf { it.isNotBlank() }, sdp)
        }

        "ICE_CANDIDATE" -> {
            val sessionId = frame.sessionId?.takeIf { it.isNotBlank() } ?: return null
            val payload = frame.payload ?: return null
            val candidate = payload.string("candidate") ?: return null
            RemoteEvent.IceCandidateEvent(
                sessionId = sessionId,
                requestId = frame.requestId?.takeIf { it.isNotBlank() },
                candidate = IceCandidate(
                    candidate = candidate,
                    sdpMid = payload.string("sdpMid"),
                    sdpMLineIndex = payload.int("sdpMLineIndex"),
                    usernameFragment = payload.string("usernameFragment"),
                ),
            )
        }

        "ERROR" -> {
            val payload = frame.payload ?: return null
            RemoteEvent.SessionError(
                requestId = frame.requestId?.takeIf { it.isNotBlank() },
                sessionId = frame.sessionId?.takeIf { it.isNotBlank() },
                code = payload.string("code") ?: "INTERNAL_ERROR",
                message = payload.string("message") ?: "操作失败，请重试",
                retryAfter = payload.long("retryAfter"),
            )
        }

        // 未知事件静默忽略，保持连接安全。
        else -> null
    }

    /** 上下线帧只用于更新列表里的在线状态；缺少 deviceId 的畸形帧直接丢弃。 */
    private fun WsFrame.toPresence(online: Boolean): RemoteEvent.DevicePresence? {
        val payload = decodePayload<WsPresencePayload>() ?: return null
        if (payload.deviceId.isBlank()) return null
        return RemoteEvent.DevicePresence(
            deviceId = payload.deviceId,
            online = online,
            lastSeenAt = payload.lastSeenAt.toEpochMillisOrNull(),
        )
    }

    private fun String?.toEpochMillisOrNull(): Long? =
        this?.let { value -> runCatching { Instant.parse(value).toEpochMilli() }.getOrNull() }

    private fun JsonElement.toDeviceSummaryOrNull(): WsDeviceSummary? =
        runCatching { WsProtocol.json.decodeFromJsonElement<WsDeviceSummary>(this) }.getOrNull()

    private fun JsonObject.value(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

    private fun JsonObject.string(key: String): String? = value(key)?.contentOrNull
    private fun JsonObject.boolean(key: String): Boolean? = value(key)?.booleanOrNull
    private fun JsonObject.int(key: String): Int? = value(key)?.intOrNull
    private fun JsonObject.double(key: String): Double? = value(key)?.doubleOrNull
    private fun JsonObject.long(key: String): Long? = value(key)?.contentOrNull?.toLongOrNull()

    private inline fun <reified T> WsFrame.decodePayload(): T? {
        val element = this.payload ?: return null
        return runCatching { WsProtocol.json.decodeFromJsonElement<T>(element) }.getOrNull()
    }

    private fun WsDeviceSummary.toDevice() = Device(
        id = deviceId,
        deviceName = deviceName,
        platform = platform,
        model = model.orEmpty(),
        androidVersion = androidVersion,
        lastOnlineAt = null,
        createdAt = null,
    )
}
