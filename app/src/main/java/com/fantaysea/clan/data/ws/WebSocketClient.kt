package com.fantaysea.clan.data.ws

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** WebSocket 连接状态 */
enum class WsState { Disconnected, Connecting, Connected, Reconnecting }

/**
 * 实时连接失败原因。
 *
 * 认证被拒（设备令牌失效/被服务端限流）与网络不可达是两类完全不同的问题：
 * 前者继续重试永远不会成功，必须重新注册设备；后者只需等待客户端退避重连。
 * 分开表达才能给用户可行动的提示，也便于定位线上问题。
 */
sealed interface WsFailure {

    /** 服务端回复 AUTH_FAILED：令牌失效或被限流，重试没有意义。 */
    data class AuthRejected(val code: Int?, val serverMessage: String?) : WsFailure

    /** 握手/网络失败：连不上、服务端返回非 101、连接被中断等，客户端会自动重连。 */
    data class Transport(val reason: String) : WsFailure

    /** 面向用户的提示；只包含状态码和连接信息，不含设备令牌。 */
    val message: String
        get() = when (this) {
            is AuthRejected -> buildString {
                append("实时连接认证失败")
                code?.let { append("（错误码 ").append(it).append("）") }
            }
            is Transport -> "实时连接失败：$reason"
        }
}

/**
 * WebSocket 客户端接口。
 *
 * 实现需要：连接后立即 AUTH、20 秒 PING 保活、断线重连、把服务端帧原样
 * 交给 [incoming]；上层再通过 [WsEventMapper] 映射为业务事件。
 * 同一个 URL + token 的 [start] 必须幂等。
 */
interface WebSocketClient {
    val state: StateFlow<WsState>
    val incoming: SharedFlow<WsFrame>

    /** 最近一次连接失败原因；连接成功后清空。 */
    val failure: StateFlow<WsFailure?>

    fun start(url: String, deviceToken: String)
    fun stop()
}
