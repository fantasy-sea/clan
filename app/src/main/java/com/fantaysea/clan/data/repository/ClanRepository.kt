package com.fantaysea.clan.data.repository

import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.data.model.PairingCode
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 远程数据仓库接口 —— UI 层只依赖此抽象。
 *
 * 当前由 [com.fantaysea.clan.data.mock.MockServer] 在本地内存中模拟服务端行为;
 * 服务端就绪后, 提供基于 [com.fantaysea.clan.data.remote.ClanApi](REST 资源操作)
 * 与 [com.fantaysea.clan.data.ws.WebSocketClient](实时信令) 的实现替换即可, UI 不变。
 */
interface ClanRepository {

    /** 信令事件流(匹配/配对/控制/会话)。 */
    val events: SharedFlow<RemoteEvent>

    /** 本机身份(registerSelf 后非空)。 */
    val self: StateFlow<Device?>

    /** 注册本机身份(应用启动时调用一次), 后续提交配对码/控制请求都以它为发起方。 */
    suspend fun registerSelf(
        deviceToken: String,
        deviceName: String,
        platform: String,
        model: String,
    )

    /** 已配对设备列表(含在线状态)。 */
    suspend fun fetchDevices(): List<PairedDevice>

    /** 解除与某设备的绑定(左滑删除)。 */
    suspend fun unbindDevice(deviceId: Long)

    /** 被控端: 生成新的 6 位匹配码(60s 有效)。 */
    suspend fun createPairingCode(): PairingCode

    /**
     * 控制端: 提交匹配码。校验通过后服务端向被控端推送 [RemoteEvent.MatchRequest],
     * 本方法返回该请求的 requestId 供后续匹配 [RemoteEvent.MatchResult]。
     *
     * @throws InvalidPairingCodeException 码不存在或格式错误
     * @throws ExpiredPairingCodeException 码已过期
     */
    suspend fun submitPairingCode(code: String): String

    /** 被控端: 应答配对请求。accept=true 时服务端建立绑定并向控制端回推新设备。 */
    suspend fun respondMatch(requestId: String, accept: Boolean)

    /**
     * 控制端: 对已在线设备发起控制请求, 返回 requestId。
     * 服务端向被控端推送 [RemoteEvent.ControlRequest]。
     */
    suspend fun sendControlRequest(deviceId: Long): String

    /** 被控端: 应答控制请求。accept=true 时服务端向双方推送 [RemoteEvent.SessionStarted]。 */
    suspend fun respondControl(requestId: String, accept: Boolean)

    /** 结束进行中的会话。 */
    suspend fun endSession(peerDeviceId: Long)
}

/** 匹配码不存在或格式错误。 */
class InvalidPairingCodeException(message: String) : Exception(message)

/** 匹配码已过期(超过 60 秒)。 */
class ExpiredPairingCodeException(message: String) : Exception(message)

/** 目标设备当前不在线。 */
class DeviceOfflineException(message: String) : Exception(message)
