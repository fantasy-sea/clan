package com.fantaysea.clan.core.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.fantaysea.clan.core.di.LocalClanRepository
import com.fantaysea.clan.core.di.LocalNavigator
import com.fantaysea.clan.ui.components.SessionDeviceGate
import com.fantaysea.clan.ui.screens.devices.DeviceListScreen
import com.fantaysea.clan.ui.screens.entry.ModuleEntryScreen
import com.fantaysea.clan.ui.screens.session.SessionControlScreen
import com.fantaysea.clan.ui.screens.session.SessionControlledScreen
import com.fantaysea.clan.ui.screens.share.ShareScreen

/**
 * home 域嵌套导航图: 首页 / 远程控制 / 共享屏幕 / 控制页 / 被控页。
 * 目的地与参数由 [HomeDestination] 的类型序列化生成, 子屏只依赖目的地类型。
 * 跳转与返回统一走 [LocalNavigator](由 AppNavigation 注入), 这里不再透传导航回调。
 * 页面转场由图级统一继承([appNavGraph]), 各页面不再重复声明。
 * 返回逻辑由各子屏自行注册 BackHandler 处理(会话页返回前有确认);
 * 图起点(home)的系统返回由 NavHost 判定不拦截(compose 栈仅剩一项), 交由系统退出应用。
 */
fun NavGraphBuilder.interconnectGraph() {
    appNavGraph<InterconnectGraph>(startDestination = ModuleEntryDest) {
        composable<ModuleEntryDest> {
            ModuleEntryScreen()
        }
        composable<DeviceListDest> {
            DeviceListScreen()
        }
        composable<ShareScreenDest> {
            ShareScreen()
        }
        composable<SessionControlDest> { entry ->
            val deviceId = entry.toRoute<SessionControlDest>().deviceId
            val navigator = LocalNavigator.current
            SessionDeviceGate(
                repository = LocalClanRepository.current,
                deviceId = deviceId,
                onBack = { navigator.back() },
            ) { peer ->
                SessionControlScreen(peer = peer)
            }
        }
        composable<SessionControlledDest> { entry ->
            val deviceId = entry.toRoute<SessionControlledDest>().deviceId
            val navigator = LocalNavigator.current
            SessionDeviceGate(
                repository = LocalClanRepository.current,
                deviceId = deviceId,
                onBack = { navigator.back() },
            ) { peer ->
                SessionControlledScreen(peer = peer)
            }
        }
    }
}
