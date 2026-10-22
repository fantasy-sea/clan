package com.fantaysea.clan.data.remote

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface ClanApi {
    @POST("api/v1/devices/register")
    suspend fun registerDevice(@Body request: RegisterDeviceRequest): Response<ApiEnvelope<RegisterDeviceResponse>>

    @GET("api/v1/devices/me")
    suspend fun getCurrentDevice(@Header("Authorization") authorization: String): Response<ApiEnvelope<DeviceResponse>>
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
