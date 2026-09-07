package com.fantaysea.clan.core.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import com.fantaysea.clan.data.mock.MockServer
import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.ui.control.ControlHomeScreen
import com.fantaysea.clan.ui.home.HomeMenuScreen
import com.fantaysea.clan.ui.session.ControlledSessionScreen
import com.fantaysea.clan.ui.session.ControlSessionScreen
import com.fantaysea.clan.ui.share.ShareScreen

/** 顶层目的地(各子屏的导航契约, 由 [homeGraph] 映射为 nav 路由) */
sealed interface HomeDestination {
    data object Home : HomeDestination
    data object ControlHome : HomeDestination
    data object Share : HomeDestination
    data class ControlSession(val deviceId: Long) : HomeDestination
    data class ControlledSession(val deviceId: Long) : HomeDestination
}

/** home 域路由表(嵌套图) */
object HomeRoute {
    const val GRAPH = "homeGraph"
    const val HOME = "home"
    const val CONTROL_HOME = "controlHome"
    const val SHARE = "share"
    const val CONTROL_SESSION = "controlSession"
    const val CONTROLLED_SESSION = "controlledSession"
    const val DEVICE_ID_ARG = "deviceId"

    /** 会话路由(带 deviceId 参数) */
    fun controlSession(deviceId: Long) = "$CONTROL_SESSION/$deviceId"
    fun controlledSession(deviceId: Long) = "$CONTROLLED_SESSION/$deviceId"
}

/** home 域转场(滑动 + 淡入淡出, 与原先 AnimatedContent 观感一致) */
private val homeEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(tween(320)) { it / 4 } + fadeIn(tween(320))
}
private val homeExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(tween(320)) { -it / 4 } + fadeOut(tween(240))
}
private val homePopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(tween(320)) { -it / 4 } + fadeIn(tween(320))
}
private val homePopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(tween(320)) { it / 4 } + fadeOut(tween(240))
}

/**
 * home 域嵌套导航图: 首页 / 远程控制 / 共享屏幕 / 控制页 / 被控页。
 * 返回逻辑由各子屏自行注册 BackHandler 处理(会话页返回前有确认);
 * 图起点(home)的系统返回由 NavHost 判定不拦截(compose 栈仅剩一项), 交由系统退出应用。
 */
fun NavGraphBuilder.homeGraph(navController: NavController) {
    navigation(
        startDestination = HomeRoute.HOME,
        route = HomeRoute.GRAPH,
    ) {
        composable(
            route = HomeRoute.HOME,
            enterTransition = homeEnter,
            exitTransition = homeExit,
            popEnterTransition = homePopEnter,
            popExitTransition = homePopExit,
        ) {
            HomeMenuScreen(onNavigate = { navigateTo(navController, it) })
        }
        composable(
            route = HomeRoute.CONTROL_HOME,
            enterTransition = homeEnter,
            exitTransition = homeExit,
            popEnterTransition = homePopEnter,
            popExitTransition = homePopExit,
        ) {
            ControlHomeScreen(
                onNavigate = { navigateTo(navController, it) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = HomeRoute.SHARE,
            enterTransition = homeEnter,
            exitTransition = homeExit,
            popEnterTransition = homePopEnter,
            popExitTransition = homePopExit,
        ) {
            ShareScreen(
                onNavigate = { navigateTo(navController, it) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "${HomeRoute.CONTROL_SESSION}/{${HomeRoute.DEVICE_ID_ARG}}",
            arguments = listOf(navArgument(HomeRoute.DEVICE_ID_ARG) { type = NavType.LongType }),
            enterTransition = homeEnter,
            exitTransition = homeExit,
            popEnterTransition = homePopEnter,
            popExitTransition = homePopExit,
        ) { entry ->
            val deviceId = entry.arguments?.getLong(HomeRoute.DEVICE_ID_ARG) ?: return@composable
            SessionDeviceGate(deviceId = deviceId, onBack = { navController.popBackStack() }) { peer ->
                ControlSessionScreen(peer = peer, onBack = { navController.popBackStack() })
            }
        }
        composable(
            route = "${HomeRoute.CONTROLLED_SESSION}/{${HomeRoute.DEVICE_ID_ARG}}",
            arguments = listOf(navArgument(HomeRoute.DEVICE_ID_ARG) { type = NavType.LongType }),
            enterTransition = homeEnter,
            exitTransition = homeExit,
            popEnterTransition = homePopEnter,
            popExitTransition = homePopExit,
        ) { entry ->
            val deviceId = entry.arguments?.getLong(HomeRoute.DEVICE_ID_ARG) ?: return@composable
            SessionDeviceGate(deviceId = deviceId, onBack = { navController.popBackStack() }) { peer ->
                ControlledSessionScreen(peer = peer, onBack = { navController.popBackStack() })
            }
        }
    }
}

private fun navigateTo(navController: NavController, dest: HomeDestination) {
    when (dest) {
        HomeDestination.Home -> Unit // 首页即图起点, 无需导航
        HomeDestination.ControlHome -> navController.navigate(HomeRoute.CONTROL_HOME)
        HomeDestination.Share -> navController.navigate(HomeRoute.SHARE)
        is HomeDestination.ControlSession -> navController.navigate(HomeRoute.controlSession(dest.deviceId))
        is HomeDestination.ControlledSession -> navController.navigate(HomeRoute.controlledSession(dest.deviceId))
    }
}

/**
 * 会话目的地设备解析门: 按 deviceId 从仓库解析 [Device] 后再渲染会话页。
 * 解析期间保持空白; 设备不存在(如参数缺失 / 会话中设备被解绑)时自动返回上一页。
 */
@Composable
private fun SessionDeviceGate(
    deviceId: Long,
    onBack: () -> Unit,
    content: @Composable (Device) -> Unit,
) {
    var device by remember { mutableStateOf<Device?>(null) }
    LaunchedEffect(deviceId) {
        device = null
        val found = MockServer.instance.findDevice(deviceId)
        if (found == null) {
            onBack()
        } else {
            device = found
        }
    }
    val found = device
    if (found != null) {
        content(found)
    } else {
        Box(modifier = Modifier.fillMaxSize())
    }
}