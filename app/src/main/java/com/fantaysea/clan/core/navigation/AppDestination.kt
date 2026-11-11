package com.fantaysea.clan.core.navigation

import kotlinx.serialization.Serializable

/**
 * 全局导航目的地标记接口。
 *
 * 各模块在自己的包里定义 sealed 目的地接口并继承它,
 * 未来的 SettingsDestination / DeviceDestination ...), [Navigator] 只认这个根类型,
 * 因此新增模块不需要改动 core, 也不用改 Navigator 的签名。
 *
 * 刻意不加 @Serializable: 标记接口不参与序列化, 序列化仍由每个具体目的地自己负责。
 * Navigation 的 type-safe 路由按运行期类型取序列化器(route::class.serializer()),
 * 所以静态参数类型不必是可序列化的, 类型安全不受影响。
 */
interface AppDestination
/**
 * 模块入口：屏幕控制、屏幕共享
 */
@Serializable
data object ModuleEntryDest : AppDestination

/**
 * 绑定设备列表
 */
@Serializable
data object DeviceListDest : AppDestination

/**
 * 屏幕分享
 */
@Serializable
data object ShareScreenDest : AppDestination
/**
 * 连接会话-远程控制
 */
@Serializable
data class SessionControlDest(val deviceId: String) : AppDestination

/**
 * 连接会话-远程被控
 */
@Serializable
data class SessionControlledDest(val deviceId: String) : AppDestination
/** 互联 域嵌套导航图(仅用于成组管理与冷启动入口) */
@Serializable
data object InterconnectGraph : AppDestination

/** 引导页目的地 */
@Serializable
data object OnboardingDestination : AppDestination