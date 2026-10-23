package com.fantaysea.clan.core.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.navigation

/** 滑动时长(ms) */
private const val SlideMillis = 320

/** 出场淡出时长(ms) */
private const val FadeOutMillis = 240

/** 自 [side] 侧(+1 右 / -1 左)滑入并淡入 */
private fun slideFadeIn(side: Int): EnterTransition =
    slideInHorizontally(tween(SlideMillis)) { side * it / 4 } + fadeIn(tween(SlideMillis))

/** 向 [side] 侧(+1 右 / -1 左)滑出并淡出 */
private fun slideFadeOut(side: Int): ExitTransition =
    slideOutHorizontally(tween(SlideMillis)) { side * it / 4 } + fadeOut(tween(FadeOutMillis))

/**
 * 全应用统一的页面转场(水平滑动 + 交叉淡入淡出)。
 * 正向: 新页自右滑入、旧页向左让位; 返回方向为正向的镜像。
 */
internal object AppTransitions {
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
        { slideFadeIn(+1) }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
        { slideFadeOut(-1) }
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
        { slideFadeIn(-1) }
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
        { slideFadeOut(+1) }
}

/**
 * 提供了一个已经配置好过渡动画的navigation
 * 后续通过该扩展函数创建的导航图自带其中的动画
 *
 * 因此图内目的地默认继承这里的一套; 某个页面需要不同观感时, 仍可在自己的 `composable`
 * 上单独声明(destination 级优先)。
 */


//
internal inline fun <reified T : Any> NavGraphBuilder.appNavGraph(
    startDestination: Any,
    //函数声明为内联 但builder需要传递所以不能够被内联 （noinline)
    noinline builder: NavGraphBuilder.() -> Unit,
) {
    navigation<T>(
        startDestination = startDestination,
        enterTransition = AppTransitions.enter,
        exitTransition = AppTransitions.exit,
        popEnterTransition = AppTransitions.popEnter,
        popExitTransition = AppTransitions.popExit,
        builder = builder,
    )
}
