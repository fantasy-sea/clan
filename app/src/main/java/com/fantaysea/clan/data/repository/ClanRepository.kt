package com.fantaysea.clan.data.repository

import androidx.compose.runtime.Immutable
import com.fantaysea.clan.data.model.ActiveSession
import com.fantaysea.clan.data.model.ControlCommand
import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.data.model.IceCandidate
import com.fantaysea.clan.data.model.LocalPermissions
import com.fantaysea.clan.data.model.PairingCode
import com.fantaysea.clan.data.model.SessionInfo
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 设备注册信息
 */
@Immutable
data class DeviceRegistration(
    val deviceName: String,
    val model: String,
    val androidVersion: String
)

enum class BackendCapability {
    /**设备标识 （注册 / 获取本机信息）*/
    DEVICE_IDENTITY,

    /**设备绑定（/ 解绑 / 查
    找）*/
    DEVICE_BINDINGS,

    /**设备配对  （生成/提交配对码、应答
    匹配）*/
    PAIRING,

    /**设备控制会话  （发起/应答控制、结束会
    话）*/
    CONTROL_SESSIONS,

    /**设备实时信号 （WebSocket 推送） */
    REALTIME_SIGNALING,
}

/**
 * Repository 在Android 开发中属于数据层 用于数据从哪来、怎么缓存怎么聚合多个数据源、怎么对外暴露
 * 所以该类用于对接 后端的
 */
interface ClanRepository {
    /**
     * 存放当前后端所支持的功能
     */
    val capabilities: Set<BackendCapability>
    val events: SharedFlow<RemoteEvent>
    val self: StateFlow<Device?>

    /** 本机当前参与的远程会话；会话结束/失败后清空。 */
    val activeSession: StateFlow<ActiveSession?>

    /** 本机（被控端）的系统权限，供 PERMISSION_STATUS 回传。 */
    val localPermissions: StateFlow<LocalPermissions>

    /**
     * 初始化注册本设备
     */
    suspend fun initializeSelf(registration: DeviceRegistration): Device

    /**
     * 获取设备列表
     */
    suspend fun fetchDevices(): List<PairedDevice>
    suspend fun findDevice(deviceId: String): Device?
    suspend fun unbindDevice(deviceId: String)

    /**
     * 修改已配对设备的设备名称(服务端 PATCH /api/v1/bindings/{deviceId})。
     * 成功后列表重新拉取即可看到新名称。
     */
    suspend fun updateDeviceName(deviceId: String, deviceName: String)
    suspend fun createPairingCode(): PairingCode
    suspend fun submitPairingCode(code: String): String
    suspend fun respondMatch(requestId: String, accept: Boolean)
    suspend fun cancelPairingRequest(requestId: String)

    // ── P4：远程会话与控制 ───────────────────────────────────

    /** 控制端创建会话；成功后 activeSession 记录本机为 CONTROLLER。 */
    suspend fun sendControlRequest(device: Device): SessionInfo

    /** 被控端接受/拒绝会话；接受成功后 activeSession 记录本机为 CONTROLLED。 */
    suspend fun respondControl(sessionId: String, accept: Boolean): SessionInfo

    /** 结束当前会话；对已结束的会话按幂等处理。 */
    suspend fun endSession(sessionId: String)

    /** 发送 CONTROL，返回本次请求的 requestId，供 CONTROL_RESULT 关联。 */
    suspend fun sendControl(sessionId: String, command: ControlCommand): String

    /** 被控端回传命令执行结果。 */
    suspend fun sendControlResult(sessionId: String, requestId: String, ok: Boolean, reason: String? = null)

    /** 被控端回传投屏/无障碍权限状态。 */
    suspend fun sendPermissionStatus(sessionId: String, screenCapture: Boolean, accessibility: Boolean)

    /** WebRTC 连通后由 [com.fantaysea.clan.media.WebRtcManager] 发送；服务端幂等。 */
    suspend fun sendSessionConnected(sessionId: String)

    // WebRTC 信令；媒体层接入后由 WebRtcManager 调用。
    suspend fun sendWebRtcOffer(sessionId: String, requestId: String, sdp: String)
    suspend fun sendWebRtcAnswer(sessionId: String, requestId: String, sdp: String)
    suspend fun sendIceCandidate(sessionId: String, requestId: String, candidate: IceCandidate)

    /** 更新本机权限状态；被控会话页据此发送 PERMISSION_STATUS。 */
    fun updateLocalPermissions(screenCapture: Boolean, accessibility: Boolean)

    /** 释放仓库持有的实时连接；默认无操作，未持有实时连接的实现无需处理。 */
    fun close() {}
}

open class ClanServiceException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

class BackendCapabilityUnavailableException(val capability: BackendCapability) :
    ClanServiceException("服务端暂未支持")

class ClanApiException(
    val businessCode: Int?, val httpStatus: Int?, message: String, cause: Throwable? = null,
) : ClanServiceException(message, cause)

class InvalidPairingCodeException(message: String) : Exception(message)
class ExpiredPairingCodeException(message: String) : Exception(message)
class DeviceOfflineException(message: String) : Exception(message)
