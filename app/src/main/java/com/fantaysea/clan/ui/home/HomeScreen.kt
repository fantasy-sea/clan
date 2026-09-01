package com.fantaysea.clan.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.ui.control.ControlHomeScreen
import com.fantaysea.clan.ui.session.ControlledSessionScreen
import com.fantaysea.clan.ui.session.ControlSessionScreen
import com.fantaysea.clan.ui.share.ShareScreen

/** 顶层目的地 */
sealed interface HomeDestination {
    data object Home : HomeDestination
    data object ControlHome : HomeDestination
    data object Share : HomeDestination
    data class ControlSession(val peer: Device) : HomeDestination
    data class ControlledSession(val peer: Device) : HomeDestination
}

/** 简单栈式导航(无 navigation 依赖, 掌控转场与返回) */
private class HomeNavState {
    var stack by mutableStateOf(listOf<HomeDestination>(HomeDestination.Home))
        private set

    val current: HomeDestination get() = stack.last()

    fun push(dest: HomeDestination) {
        stack = stack + dest
    }

    fun pop() {
        if (stack.size > 1) stack = stack.dropLast(1)
    }
}

/**
 * 应用导航宿主: 首页 / 远程控制 / 共享屏幕 / 控制页 / 被控页。
 * 返回逻辑由各子屏各自注册 BackHandler 处理(会话页返回前有确认)。
 */
@Composable
fun HomeScreen() {
    val nav = remember { HomeNavState() }

    AnimatedContent(
        targetState = nav.current,
        transitionSpec = {
            (slideInHorizontally(tween(320)) { it / 4 } + fadeIn(tween(320)))
                .togetherWith(slideOutHorizontally(tween(320)) { -it / 4 } + fadeOut(tween(240)))
        },
        label = "homeNav",
    ) { dest ->
        when (dest) {
            HomeDestination.Home -> HomeMenuScreen(onNavigate = nav::push)
            HomeDestination.ControlHome -> ControlHomeScreen(
                onNavigate = nav::push,
                onBack = nav::pop,
            )

            HomeDestination.Share -> ShareScreen(
                onNavigate = nav::push,
                onBack = nav::pop,
            )

            is HomeDestination.ControlSession -> ControlSessionScreen(
                peer = dest.peer,
                onBack = nav::pop,
            )

            is HomeDestination.ControlledSession -> ControlledSessionScreen(
                peer = dest.peer,
                onBack = nav::pop,
            )
        }
    }
}
