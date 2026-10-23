package com.fantaysea.clan.data.repository

import androidx.compose.runtime.Immutable
import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.data.model.PairingCode
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

    /**设备绑定  （/ 解绑 / 查
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

interface ClanRepository {
    /**
     * 当前后端所支持的功能
     */
    val capabilities: Set<BackendCapability>
    val events: SharedFlow<RemoteEvent>
    val self: StateFlow<Device?>

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
    suspend fun createPairingCode(): PairingCode
    suspend fun submitPairingCode(code: String): String
    suspend fun respondMatch(requestId: String, accept: Boolean)
    suspend fun sendControlRequest(deviceId: String): String
    suspend fun respondControl(requestId: String, accept: Boolean)
    suspend fun endSession(peerDeviceId: String)
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
