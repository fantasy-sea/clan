package com.fantaysea.clan.core.navigation

import kotlinx.serialization.Serializable

/**
 * 全局导航目的地标记接口。
 *
 * 各模块在自己的包里定义 sealed 目的地接口并继承它(如 [HomeDestination],
 * 未来的 SettingsDestination / DeviceDestination ...), [Navigator] 只认这个根类型,
 * 因此新增模块不需要改动 core, 也不用改 Navigator 的签名。
 *
 * 刻意不加 @Serializable: 标记接口不参与序列化, 序列化仍由每个具体目的地自己负责。
 * Navigation 的 type-safe 路由按运行期类型取序列化器(route::class.serializer()),
 * 所以静态参数类型不必是可序列化的, 类型安全不受影响。
 */
interface AppDestination


/**
 * home 域目的地: 各子屏的导航契约, 也是 Navigation 的 type-safe 路由。
 * 路由由导航库按类型序列化生成, 不再维护字符串路由表。
 *
 * 继承 [AppDestination] 接入全局导航契约: 新增模块只需再定义一个同形状的
 * sealed 目的地接口继承 [AppDestination], Navigator 与 core 都不用改。
 */
@Serializable
sealed interface HomeDestination : AppDestination {
    @Serializable
    data object Home : HomeDestination

    @Serializable
    data object ControlHome : HomeDestination

    @Serializable
    data object Share : HomeDestination

    @Serializable
    data class ControlSession(val deviceId: Long) : HomeDestination

    @Serializable
    data class ControlledSession(val deviceId: Long) : HomeDestination
}

/** home 域嵌套导航图(仅用于成组管理与冷启动入口) */
@Serializable
data object HomeGraph

/** 引导页目的地 */
@Serializable
data object OnboardingDestination : AppDestination



