package com.fantaysea.clan.core.navigation

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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.fantaysea.clan.LocalAppPreferences
import com.fantaysea.clan.ui.control.ControlHomeScreen
import com.fantaysea.clan.ui.control.SessionDeviceGate
import com.fantaysea.clan.ui.home.HomeMenuScreen
import com.fantaysea.clan.ui.onboarding.OnboardingScreen
import com.fantaysea.clan.ui.session.ControlSessionScreen
import com.fantaysea.clan.ui.session.ControlledSessionScreen
import com.fantaysea.clan.ui.share.ShareScreen
import kotlinx.coroutines.launch


@Composable
fun AppNavigation() {
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
                    onNavigate = {
                        navController.navigate(HomeControl)
                    },
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
                    onNavigate = {
                        navController.navigate(HomeShare)
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<HomeControlSession>(
                enterTransition = homeEnter,
                exitTransition = homeExit,
                popEnterTransition = homePopEnter,
                popExitTransition = homePopExit,
            ) { entry ->
                val deviceId =
                    entry.arguments?.getLong("deviceID") ?: return@composable
                SessionDeviceGate(
                    deviceId = deviceId,
                    onBack = { navController.popBackStack() }) { peer ->
                    ControlSessionScreen(peer = peer, onBack = { navController.popBackStack() })
                }
            }
            composable<HomeControlledSession>(
                enterTransition = homeEnter,
                exitTransition = homeExit,
                popEnterTransition = homePopEnter,
                popExitTransition = homePopExit,
            ) { entry ->
                val deviceId =
                    entry.arguments?.getLong("deviceID") ?: return@composable
                SessionDeviceGate(
                    deviceId = deviceId,
                    onBack = { navController.popBackStack() }) { peer ->
                    ControlledSessionScreen(peer = peer, onBack = { navController.popBackStack() })
                }
            }

        }
        composable<Onboarding> {
            OnboardingScreen(
                onComplete = { coroutineScope.launch { appPreferences.setOnboardingCompleted(true) } },
            )
        }
    }


}
