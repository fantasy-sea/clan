package com.fantaysea.clan.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.fantaysea.clan.core.di.LocalNavigator
import com.fantaysea.clan.core.navigation.AppDestination
import com.fantaysea.clan.core.navigation.HomeDestination
import com.fantaysea.clan.core.navigation.Navigator
import com.fantaysea.clan.ui.theme.ClanTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeMenuScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** 只记录跳转目的地, 不碰 NavController */
    private class RecordingNavigator : Navigator {
        val destinations = mutableListOf<AppDestination>()

        override fun navigateTo(destination: AppDestination) {
            destinations += destination
        }

        override fun back() = Unit
    }

    @Test
    fun remoteControlCard_navigatesToControlHomeOnly() {
        val navigator = RecordingNavigator()
        composeRule.setContent {
            ClanTheme {
                CompositionLocalProvider(LocalNavigator provides navigator) {
                    HomeMenuScreen(isPlayAnimation = false)
                }
            }
        }

        composeRule.onNodeWithText("远程控制").performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf<AppDestination>(HomeDestination.ControlHome),
                navigator.destinations,
            )
        }
    }

    @Test
    fun shareScreenCard_navigatesToShareOnly() {
        val navigator = RecordingNavigator()
        composeRule.setContent {
            ClanTheme {
                CompositionLocalProvider(LocalNavigator provides navigator) {
                    HomeMenuScreen(isPlayAnimation = false)
                }
            }
        }

        composeRule.onNodeWithText("共享屏幕").performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf<AppDestination>(HomeDestination.Share),
                navigator.destinations,
            )
        }
    }
}
