package com.fantaysea.clan.service

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings

/**
 * 本次跳转实际落到的系统页面。
 *
 * 通话中的引导文案依赖它：落到详情页时用户只需要拨一个开关，落到列表页时才需要教用户找应用。
 */
enum class AccessibilitySettingsTarget {
    /** 本应用无障碍服务的详情页（最省事：页面里只有这一项开关）。 */
    ServiceDetail,

    /** 系统无障碍服务列表（需要在「已下载的应用 / 已安装的服务」里找）。 */
    AccessibilityList,

    /** 系统设置首页（最后兜底，用户还要自己往下进入无障碍）。 */
    SystemSettings,

    /** 三个入口都打不开。 */
    None,
}

/**
 * `Settings.ACTION_ACCESSIBILITY_DETAILS_SETTINGS` 在公开 SDK 中不存在（@SystemApi），
 * 所以这里用 action 字面量：已安装的 Settings 只要能解析该 action 就会打开指定服务的详情页，
 * 解析失败则由调用方回退到无障碍列表。不要改成直接引用 Settings 常量（编译不过）。
 */
private const val ACTION_ACCESSIBILITY_DETAILS_SETTINGS =
    "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"

/**
 * 打开本应用无障碍服务的系统设置页，按「最省事 → 兜底」顺序尝试：
 *
 * 1. 服务详情页（Android 13+ 常见，部分 12 与 ROM 也支持），直接把用户送到开关面前；
 * 2. 无障碍服务列表；
 * 3. 系统设置首页。
 *
 * 从 Activity 启动时**不加** [Intent.FLAG_ACTIVITY_NEW_TASK]：让系统设置落在当前任务栈里，
 * 用户按一次返回键就能回到本页，而不是回到系统列表或桌面。
 */
fun openAccessibilitySettings(context: Context, serviceClass: Class<*>): AccessibilitySettingsTarget {
    val component = ComponentName(context, serviceClass).flattenToString()
    val candidates = listOf(
        Intent(ACTION_ACCESSIBILITY_DETAILS_SETTINGS)
            .putExtra(Intent.EXTRA_COMPONENT_NAME, component) to AccessibilitySettingsTarget.ServiceDetail,
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) to AccessibilitySettingsTarget.AccessibilityList,
        Intent(Settings.ACTION_SETTINGS) to AccessibilitySettingsTarget.SystemSettings,
    )
    val activity = context.findActivity()
    val starter = activity ?: context
    candidates.forEach { (intent, target) ->
        if (activity == null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val started = runCatching { starter.startActivity(intent) }.isSuccess
        if (started) return target
    }
    return AccessibilitySettingsTarget.None
}

/** 解包 [ContextWrapper] 找 Activity：只有从 Activity 启动才能把系统设置放进当前任务栈。 */
private fun Context.findActivity(): Activity? =
    generateSequence(this) { (it as? ContextWrapper)?.baseContext }
        .firstOrNull { it is Activity } as? Activity
