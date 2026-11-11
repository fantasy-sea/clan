package com.fantaysea.clan.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal val Context.dataStore by preferencesDataStore(name = "clan_prefs")

/** 非敏感应用偏好。后端设备凭据由独立的 [DeviceCredentialStore] 保存。 */
class AppPreferences(private val context: Context) {
    private val onboardingCompletedKey = booleanPreferencesKey("onboarding_completed")
    private val privacyConsentCompletedKey = booleanPreferencesKey("privacy_consent_completed")

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[onboardingCompletedKey] ?: false }

    suspend fun setOnboardingCompleted(completed: Boolean = true) {
        context.dataStore.edit { preferences -> preferences[onboardingCompletedKey] = completed }
    }

    /**
     * 隐私提示是否已同意。OAID 只能在同意后读取，因此身份初始化前必须先通过该门。
     */
    val privacyConsentCompleted: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[privacyConsentCompletedKey] ?: false }

    suspend fun setPrivacyConsentCompleted(completed: Boolean = true) {
        context.dataStore.edit { preferences -> preferences[privacyConsentCompletedKey] = completed }
    }
}
