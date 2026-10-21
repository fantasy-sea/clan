package com.fantaysea.clan.data.repository

import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.data.model.PairingCode
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class DeviceRegistration(val deviceName: String, val model: String, val androidVersion: String)

enum class BackendCapability {
    DEVICE_IDENTITY, DEVICE_BINDINGS, PAIRING, CONTROL_SESSIONS, REALTIME_SIGNALING,
}

interface ClanRepository {
    val capabilities: Set<BackendCapability>
    val events: SharedFlow<RemoteEvent>
    val self: StateFlow<Device?>

    suspend fun initializeSelf(registration: DeviceRegistration): Device
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

open class ClanServiceException(message: String, cause: Throwable? = null) : Exception(message, cause)
class BackendCapabilityUnavailableException(val capability: BackendCapability) : ClanServiceException("服务端暂未支持")
class ClanApiException(
    val businessCode: Int?, val httpStatus: Int?, message: String, cause: Throwable? = null,
) : ClanServiceException(message, cause)

class InvalidPairingCodeException(message: String) : Exception(message)
class ExpiredPairingCodeException(message: String) : Exception(message)
class DeviceOfflineException(message: String) : Exception(message)
