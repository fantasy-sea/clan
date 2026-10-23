package com.fantaysea.clan.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.fantaysea.clan.core.di.LocalClanRepository
import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.ui.control.ControlHomeScreen
import com.fantaysea.clan.ui.home.HomeMenuScreen
import com.fantaysea.clan.ui.session.ControlSessionScreen
import com.fantaysea.clan.ui.session.ControlledSessionScreen
import com.fantaysea.clan.ui.share.ShareScreen

/**
 * home 域嵌套导航图: 首页 / 远程控制 / 共享屏幕 / 控制页 / 被控页。
 * 目的地与参数由 [HomeDestination] 的类型序列化生成, 子屏只依赖目的地类型。
 * 跳转与返回统一走 [LocalNavigator](由 AppNavigation 注入), 这里不再透传导航回调。
 * 页面转场由图级统一继承([appNavGraph]), 各页面不再重复声明。
 * 返回逻辑由各子屏自行注册 BackHandler 处理(会话页返回前有确认);
 * 图起点(home)的系统返回由 NavHost 判定不拦截(compose 栈仅剩一项), 交由系统退出应用。
 */
fun NavGraphBuilder.homeGraph() {
    appNavGraph<HomeGraph>(startDestination = HomeDestination.Home) {
        composable<HomeDestination.Home> {
            HomeMenuScreen()
        }
        composable<HomeDestination.ControlHome> {
            ControlHomeScreen()
        }
        composable<HomeDestination.Share> {
            ShareScreen()
        }
        composable<HomeDestination.ControlSession> { entry ->
            val deviceId = entry.toRoute<HomeDestination.ControlSession>().deviceId
            SessionDeviceGate(deviceId = deviceId) { peer ->
                ControlSessionScreen(peer = peer)
            }
        }
        composable<HomeDestination.ControlledSession> { entry ->
            val deviceId = entry.toRoute<HomeDestination.ControlledSession>().deviceId
            SessionDeviceGate(deviceId = deviceId) { peer ->
                ControlledSessionScreen(peer = peer)
            }
        }
    }
}

/**
 * 会话目的地设备解析门: 按 deviceId 从仓库解析 [Device] 后再渲染会话页。
 * 解析期间保持空白; 设备不存在(如参数缺失 / 会话中设备被解绑)时自动返回上一页。
 */
@Composable
private fun SessionDeviceGate(
    deviceId: Long,
    content: @Composable (Device) -> Unit,
) {
    val repository = LocalClanRepository.current
    var device by remember { mutableStateOf<Device?>(null) }
    LaunchedEffect(deviceId) {
        device = null
        device = repository.findDevice(deviceId.toString())
    }
    if (device != null) {
        content(device!!)
    } else {
        Box(modifier = Modifier.fillMaxSize())
    }
}
