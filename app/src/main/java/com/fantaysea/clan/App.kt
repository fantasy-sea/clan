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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fantaysea.clan.core.di.LocalAppPreferences
import com.fantaysea.clan.core.di.LocalClanRepository
import com.fantaysea.clan.core.di.LocalScreenCaptureManager
import com.fantaysea.clan.core.di.LocalSelfDevice
import com.fantaysea.clan.core.di.LocalWebRtcManager
import com.fantaysea.clan.core.navigation.AppNavigation
import com.fantaysea.clan.data.AppPreferences
import com.fantaysea.clan.data.repository.ClanRepository
import com.fantaysea.clan.data.repository.DeviceRegistration
import com.fantaysea.clan.media.ScreenCaptureManager
import com.fantaysea.clan.media.WebRtcManager

private sealed interface IdentityState {
    data object Loading : IdentityState
    data object Ready : IdentityState
    data class Error(val message: String) : IdentityState
}

@Composable
fun App(
    appPreferences: AppPreferences,
    repository: ClanRepository,
    screenCaptureManager: ScreenCaptureManager,
    webRtcManager: WebRtcManager,
) {
    //连接服务端尝试次数
    var attempt by remember { mutableIntStateOf(0) }
    var identityState by remember { mutableStateOf<IdentityState>(IdentityState.Loading) }

    LaunchedEffect(attempt) {
        identityState = IdentityState.Loading
        identityState = try {
            //尝试注册本设备
            // TODO 注册后有返回设备信息没有用上
            repository.initializeSelf(
                //传入设备注册信息
                DeviceRegistration(
                    deviceName = Build.MODEL,
                    model = Build.MODEL,
                    androidVersion = Build.VERSION.RELEASE,
                )
            )
            IdentityState.Ready
        } catch (error: Exception) {
            IdentityState.Error(error.message ?: "无法连接服务端")
        }
    }
    when (val state = identityState) {
        IdentityState.Loading -> IdentityLoading()
        IdentityState.Ready -> {
            val selfDevice by repository.self.collectAsState()
            //传入的值会传递给其下的所有子Composable
            //其中所有Composable都能够访问LocalAppPreferences
            CompositionLocalProvider(
                //在其中提供共享变量，其下的所有子Composable都能够访问到声明的一个或多个共享变量
                LocalAppPreferences provides appPreferences,
                LocalClanRepository provides repository,
                LocalSelfDevice provides selfDevice,
                LocalScreenCaptureManager provides screenCaptureManager,
                LocalWebRtcManager provides webRtcManager,
            ) {
                AppNavigation()
            }
        }

        is IdentityState.Error -> IdentityError(state.message, onRetry = { attempt++ })
    }

}

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

}

@Composable
private fun IdentityError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("连接服务端失败", style = MaterialTheme.typography.titleLarge)
        Text(message, modifier = Modifier.padding(vertical = 12.dp))
        Button(onClick = onRetry) { Text("重试") }
    }
}
