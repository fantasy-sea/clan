package com.fantaysea.clan.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/**
 * 设备凭证对象。
 *
 * reported* 记录上次成功注册时上报的客户端 fingerprint，用于判断 OAID 是否第一次
 * 出现/发生变化，从而决定是否做一次 best-effort 补挂注册。它们只在本地使用，不参与
 * 任何认证逻辑。
 */
data class DeviceCredentials(
    val deviceId: String,
    val deviceToken: String,
    val reportedOaidFingerprint: String? = null,
    val reportedAndroidIdFingerprint: String? = null,
)

/**
 * 设备凭证存储
 * DeviceCredentials 本质是服务端下发的“设备身份证 + 访问令牌”。
 * 把这两个值保存在本地，是为了让 App 只注册一次，之后每
 *      次启动都能以同一台设备的身份访问后端，而不是每次都创建一个
 *      新的设备记录。
 */
interface DeviceCredentialStore {
    suspend fun load(): DeviceCredentials?
    suspend fun save(credentials: DeviceCredentials)
    suspend fun clear()
}

//获取本次存储的设备凭据dataStore对象
private val Context.deviceCredentialDataStore by preferencesDataStore(name = "device_credentials")

/** Android应用私有凭据存储。设备 id 与 token 始终在同一个 DataStore 事务中更新。 */
class AndroidDeviceCredentialStore(private val context: Context) : DeviceCredentialStore {
    private val deviceIdKey = stringPreferencesKey("device_id")
    private val deviceTokenKey = stringPreferencesKey("device_token")
    private val reportedOaidKey = stringPreferencesKey("reported_oaid_fingerprint")
    private val reportedAndroidIdKey = stringPreferencesKey("reported_android_id_fingerprint")

    override suspend fun load(): DeviceCredentials? {
        val preferences = context.deviceCredentialDataStore.data.first()
        val deviceId = preferences[deviceIdKey]
        val deviceToken = preferences[deviceTokenKey]
        if (deviceId.isNullOrBlank() || deviceToken.isNullOrBlank()) {
            if (deviceId != null || deviceToken != null) clear()
            return null
        }
        return DeviceCredentials(
            deviceId = deviceId,
            deviceToken = deviceToken,
            reportedOaidFingerprint = preferences[reportedOaidKey]?.takeIf { it.isNotBlank() },
            reportedAndroidIdFingerprint = preferences[reportedAndroidIdKey]?.takeIf { it.isNotBlank() },
        )
    }

    override suspend fun save(credentials: DeviceCredentials) {
        //通过require 校验设备凭证对象
        require(credentials.deviceId.isNotBlank())
        require(credentials.deviceToken.isNotBlank())
        //凭证对象正常写入存储
        context.deviceCredentialDataStore.edit { preferences ->
            preferences[deviceIdKey] = credentials.deviceId
            preferences[deviceTokenKey] = credentials.deviceToken
            val oaid = credentials.reportedOaidFingerprint?.takeIf { it.isNotBlank() }
            if (oaid != null) {
                preferences[reportedOaidKey] = oaid
            } else {
                preferences.remove(reportedOaidKey)
            }
            val androidId = credentials.reportedAndroidIdFingerprint?.takeIf { it.isNotBlank() }
            if (androidId != null) {
                preferences[reportedAndroidIdKey] = androidId
            } else {
                preferences.remove(reportedAndroidIdKey)
            }
        }
    }

    override suspend fun clear() {
        context.deviceCredentialDataStore.edit { preferences ->
            preferences.remove(deviceIdKey)
            preferences.remove(deviceTokenKey)
            preferences.remove(reportedOaidKey)
            preferences.remove(reportedAndroidIdKey)
        }
    }
}
