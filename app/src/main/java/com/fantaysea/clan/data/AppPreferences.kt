package com.fantaysea.clan.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "clan_prefs")

/**
 * 应用级本地偏好(DataStore)。
 * 目前只负责"首次启动"标志: 欢迎页完成后写入, 之后启动直接进主界面。
 */
class AppPreferences(private val context: Context) {

    private val onboardingCompletedKey = booleanPreferencesKey("onboarding_completed")

    /** true = 已看过欢迎页; false = 第一次进入应用 */
    val onboardingCompleted: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[onboardingCompletedKey] ?: false }

    /** 欢迎页完成(或跳过)后调用 */
    suspend fun setOnboardingCompleted(completed: Boolean = true) {
        context.dataStore.edit { prefs -> prefs[onboardingCompletedKey] = completed }
    }
}
