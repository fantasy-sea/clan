package com.fantaysea.clan.core.di

import androidx.compose.runtime.staticCompositionLocalOf
import com.fantaysea.clan.core.navigation.Navigator
import com.fantaysea.clan.core.navigation.NoOpNavigator
import com.fantaysea.clan.data.AppPreferences
import com.fantaysea.clan.data.repository.ClanRepository

//staticCompositionLocalOf 用于不常变化的、全局需要使用的变量（值发生变化时会追踪重组整个 Provider 的 content 范围）
//顶层变量用于声明不会变化、全局需要使用的变量
//compositionLocalOf 用于声明常变化、全局需要使用的变量（值发生变化时重组读取该值的相关位置）
val LocalAppPreferences = staticCompositionLocalOf<AppPreferences> {
    //这边声明的是默认值，当没有为staticCompositionLocalOf<AppPreferences> 提供实现时才会调用
    error("未提供本地存储实现")
}
val LocalClanRepository =staticCompositionLocalOf<ClanRepository>{
    error("无法使用")
}


/**
 * 全局导航入口: 由 AppNavigation 通过 CompositionLocalProvider 提供。
 *
 * 用 staticCompositionLocalOf 是因为提供的 Navigator 实例由 remember 缓存, 身份稳定不变;
 * 若以后需要暴露"当前目的地"这类会变化的值, 应改用 compositionLocalOf。
 */
val LocalNavigator = staticCompositionLocalOf<Navigator> { NoOpNavigator }

val EMPTY_FUNCTION ={}