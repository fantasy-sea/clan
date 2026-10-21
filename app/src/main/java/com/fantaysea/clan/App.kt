package com.fantaysea.clan

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.viewmodel.CreationExtras
import com.fantaysea.clan.core.navigation.AppNavigation
import com.fantaysea.clan.data.AppPreferences
import com.fantaysea.clan.data.mock.MockServer


//staticCompositionLocalOf 用于不常变化的、全局需要使用的变量（值发生变化时会追踪重组整个 Provider 的 content 范围）
//顶层变量用于声明不会变化、全局需要使用的变量
//compositionLocalOf 用于声明常变化、全局需要使用的变量（值发生变化时重组读取该值的相关位置）

val LocalAppPreferences = staticCompositionLocalOf<AppPreferences> {
    //这边声明的是默认值，当没有为staticCompositionLocalOf<AppPreferences> 提供实现时才会调用
    error("未提供本地存储实现")
}
val EMPTY_FUNCTION ={}
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
    //传入的值会传递给其下的所有子Composable
    CompositionLocalProvider(
        //在其中提供共享变量，其下的所有子Composable都能够访问到声明的一个或多个共享变量
        LocalAppPreferences provides appPreferences,
    ) {
        //其中所有Composable都能够访问LocalAppPreferences
        AppNavigation()
    }
}