package com.fantaysea.clan.data.ws

import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.data.repository.RemoteEvent
import java.time.Instant
import kotlinx.serialization.json.decodeFromJsonElement

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

        // 控制/会话事件属于后续阶段；未知事件静默忽略。
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
