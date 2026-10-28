package com.fantaysea.clan.data.repository

import com.fantaysea.clan.data.model.ControlCommand
import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.data.model.IceCandidate

/**
 * 设备列表中的 UI/领域视图: 表字段 + 服务端心跳推导出的在线状态。
 */
data class PairedDevice(
    val device: Device,
    val online: Boolean,
)

/**
 * 远程信令事件(服务端推送)。
 * 真实实现中, 这些事件由 [com.fantaysea.clan.data.ws.OkHttpWebSocketClient] 收到
 * [com.fantaysea.clan.data.ws.WsFrame] 后经 WsEventMapper 映射而来。
 */
sealed interface RemoteEvent {

    /** WebSocket 认证成功/重连成功: 客户端应刷新绑定列表做对账 */
    data object RealtimeReady : RemoteEvent

    /**
     * 已配对设备的上下线推送(服务端 DEVICE_ONLINE / DEVICE_OFFLINE)。
     * [lastSeenAt] 为服务端最后在线时间换算的 epoch millis, 解析失败时为 null。
     */
    data class DevicePresence(
        val deviceId: String,
        val online: Boolean,
        val lastSeenAt: Long? = null,
    ) : RemoteEvent

    /** 控制端提交了有效配对码 → 被控端弹出「配对确认」框 */
    data class MatchRequest(
        val requestId: String,
        val code: String,
        val requester: Device,
    ) : RemoteEvent

    /** 配对结果回推: 成功时若携带设备则直接展示, 否则等待 PairingSucceeded */
    data class MatchResult(
        val requestId: String,
        /**
         * accepted 为true 时认为双方的绑定关系已经存在，绑定已成功
         */
        val accepted: Boolean,
        val pairedDevice: Device?,
        val expired: Boolean = false,
    ) : RemoteEvent

    /** 配对成功后回推给被控端 */
    data class PairingSucceeded(
        val requestId: String,
        val pairedDevice: Device,
    ) : RemoteEvent

    /** 控制端创建会话 → 被控端弹出「控制确认」框；服务端用 sessionId 关联。 */
    data class ControlRequest(
        val sessionId: String,
        val controller: Device,
        val expiresAt: Long? = null,
    ) : RemoteEvent

    /** `CONTROL_RESULT.scope=SESSION_REQUEST` 的四种结果。 */
    enum class SessionRequestResult { ACCEPTED, REJECTED, EXPIRED, FAILED }

    /** 控制请求结果回推给双方；控制端据此结束等待，被控端据此关闭弹窗。 */
    data class ControlResult(
        val sessionId: String,
        val result: SessionRequestResult,
        val reason: String? = null,
    ) : RemoteEvent

    /** 会话进入 CONNECTING：双方据此进入各自会话页。 */
    data class SessionStarted(
        val sessionId: String,
        val connectExpiresAt: Long? = null,
    ) : RemoteEvent

    /** `SESSION_CONNECTED`：WebRTC 媒体链路已连通，控制端可开始发送 CONTROL。 */
    data class SessionConnected(
        val sessionId: String,
        val connectedAt: Long? = null,
    ) : RemoteEvent

    /** `SESSION_ENDED` / `SESSION_FAILED`。 */
    data class SessionEnded(
        val sessionId: String,
        val reason: String? = null,
        val failed: Boolean = false,
    ) : RemoteEvent

    /** 被控端收到的 CONTROL 指令。 */
    data class ControlCommandEvent(
        val sessionId: String,
        val requestId: String,
        val command: ControlCommand,
    ) : RemoteEvent

    enum class ControlResultStatus { OK, FAILED }

    /** 控制端收到的 `CONTROL_RESULT.scope=COMMAND`。 */
    data class ControlCommandResult(
        val sessionId: String,
        val requestId: String,
        val result: ControlResultStatus,
        val reason: String? = null,
    ) : RemoteEvent

    /** 被控端回传的投屏/无障碍权限状态。 */
    data class PermissionStatus(
        val sessionId: String,
        val screenCapture: Boolean,
        val accessibility: Boolean,
    ) : RemoteEvent

    /** WebRTC SDP 信令；供后续 WebRtcManager 消费。 */
    data class WebRtcOffer(
        val sessionId: String,
        val requestId: String? = null,
        val sdp: String,
    ) : RemoteEvent

    data class WebRtcAnswer(
        val sessionId: String,
        val requestId: String? = null,
        val sdp: String,
    ) : RemoteEvent

    data class IceCandidateEvent(
        val sessionId: String,
        val requestId: String? = null,
        val candidate: IceCandidate,
    ) : RemoteEvent

    /** 服务端 ERROR 帧：控制/信令被拒绝或限流时由 UI 提示。 */
    data class SessionError(
        val requestId: String? = null,
        val sessionId: String? = null,
        val code: String,
        val message: String,
        val retryAfter: Long? = null,
    ) : RemoteEvent
}
