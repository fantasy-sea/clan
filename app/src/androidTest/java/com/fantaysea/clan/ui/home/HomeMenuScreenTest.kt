package com.fantaysea.clan.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.fantaysea.clan.ui.theme.ClanTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeMenuScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun remoteControlCard_invokesOnlyControlCallback() {
        var controlClicks = 0
        var shareClicks = 0
        composeRule.setContent {
            ClanTheme {
                HomeMenuScreen(
                    onNavigateToControl = { controlClicks++ },
                    onNavigateToShare = { shareClicks++ },
                    isPlayAnimation = false,
                )
            }
        }

        composeRule.onNodeWithText("远程控制").performClick()

        composeRule.runOnIdle {
            assertEquals(1, controlClicks)
            assertEquals(0, shareClicks)
        }
    }

    @Test
    fun shareScreenCard_invokesOnlyShareCallback() {
        var controlClicks = 0
        var shareClicks = 0
        composeRule.setContent {
            ClanTheme {
                HomeMenuScreen(
                    onNavigateToControl = { controlClicks++ },
                    onNavigateToShare = { shareClicks++ },
                    isPlayAnimation = false,
                )
            }
        }

        composeRule.onNodeWithText("共享屏幕").performClick()

        composeRule.runOnIdle {
            assertEquals(0, controlClicks)
            assertEquals(1, shareClicks)
        }
    }
}
