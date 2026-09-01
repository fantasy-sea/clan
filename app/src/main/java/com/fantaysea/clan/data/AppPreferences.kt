package com.fantaysea.clan.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "clan_prefs")

/**
 * 应用级本地偏好(DataStore)。
 * 目前负责: "首次启动"标志 + 本机设备 token(远程身份标识)。
 */
class AppPreferences(private val context: Context) {

    private val onboardingCompletedKey = booleanPreferencesKey("onboarding_completed")
    private val deviceTokenKey = stringPreferencesKey("device_token")

    /** true = 已看过欢迎页; false = 第一次进入应用 */
    val onboardingCompleted: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[onboardingCompletedKey] ?: false }

    /** 本机设备 token(首次启动生成并持久化, 作为服务端 Devices.device_token 身份) */
    val deviceToken: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[deviceTokenKey] }

    /** 欢迎页完成(或跳过)后调用 */
    suspend fun setOnboardingCompleted(completed: Boolean = true) {
        context.dataStore.edit { prefs -> prefs[onboardingCompletedKey] = completed }
    }

    /** 读取本机 token, 不存在则生成并持久化 */
    suspend fun ensureDeviceToken(): String {
        val existing = context.dataStore.data.first()[deviceTokenKey]
        if (!existing.isNullOrBlank()) return existing
        val token = UUID.randomUUID().toString()
        context.dataStore.edit { prefs -> prefs[deviceTokenKey] = token }
        return token
    }
}
