package com.fantaysea.clan.data.ws

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** WebSocket 连接状态 */
enum class WsState { Disconnected, Connecting, Connected, Reconnecting }

/**
 * WebSocket 客户端接口(预留, 尚未实现)。
 *
 * 落地建议: OkHttp WebSocket + kotlinx.serialization;
 * 需具备: 断线自动重连(指数退避)、心跳保活、按 request_id 关联请求/响应。
 * 实现后将收到的 [WsMessage] 映射为 [com.fantaysea.clan.data.repository.RemoteEvent]
 * 转发给 [com.fantaysea.clan.data.repository.ClanRepository] 的事件流。
 */
interface WebSocketClient {
    val state: StateFlow<WsState>
    val incoming: Flow<WsMessage>

    suspend fun connect(url: String, deviceToken: String)
    suspend fun send(message: WsMessage)
    suspend fun disconnect()
}
