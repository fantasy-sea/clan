package com.fantaysea.clan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fantaysea.clan.data.AppPreferences
import com.fantaysea.clan.data.repository.ClanRepository
import com.fantaysea.clan.ui.theme.ClanTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val clanApplication =application as ClanApplication
        enableEdgeToEdge()
        setContent {
            ClanTheme {
                App(
                    appPreferences = clanApplication.appPreferences,
                    clanRepository = clanApplication.repository,
                    screenCaptureManager = clanApplication.screenCaptureManager,
                    webRtcManager = clanApplication.webRtcManager,
                    deviceIdentity = clanApplication.deviceIdentity,
                )
            }
        }
    }
}
