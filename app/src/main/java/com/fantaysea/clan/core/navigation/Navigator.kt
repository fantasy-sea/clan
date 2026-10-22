package com.fantaysea.clan.core.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import kotlin.reflect.KClass

/**
 * 导航契约: 只暴露领域目的地, UI 层不感知 NavController。
 *
 * 之所以不直接下发 NavController: NavController 带有
 * navigate(String) / popBackStack(route, inclusive) / currentBackStackEntry 等全量 API,
 * 一旦泄漏到 UI 层, 目的地的类型安全约束就形同虚设。
 *
 * 目的地统一用 [AppDestination] 收口: 各模块定义自己的 sealed 子接口继承它
 * (如 [HomeDestination]), 新增模块不需要改 Navigator 的签名,
 * 而 UI 层依然拿不到 NavController 的任何 API。
 */
interface Navigator {
    /** 跳转到任意模块的领域目的地 */
    fun navigateTo(destination: AppDestination)

    /** 返回上一页 */
    fun back()
}

/** 无操作实现: 作为 CompositionLocal 默认值, 保证 @Preview / 单元测试渲染不崩溃 */
object NoOpNavigator : Navigator {
    override fun navigateTo(destination: AppDestination) = Unit

    override fun back() = Unit
}

/**
 * NavController -> Navigator 的唯一适配点。
 * NavController 的其余 API 全部收敛在这一层, 不外泄。
 */
fun NavController.asNavigator(): Navigator = object : Navigator {
    override fun navigateTo(destination: AppDestination) {
        // 已在目标页则不再入栈: 图起点(如首页)重复入栈会让回退栈多一格
        if (isCurrentDestination(destination)) return
        // NavController 会在整棵导航图里查找目标(而不是只在当前图里),
        // 所以从 home 图内跳到根级目的地、或跳到别的模块的目的地都能用
        navigate(destination)
    }

    override fun back() {
        popBackStack()
    }
}

/**
 * 目标页是否就是当前页。
 *
 * - 类型必须一致: [hasRoute] 用目标类型的序列化器哈希与当前目的地比对;
 * - 目标类型声明了参数时不能按类型去重: 同一类型的不同参数是不同页面
 *   (如不同 deviceId 的会话页), 否则会吞掉本该发生的跳转。
 */
private fun NavController.isCurrentDestination(destination: AppDestination): Boolean {
    val current = currentDestination ?: return false
    return current.hasRoute(destination.routeClass()) && current.arguments.isEmpty()
}

/**
 * 取运行期类型: 导航路由按运行期类型序列化(route::class.serializer()),
 * 静态类型只用来圈定契约, 因此这里必须拿运行期类去比对。
 */
@Suppress("UNCHECKED_CAST")
private fun AppDestination.routeClass(): KClass<Any> = this::class as KClass<Any>

/**
 * 全局导航入口: 由 AppNavigation 通过 CompositionLocalProvider 提供。
 *
 * 用 staticCompositionLocalOf 是因为提供的 Navigator 实例由 remember 缓存, 身份稳定不变;
 * 若以后需要暴露"当前目的地"这类会变化的值, 应改用 compositionLocalOf。
 */
val LocalNavigator = staticCompositionLocalOf<Navigator> { NoOpNavigator }
