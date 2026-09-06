package com.fantaysea.clan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.fantaysea.clan.data.AppPreferences
import com.fantaysea.clan.ui.theme.ClanTheme

class MainActivity : ComponentActivity() {
    private val appPreferences by lazy { AppPreferences(applicationContext) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClanTheme {
                App(appPreferences)
            }
        }
    }
}