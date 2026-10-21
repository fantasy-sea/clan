package com.fantaysea.clan.core.navigation

<<<<<<< HEAD
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
=======
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavBackStackEntry
>>>>>>> ed8cc7a2bcc6e1a65cef8d6559532e18a32a3172
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.fantaysea.clan.LocalAppPreferences
import com.fantaysea.clan.data.repository.ClanRepository
import com.fantaysea.clan.ui.control.ControlHomeScreen
import com.fantaysea.clan.ui.control.SessionDeviceGate
import com.fantaysea.clan.ui.home.HomeMenuScreen
import com.fantaysea.clan.ui.onboarding.OnboardingScreen
<<<<<<< HEAD
import kotlinx.coroutines.flow.first
=======
import com.fantaysea.clan.ui.session.ControlSessionScreen
import com.fantaysea.clan.ui.session.ControlledSessionScreen
import com.fantaysea.clan.ui.share.ShareScreen
>>>>>>> ed8cc7a2bcc6e1a65cef8d6559532e18a32a3172
import kotlinx.coroutines.launch

@Composable
<<<<<<< HEAD
fun AppNavigation() {
    //获取导航
    val navController = rememberNavController()
    //获取本地存储方案
    val appPreferences = LocalAppPreferences.current
    val coroutineScope = rememberCoroutineScope()
    // remember 保证实例身份稳定, 与 staticCompositionLocalOf 的前提匹配。
    val navigator: Navigator = remember(navController) {
        // 收敛 NavController: UI 层只通过 Navigator 访问领域目的地, 不接触 NavController 本体。
        navController.asNavigator()
    }
    // 冷启动入口只解析一次: 读完本地标识后固定 startDestination, 之后的跳转全部交给显式导航。
    // 这样写标识时不会改变 startDestination, 也就不会重建整张导航图(避免硬切与状态丢失)。
    //produceState 将异步或外部的数据源（开启一个协程函数体）转换为Compose可观察的State
    val startDestination by produceState<Any?>(initialValue = null) {
        value = if (appPreferences.onboardingCompleted.first())
            HomeGraph else OnboardingDestination
    }
    if (startDestination == null) {
        // 本地标识尚未读到: 先铺主题背景占位, 避免首帧闪现首页再切到引导页
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        )
        return
    }
    // 把导航能力下发给 NavHost 下的所有子 Composable:
    // destination 的内容在 Provider 作用域内组合, 因此子屏可直接 LocalNavigator.current 取用。
    CompositionLocalProvider(LocalNavigator provides navigator) {
        NavHost(
            navController = navController,
            startDestination = startDestination!!,
        ) {
            //首页导航图
            homeGraph()
            //引导页
            composable<OnboardingDestination> {
                OnboardingScreen(
                    onComplete = {
                        // 防重复触发: 跳转后当前目的地已不是引导页
                        if (navController.currentDestination?.hasRoute<OnboardingDestination>() != true)
                            return@OnboardingScreen
                        // 持久化"已看过引导页", 决定下次冷启动的入口
                        coroutineScope.launch { appPreferences.setOnboardingCompleted(true) }
                        // 显式跳转首页导航图, 并把引导页移出回退栈
                        navController.navigate(HomeGraph) {
                            popUpTo<OnboardingDestination> { inclusive = true }
                        }
                    },
                )
            }
        }
    }
=======
fun AppNavigation(repository: ClanRepository) {
    val navController = rememberNavController()
    val appPreferences = LocalAppPreferences.current
    val coroutineScope = rememberCoroutineScope()
    val isSkillOnboarding = appPreferences.onboardingCompleted.collectAsState(initial = true)

    NavHost(
        navController = navController,
        startDestination = if (isSkillOnboarding.value) HomeGraph else Onboarding
    ) {
        /**
         * home 域嵌套导航图: 首页 / 远程控制 / 共享屏幕 / 控制页 / 被控页。
         * 返回逻辑由各子屏自行注册 BackHandler 处理(会话页返回前有确认);
         * 图起点(home)的系统返回由 NavHost 判定不拦截(compose 栈仅剩一项), 交由系统退出应用。
         */
        navigation<HomeGraph>(startDestination = Home) {

            /** home 域转场(滑动 + 淡入淡出, 与原先 AnimatedContent 观感一致) */
            val homeEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
                {
                    slideInHorizontally(tween(320)) { it / 4 } + fadeIn(tween(320))
                }
            val homeExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
                slideOutHorizontally(tween(320)) { -it / 4 } + fadeOut(tween(240))
            }
            val homePopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
                {
                    slideInHorizontally(tween(320)) { -it / 4 } + fadeIn(tween(320))
                }
            val homePopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
                {
                    slideOutHorizontally(tween(320)) { it / 4 } + fadeOut(tween(240))
                }

            composable<Home>(
                enterTransition = homeEnter,
                exitTransition = homeExit,
                popEnterTransition = homePopEnter,
                popExitTransition = homePopExit,
            ) {
                HomeMenuScreen(
                    onNavigateToControl = { navController.navigate(HomeControl) },
                    onNavigateToShare = { navController.navigate(HomeShare) },
                )
            }
            composable<HomeControl>(
                enterTransition = homeEnter,
                exitTransition = homeExit,
                popEnterTransition = homePopEnter,
                popExitTransition = homePopExit,
            ) {
                ControlHomeScreen(
                    repository = repository,
                    onNavigate = { deviceId -> navController.navigate(HomeControlSession(deviceId)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<HomeShare>(
                enterTransition = homeEnter,
                exitTransition = homeExit,
                popEnterTransition = homePopEnter,
                popExitTransition = homePopExit,
            ) {
                ShareScreen(
                    repository = repository,
                    onNavigate = { deviceId -> navController.navigate(HomeControlledSession(deviceId)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<HomeControlSession>(
                enterTransition = homeEnter,
                exitTransition = homeExit,
                popEnterTransition = homePopEnter,
                popExitTransition = homePopExit,
            ) { entry ->
                val deviceId = entry.toRoute<HomeControlSession>().deviceId
                SessionDeviceGate(
                    repository = repository,
                    deviceId = deviceId,
                    onBack = { navController.popBackStack() }) { peer ->
                    ControlSessionScreen(repository = repository, peer = peer, onBack = { navController.popBackStack() })
                }
            }
            composable<HomeControlledSession>(
                enterTransition = homeEnter,
                exitTransition = homeExit,
                popEnterTransition = homePopEnter,
                popExitTransition = homePopExit,
            ) { entry ->
                val deviceId = entry.toRoute<HomeControlledSession>().deviceId
                SessionDeviceGate(
                    repository = repository,
                    deviceId = deviceId,
                    onBack = { navController.popBackStack() }) { peer ->
                    ControlledSessionScreen(repository = repository, peer = peer, onBack = { navController.popBackStack() })
                }
            }

        }
        composable<Onboarding> {
            OnboardingScreen(
                onComplete = { coroutineScope.launch { appPreferences.setOnboardingCompleted(true) } },
            )
        }
    }


>>>>>>> ed8cc7a2bcc6e1a65cef8d6559532e18a32a3172
}
