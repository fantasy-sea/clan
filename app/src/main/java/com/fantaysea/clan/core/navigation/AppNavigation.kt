package com.fantaysea.clan.core.navigation

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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.fantaysea.clan.LocalAppPreferences
import com.fantaysea.clan.ui.onboarding.OnboardingScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
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

}
