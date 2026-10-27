package com.fantaysea.clan.core.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.fantaysea.clan.core.di.LocalClanRepository
import com.fantaysea.clan.core.di.LocalNavigator
import com.fantaysea.clan.ui.control.ControlHomeScreen
import com.fantaysea.clan.ui.control.SessionDeviceGate
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
            val navigator = LocalNavigator.current
            SessionDeviceGate(
                repository = LocalClanRepository.current,
                deviceId = deviceId,
                onBack = { navigator.back() },
            ) { peer ->
                ControlSessionScreen(peer = peer)
            }
        }
        composable<HomeDestination.ControlledSession> { entry ->
            val deviceId = entry.toRoute<HomeDestination.ControlledSession>().deviceId
            val navigator = LocalNavigator.current
            SessionDeviceGate(
                repository = LocalClanRepository.current,
                deviceId = deviceId,
                onBack = { navigator.back() },
            ) { peer ->
                ControlledSessionScreen(peer = peer)
            }
        }
    }
}
