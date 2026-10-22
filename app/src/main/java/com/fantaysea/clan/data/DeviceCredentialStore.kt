package com.fantaysea.clan.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

data class DeviceCredentials(val deviceId: String, val deviceToken: String)

interface DeviceCredentialStore {
    suspend fun load(): DeviceCredentials?
    suspend fun save(credentials: DeviceCredentials)
    suspend fun clear()
}

private val Context.deviceCredentialDataStore by preferencesDataStore(name = "device_credentials")

/** 应用私有凭据存储。设备 id 与 token 始终在同一个 DataStore 事务中更新。 */
class AndroidDeviceCredentialStore(private val context: Context) : DeviceCredentialStore {
    private val deviceIdKey = stringPreferencesKey("device_id")
    private val deviceTokenKey = stringPreferencesKey("device_token")
    private val legacyDeviceTokenKey = stringPreferencesKey("device_token")

    override suspend fun load(): DeviceCredentials? {
        // 旧版 UUID 是客户端自行生成的，服务端不认识；忽略并迁移清理。
        context.dataStore.edit { it.remove(legacyDeviceTokenKey) }
        val preferences = context.deviceCredentialDataStore.data.first()
        val deviceId = preferences[deviceIdKey]
        val deviceToken = preferences[deviceTokenKey]
        if (deviceId.isNullOrBlank() || deviceToken.isNullOrBlank()) {
            if (deviceId != null || deviceToken != null) clear()
            return null
        }
        return DeviceCredentials(deviceId, deviceToken)
    }

    override suspend fun save(credentials: DeviceCredentials) {
        require(credentials.deviceId.isNotBlank())
        require(credentials.deviceToken.isNotBlank())
        context.deviceCredentialDataStore.edit { preferences ->
            preferences[deviceIdKey] = credentials.deviceId
            preferences[deviceTokenKey] = credentials.deviceToken
        }
    }

    override suspend fun clear() {
        context.deviceCredentialDataStore.edit { preferences ->
            preferences.remove(deviceIdKey)
            preferences.remove(deviceTokenKey)
        }
    }
}
