package com.fantaysea.clan

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import com.fantaysea.clan.core.navigation.AppNavigation
import com.fantaysea.clan.data.AppPreferences
import com.fantaysea.clan.data.mock.MockServer

//
val LocalAppPreferences = staticCompositionLocalOf<AppPreferences> {
    error("AppPreferences not provided")
}
@Composable
fun App(appPreferences: AppPreferences) {
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
    CompositionLocalProvider(LocalAppPreferences provides appPreferences) {
        //其中所有Composable都能够访问LocalAppPreferences
        AppNavigation()
    }
}