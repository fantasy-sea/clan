package com.fantaysea.clan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fantaysea.clan.data.AppPreferences
import com.fantaysea.clan.data.AndroidDeviceCredentialStore
import com.fantaysea.clan.data.repository.RealClanRepository
import com.fantaysea.clan.ui.theme.ClanTheme

class MainActivity : ComponentActivity() {
    //初始化appPreferences用于本地存储
    private val appPreferences by lazy { AppPreferences(applicationContext) }
    private val repository by lazy {
        //创建
        RealClanRepository.create(
            baseUrl = BuildConfig.CLAN_API_BASE_URL,
            credentialStore = AndroidDeviceCredentialStore(applicationContext),
        )
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClanTheme {
                App(appPreferences, repository)
            }
        }
    }
}
