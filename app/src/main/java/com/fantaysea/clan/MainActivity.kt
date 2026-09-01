package com.fantaysea.clan

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.fantaysea.clan.data.AppPreferences
import com.fantaysea.clan.data.mock.MockServer
import com.fantaysea.clan.ui.onboarding.OnboardingScreen
import com.fantaysea.clan.ui.onboarding.onboardingPages
import com.fantaysea.clan.ui.home.HomeScreen
import com.fantaysea.clan.ui.theme.ClanTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val appPreferences by lazy { AppPreferences(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClanTheme {
                ClanApp(appPreferences)
            }
        }
    }
}

/**
 * 应用入口路由:
 * - null  : DataStore 首次读取中(短暂空页)
 * - false : 第一次启动 → 显示欢迎页
 * - true  : 已看过欢迎页 → 直接进入主界面
 *
 * 欢迎页点击"开始使用"(或改为跳过即完成)后,
 * 写入 DataStore, Flow 自动回推 true, 界面自动切换到主界面。
 */
@Composable
private fun ClanApp(appPreferences: AppPreferences) {
    val onboardingCompleted by appPreferences.onboardingCompleted.collectAsState(initial = null)
    val coroutineScope = rememberCoroutineScope()

    // 注册本机远程身份(设备 token + 机型), 供配对/控制流程使用。
    // TODO(接真实服务端): 此处改为 WebSocket 连接握手(Hello 携带 deviceToken)。
    LaunchedEffect(Unit) {
        val token = appPreferences.ensureDeviceToken()
        MockServer.instance.registerSelf(
            deviceToken = token,
            deviceName = Build.MODEL,
            platform = "Android",
            model = Build.MODEL,
        )
    }

    when (onboardingCompleted) {
        null -> Box(modifier = Modifier.fillMaxSize())
        true -> HomeScreen()
        false -> OnboardingScreen(
            pages = remember { onboardingPages() },
            onComplete = { coroutineScope.launch { appPreferences.setOnboardingCompleted(true) } },
        )
    }
}
