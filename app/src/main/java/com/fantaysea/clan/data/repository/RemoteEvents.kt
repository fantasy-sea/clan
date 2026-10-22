package com.fantaysea.clan.data.repository

import com.fantaysea.clan.data.model.Device

/**
 * 设备列表中的 UI/领域视图: 表字段 + 服务端心跳推导出的在线状态。
 */
data class PairedDevice(
    val device: Device,
    val online: Boolean,
)

/**
 * 远程信令事件(服务端推送 / 本地 Mock 广播)。
 * 真实实现中, 这些事件由 [com.fantaysea.clan.data.ws.WebSocketClient] 收到
 * [com.fantaysea.clan.data.ws.WsMessage] 后映射而来。
 */
sealed interface RemoteEvent {

    /** 控制端提交了有效配对码 → 被控端弹出「配对确认」框 */
    data class MatchRequest(
        val requestId: String,
        val code: String,
        val requester: Device,
    ) : RemoteEvent

    /** 配对结果回推给控制端: 成功时携带新配对进来的被控设备 */
    data class MatchResult(
        val requestId: String,
        val accepted: Boolean,
        val pairedDevice: Device?,
    ) : RemoteEvent

    /** 配对成功后回推给被控端 */
    data class PairingSucceeded(
        val requestId: String,
        val pairedDevice: Device,
    ) : RemoteEvent

    /** 控制端发起控制请求 → 被控端弹出「控制确认」框 */
    data class ControlRequest(
        val requestId: String,
        val controller: Device,
    ) : RemoteEvent

    /** 控制请求结果回推给控制端 */
    data class ControlResult(
        val requestId: String,
        val accepted: Boolean,
    ) : RemoteEvent

    /** 会话已建立: controller=控制方, controlled=被控方, 双方据此进入对应页面 */
    data class SessionStarted(
        val requestId: String,
        val controller: Device,
        val controlled: Device,
    ) : RemoteEvent

    /** 会话结束 */
    data class SessionEnded(
        val peer: Device,
    ) : RemoteEvent
}
