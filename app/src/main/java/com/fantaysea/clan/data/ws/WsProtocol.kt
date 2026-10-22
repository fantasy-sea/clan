package com.fantaysea.clan.data.ws

import com.fantaysea.clan.data.model.Device

/**
 * WebSocket 信令消息协议(预留, 服务端就绪后启用)。
 *
 * 消息帧建议结构: { "type": "match_request", "request_id": "...", "payload": { ... } }。
 * 这里以密封类表达类型安全的协议视图, 后续用 kotlinx.serialization 做 JSON 编解码。
 */
sealed class WsMessage {

    /** 建立连接后上报本机身份 */
    data class Hello(val deviceToken: String) : WsMessage()

    /** 控制端提交匹配码 */
    data class PairCodeSubmit(val code: String) : WsMessage()

    /** 服务端推给被控端: 有控制端请求配对 */
    data class MatchRequest(
        val requestId: String,
        val requester: Device,
    ) : WsMessage()

    /** 被控端应答配对 */
    data class MatchResponse(
        val requestId: String,
        val accept: Boolean,
    ) : WsMessage()

    /** 服务端推给控制端: 配对成功, 携带新设备 */
    data class PairingConfirmed(val pairedDevice: Device) : WsMessage()

    /** 服务端推给被控端: 有控制端请求控制 */
    data class ControlRequest(
        val requestId: String,
        val controller: Device,
    ) : WsMessage()

    /** 被控端应答控制 */
    data class ControlResponse(
        val requestId: String,
        val accept: Boolean,
    ) : WsMessage()

    /** 服务端推给双方: 会话建立 */
    data class SessionStarted(val peer: Device) : WsMessage()

    /** 服务端推给双方: 会话结束 */
    data class SessionEnded(val peer: Device) : WsMessage()

    /** 错误回执(code 供客户端定位) */
    data class Error(val code: String, val message: String) : WsMessage()

    /** 心跳(客户端定时发送, 服务端回 Pong 保持在线状态) */
    object Heartbeat : WsMessage()

    object Pong : WsMessage()
}
