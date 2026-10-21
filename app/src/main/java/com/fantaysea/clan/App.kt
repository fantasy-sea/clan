package com.fantaysea.clan

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
<<<<<<< HEAD
import androidx.lifecycle.viewmodel.CreationExtras
=======
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
>>>>>>> ed8cc7a2bcc6e1a65cef8d6559532e18a32a3172
import com.fantaysea.clan.core.navigation.AppNavigation
import com.fantaysea.clan.data.AppPreferences
import com.fantaysea.clan.data.repository.ClanRepository
import com.fantaysea.clan.data.repository.DeviceRegistration

<<<<<<< HEAD

//staticCompositionLocalOf 用于不常变化的、全局需要使用的变量（值发生变化时会追踪重组整个 Provider 的 content 范围）
//顶层变量用于声明不会变化、全局需要使用的变量
//compositionLocalOf 用于声明常变化、全局需要使用的变量（值发生变化时重组读取该值的相关位置）

val LocalAppPreferences = staticCompositionLocalOf<AppPreferences> {
    //这边声明的是默认值，当没有为staticCompositionLocalOf<AppPreferences> 提供实现时才会调用
    error("未提供本地存储实现")
}
val EMPTY_FUNCTION ={}
=======
val LocalAppPreferences = staticCompositionLocalOf<AppPreferences> { error("AppPreferences not provided") }

private sealed interface IdentityState {
    data object Loading : IdentityState
    data object Ready : IdentityState
    data class Error(val message: String) : IdentityState
}

@Composable
fun App(appPreferences: AppPreferences, repository: ClanRepository) {
    var attempt by remember { mutableIntStateOf(0) }
    var identityState by remember { mutableStateOf<IdentityState>(IdentityState.Loading) }

    LaunchedEffect(attempt) {
        identityState = IdentityState.Loading
        identityState = try {
            repository.initializeSelf(
                DeviceRegistration(
                    deviceName = Build.MODEL,
                    model = Build.MODEL,
                    androidVersion = Build.VERSION.RELEASE,
                ),
            )
            IdentityState.Ready
        } catch (error: Exception) {
            IdentityState.Error(error.message ?: "无法连接服务端")
        }
    }

    when (val state = identityState) {
        IdentityState.Loading -> IdentityLoading()
        IdentityState.Ready -> CompositionLocalProvider(LocalAppPreferences provides appPreferences) {
            AppNavigation(repository)
        }
        is IdentityState.Error -> IdentityError(state.message, onRetry = { attempt++ })
    }
}

>>>>>>> ed8cc7a2bcc6e1a65cef8d6559532e18a32a3172
@Composable
private fun IdentityLoading() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text("正在连接服务端", modifier = Modifier.padding(top = 16.dp))
    }
<<<<<<< HEAD
    //传入的值会传递给其下的所有子Composable
    CompositionLocalProvider(
        //在其中提供共享变量，其下的所有子Composable都能够访问到声明的一个或多个共享变量
        LocalAppPreferences provides appPreferences,
=======
}

@Composable
private fun IdentityError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
>>>>>>> ed8cc7a2bcc6e1a65cef8d6559532e18a32a3172
    ) {
        Text("连接服务端失败", style = MaterialTheme.typography.titleLarge)
        Text(message, modifier = Modifier.padding(vertical = 12.dp))
        Button(onClick = onRetry) { Text("重试") }
    }
}
