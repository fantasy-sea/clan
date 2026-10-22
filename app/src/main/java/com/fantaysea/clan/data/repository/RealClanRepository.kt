package com.fantaysea.clan.data.repository

import com.fantaysea.clan.data.DeviceCredentialStore
import com.fantaysea.clan.data.DeviceCredentials
import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.data.model.PairingCode
import com.fantaysea.clan.data.remote.ApiEnvelope
import com.fantaysea.clan.data.remote.ClanApi
import com.fantaysea.clan.data.remote.DeviceResponse
import com.fantaysea.clan.data.remote.RegisterDeviceRequest
import java.time.Instant
import java.io.IOException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class RealClanRepository(
    private val api: ClanApi,
    private val credentialStore: DeviceCredentialStore,
    private val json: Json = defaultJson,
) : ClanRepository {
    override val capabilities: Set<BackendCapability> = setOf(BackendCapability.DEVICE_IDENTITY)

    private val mutableSelf = MutableStateFlow<Device?>(null)
    override val self: StateFlow<Device?> = mutableSelf.asStateFlow()
    private val mutableEvents = MutableSharedFlow<RemoteEvent>()
    override val events: SharedFlow<RemoteEvent> = mutableEvents.asSharedFlow()
    private val initializationMutex = Mutex()

    override suspend fun initializeSelf(registration: DeviceRegistration): Device =
        initializationMutex.withLock {
            mutableSelf.value?.let { return@withLock it }
            var credentials = credentialStore.load() ?: register(registration)
            val device = try {
                loadCurrentDevice(credentials.deviceToken)
            } catch (error: ClanApiException) {
                if (error.businessCode != INVALID_DEVICE_TOKEN) throw error
                credentialStore.clear()
                credentials = register(registration)
                try {
                    loadCurrentDevice(credentials.deviceToken)
                } catch (retryError: ClanApiException) {
                    if (retryError.businessCode == INVALID_DEVICE_TOKEN) credentialStore.clear()
                    throw retryError
                }
            }
            mutableSelf.value = device
            device
        }

    private suspend fun register(registration: DeviceRegistration): DeviceCredentials {
        val response = safeApiCall {
            api.registerDevice(
                RegisterDeviceRequest(
                    deviceName = registration.deviceName,
                    platform = "ANDROID",
                    model = registration.model,
                    androidVersion = registration.androidVersion,
                ),
            )
        }.requireData()
        if (response.deviceId.isBlank() || response.deviceToken.isBlank()) {
            throw ClanApiException(null, null, "注册响应缺少设备凭据")
        }
        return DeviceCredentials(response.deviceId, response.deviceToken).also { credentialStore.save(it) }
    }

    private suspend fun loadCurrentDevice(token: String): Device = safeApiCall {
        api.getCurrentDevice("Bearer $token")
    }.requireData().toDomain()

    override suspend fun fetchDevices(): List<PairedDevice> = unsupported(BackendCapability.DEVICE_BINDINGS)

    override suspend fun findDevice(deviceId: String): Device? {
        val current = self.value
        if (current?.id == deviceId) return current
        return unsupported(BackendCapability.DEVICE_BINDINGS)
    }

    override suspend fun unbindDevice(deviceId: String): Unit = unsupported(BackendCapability.DEVICE_BINDINGS)
    override suspend fun createPairingCode(): PairingCode = unsupported(BackendCapability.PAIRING)
    override suspend fun submitPairingCode(code: String): String = unsupported(BackendCapability.PAIRING)
    override suspend fun respondMatch(requestId: String, accept: Boolean): Unit = unsupported(BackendCapability.PAIRING)
    override suspend fun sendControlRequest(deviceId: String): String = unsupported(BackendCapability.CONTROL_SESSIONS)
    override suspend fun respondControl(requestId: String, accept: Boolean): Unit = unsupported(BackendCapability.CONTROL_SESSIONS)
    override suspend fun endSession(peerDeviceId: String): Unit = unsupported(BackendCapability.CONTROL_SESSIONS)

    private fun <T> unsupported(capability: BackendCapability): T =
        throw BackendCapabilityUnavailableException(capability)

    private fun DeviceResponse.toDomain() = Device(
        id = deviceId,
        deviceName = deviceName,
        platform = platform,
        model = model.orEmpty(),
        androidVersion = androidVersion,
        lastOnlineAt = lastSeenAt.toEpochMillisOrNull(),
        createdAt = createdAt.toEpochMillisOrNull(),
    )

    private fun String?.toEpochMillisOrNull(): Long? =
        this?.let { value -> runCatching { Instant.parse(value).toEpochMilli() }.getOrNull() }

    private fun <T> Response<ApiEnvelope<T>>.requireData(): T {
        if (!isSuccessful) {
            val parsed = errorBody()?.string()?.let { body ->
                try {
                    json.decodeFromString<ApiEnvelope<JsonElement>>(body)
                } catch (_: SerializationException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
            throw ClanApiException(parsed?.code, code(), parsed?.message ?: "服务请求失败 (${code()})")
        }
        val envelope = body() ?: throw ClanApiException(null, code(), "服务响应为空")
        if (envelope.code != SUCCESS) throw ClanApiException(envelope.code, code(), envelope.message)
        return envelope.data ?: throw ClanApiException(envelope.code, code(), "服务响应缺少数据")
    }

    private suspend fun <T> safeApiCall(call: suspend () -> T): T = try {
        call()
    } catch (error: ClanServiceException) {
        throw error
    } catch (error: SerializationException) {
        throw ClanApiException(null, null, "服务响应格式错误", error)
    } catch (error: IOException) {
        throw ClanApiException(null, null, "无法连接服务端", error)
    } catch (error: RuntimeException) {
        throw ClanApiException(null, null, "服务请求失败", error)
    }

    companion object {
        private const val SUCCESS = 0
        private const val INVALID_DEVICE_TOKEN = 40002

        val defaultJson: Json = Json { ignoreUnknownKeys = true; explicitNulls = false }

        fun create(baseUrl: String, credentialStore: DeviceCredentialStore): RealClanRepository {
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(defaultJson.asConverterFactory("application/json".toMediaType()))
                .build()
            return RealClanRepository(retrofit.create(ClanApi::class.java), credentialStore)
        }
    }
}
