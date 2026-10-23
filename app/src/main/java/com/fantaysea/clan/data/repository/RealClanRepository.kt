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
    //开发阶段 静态声明用于告知前端目前服务端支持了哪些功能
    override val capabilities: Set<BackendCapability> = setOf(BackendCapability.DEVICE_IDENTITY)
    private val mutableSelf = MutableStateFlow<Device?>(null)
    override val self: StateFlow<Device?> = mutableSelf.asStateFlow()
    private val mutableEvents = MutableSharedFlow<RemoteEvent>()
    override val events: SharedFlow<RemoteEvent> = mutableEvents.asSharedFlow()

    /**
     * 协程互斥锁，用于控制多个协程对共享资源的并发访问，避免多个协程同时修改同一份数据而导致问题
     */
    private val initializationMutex = Mutex()

    override suspend fun initializeSelf(registration: DeviceRegistration): Device =
        //withLock：获取锁执行创建协程执行代码随后自动释放锁
        initializationMutex.withLock {
            //如果已经有设备信息，直接返回
            mutableSelf.value?.let { return@withLock it }
            //从本机存储中读取是否有设备凭证，没有则进行注册流程
            var credentials = credentialStore.load() ?: register(registration)
            val device = try {
                //拿到设备凭证后获取当前设备信息
                loadCurrentDevice(credentials.deviceToken)
            } catch (error: ClanApiException) {
                //设备凭据获取设备信息遇到问题，判断是否为无效令牌。是的话重新注册，不是的话问题不简单
                if (error.businessCode != INVALID_DEVICE_TOKEN) throw error
                credentialStore.clear()
                credentials = register(registration)
                try {
                    //重新注册了设备凭据后重新获取设备信息
                    loadCurrentDevice(credentials.deviceToken)
                } catch (retryError: ClanApiException) {
                    //重试还是发生错误，还是令牌无效的话不再重新注册。清空本地的设备凭据
                    if (retryError.businessCode == INVALID_DEVICE_TOKEN) credentialStore.clear()
                    //重试发生了非令牌无效的错误无法解决了
                    throw retryError
                }
            }
            //将获取到的设备信息保存到内存并返回设备信息
            mutableSelf.value = device
            device
        }

    private suspend fun register(registration: DeviceRegistration): DeviceCredentials {
        //发起设备注册请求
        val response = safeApiCall {
            api.registerDevice(
                RegisterDeviceRequest(
                    deviceName = registration.deviceName,
                    platform = "ANDROID",
                    model = registration.model,
                    androidVersion = registration.androidVersion,
                )
            )
        }.requireData()
        if (response.deviceId.isBlank() || response.deviceToken.isBlank()) {
            throw ClanApiException(null, null, "注册响应缺少设备凭据")
        }
        return DeviceCredentials(
            response.deviceId,
            response.deviceToken
        ).also { credentialStore.save(it) }
    }

    /**
     * 获取指定的设备信息
     */
    private suspend fun loadCurrentDevice(token: String): Device = safeApiCall {
        api.getCurrentDevice("Bearer $token")
    }.requireData().toDeviceDomain()

    override suspend fun fetchDevices(): List<PairedDevice> =
        unsupported(BackendCapability.DEVICE_BINDINGS)

    override suspend fun findDevice(deviceId: String): Device? {
        val current = self.value
        if (current?.id == deviceId) return current
        return unsupported(BackendCapability.DEVICE_BINDINGS)
    }

    override suspend fun unbindDevice(deviceId: String): Unit =
        unsupported(BackendCapability.DEVICE_BINDINGS)

    override suspend fun createPairingCode(): PairingCode = unsupported(BackendCapability.PAIRING)
    override suspend fun submitPairingCode(code: String): String =
        unsupported(BackendCapability.PAIRING)

    override suspend fun respondMatch(requestId: String, accept: Boolean): Unit =
        unsupported(BackendCapability.PAIRING)

    override suspend fun sendControlRequest(deviceId: String): String =
        unsupported(BackendCapability.CONTROL_SESSIONS)

    override suspend fun respondControl(requestId: String, accept: Boolean): Unit =
        unsupported(BackendCapability.CONTROL_SESSIONS)

    override suspend fun endSession(peerDeviceId: String): Unit =
        unsupported(BackendCapability.CONTROL_SESSIONS)

    private fun <T> unsupported(capability: BackendCapability): T =
        throw BackendCapabilityUnavailableException(capability)

    private fun DeviceResponse.toDeviceDomain() = Device(
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

    /**
     * 处理响应的数据
     */
    private fun <T> Response<ApiEnvelope<T>>.requireData(): T {
        //响应失败
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
            throw ClanApiException(
                parsed?.code,
                code(),
                parsed?.message ?: "服务请求失败 (${code()})"
            )
        }
        //处理响应成功
        //1.判断响应体是否存在
        val envelope = body() ?: throw ClanApiException(null, code(), "服务响应为空")
        //2.判断响应码是否成功
        if (envelope.code != SUCCESS) throw ClanApiException(
            envelope.code,
            code(),
            envelope.message
        )
        //3.判断是否拿到了响应体中的数据
        return envelope.data ?: throw ClanApiException(envelope.code, code(), "服务响应缺少数据")
    }

    /**
     * 处理传入的请求遇到的异常
     * 若请求正常会将结果原封不动返回
     */
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
