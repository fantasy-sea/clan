package com.fantaysea.clan.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface ClanApi {
    @POST("api/v1/devices/register")
    suspend fun registerDevice(@Body request: RegisterDeviceRequest): Response<ApiEnvelope<RegisterDeviceResponse>>

    @GET("api/v1/devices/me")
    suspend fun getCurrentDevice(@Header("Authorization") authorization: String): Response<ApiEnvelope<DeviceResponse>>

    /**
     * 修改已配对设备(绑定关系上对方设备)的名称。
     * 服务端 `PATCH /api/v1/bindings/{deviceId}` 会校验调用方与目标设备已绑定,
     * 更新目标设备的 deviceName 并返回最新绑定。
     */
    @PATCH("api/v1/bindings/{deviceId}")
    suspend fun renameBinding(
        @Header("Authorization") authorization: String,
        @Path("deviceId") deviceId: String,
        @Body request: RenameDeviceRequest,
    ): Response<ApiEnvelope<BindingResponse>>

    @GET("api/v1/bindings")
    suspend fun listBindings(@Header("Authorization") authorization: String): Response<ApiEnvelope<List<BindingResponse>>>

    @GET("api/v1/bindings/{deviceId}")
    suspend fun getBinding(
        @Header("Authorization") authorization: String,
        @Path("deviceId") deviceId: String,
    ): Response<ApiEnvelope<BindingResponse>>

    @DELETE("api/v1/bindings/{deviceId}")
    suspend fun deleteBinding(
        @Header("Authorization") authorization: String,
        @Path("deviceId") deviceId: String,
    ): Response<ApiEnvelope<JsonElement>>

    @POST("api/v1/pairing/codes")
    suspend fun createPairingCode(
        @Header("Authorization") authorization: String,
    ): Response<ApiEnvelope<PairingCodeResponse>>

    @POST("api/v1/pairing/verify")
    suspend fun verifyPairingCode(
        @Header("Authorization") authorization: String,
        @Body request: PairingVerifyRequest,
    ): Response<ApiEnvelope<PairingVerifyResponse>>

    @POST("api/v1/pairing/requests/{requestId}/accept")
    suspend fun acceptPairingRequest(
        @Header("Authorization") authorization: String,
        @Path("requestId") requestId: String,
    ): Response<ApiEnvelope<PairingRequestResponse>>

    @POST("api/v1/pairing/requests/{requestId}/reject")
    suspend fun rejectPairingRequest(
        @Header("Authorization") authorization: String,
        @Path("requestId") requestId: String,
    ): Response<ApiEnvelope<PairingRequestResponse>>

    @POST("api/v1/pairing/requests/{requestId}/cancel")
    suspend fun cancelPairingRequest(
        @Header("Authorization") authorization: String,
        @Path("requestId") requestId: String,
    ): Response<ApiEnvelope<PairingRequestResponse>>
}

@Serializable
data class ApiEnvelope<T>(val code: Int, val message: String, val data: T? = null)

@Serializable
data class RegisterDeviceRequest(
    val deviceName: String,
    val platform: String,
    val model: String,
    val androidVersion: String,
)

@Serializable
data class RegisterDeviceResponse(val deviceId: String, val deviceToken: String)

/**
 * PATCH /api/v1/bindings/{deviceId} 请求。
 */
@Serializable
data class RenameDeviceRequest(
    val deviceName: String,
)

@Serializable
data class DeviceResponse(
    val deviceId: String,
    val deviceName: String,
    val platform: String,
    val model: String? = null,
    val androidVersion: String? = null,
    val online: Boolean = false,
    val lastSeenAt: String? = null,
    val createdAt: String? = null,
)

/** 绑定列表/单条查询共用的响应；字段与后端 BindingResponse 对齐。 */
@Serializable
data class BindingResponse(
    val deviceId: String,
    val deviceName: String,
    val platform: String,
    val model: String? = null,
    val androidVersion: String? = null,
    val online: Boolean = false,
    val lastSeenAt: String? = null,
    val boundAt: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class PairingCodeResponse(
    val pairingCode: String,
    val expiresAt: String,
)

@Serializable
data class PairingVerifyRequest(val pairingCode: String)

@Serializable
data class PairingVerifyResponse(
    val requestId: String,
    val expiresAt: String,
)

@Serializable
data class PairingRequestResponse(
    val requestId: String,
    val status: String,
    val expiresAt: String,
    val handledAt: String? = null,
)
