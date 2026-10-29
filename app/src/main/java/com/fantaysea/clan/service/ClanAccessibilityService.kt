package com.fantaysea.clan.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 无障碍控制服务。
 *
 * 该服务让「无障碍控制」成为系统设置中可由用户显式开启的授权项，共享屏幕页据此做权限门控，
 * 并把真实状态同步到 [com.fantaysea.clan.data.model.LocalPermissions]。
 *
 * 控制指令由 [AccessibilityControlExecutor] 通过本服务执行：轻触/滑动使用
 * `dispatchGesture`，返回/Home/最近任务使用 `performGlobalAction`，执行结果由被控会话页
 * 以 CONTROL_RESULT 回传。服务同时暴露 [running]，供 UI 感知运行中权限是否被关闭。
 */
class ClanAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _running.value = true
        // 用户刚在本应用里点了「去打开设置」：开成功后把 App 拉回前台，省掉自己找返回。
        AccessibilitySetupCoordinator.onServiceConnected(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 触控注入不依赖窗口内容；远端控制只需要服务连接状态，无需解析无障碍事件。
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) {
            instance = null
            _running.value = false
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
            _running.value = false
        }
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var instance: ClanAccessibilityService? = null

        private val _running = MutableStateFlow(false)

        /**
         * 无障碍服务是否真正连接、可以执行手势。
         *
         * UI 用它展示/同步实时可执行状态；系统设置里的「已开启」不等于服务已绑定，
         * 只有连接成功后 `dispatchGesture` 才可用。
         */
        val running: StateFlow<Boolean> = _running.asStateFlow()

        /** 无障碍服务是否已与系统连接（可真正处理控制指令）。 */
        val isRunning: Boolean
            get() = instance != null

        /** 供 [AccessibilityControlExecutor] 取得可执行手势的服务实例。 */
        internal fun runningService(): ClanAccessibilityService? = instance

        /**
         * 系统设置中是否已开启本应用的无障碍服务。
         *
         * 先看已连接实例（最快、最准确），再查 [AccessibilityManager] 的启用列表，
         * 兼顾「刚在系统设置中开启、服务尚未完成绑定」与「进程重启后实例为空」的情况。
         */
        fun isEnabled(context: Context): Boolean {
            if (instance != null) return true
            val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
            val component = ComponentName(context, ClanAccessibilityService::class.java)
            return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { info ->
                    val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
                    ComponentName(serviceInfo.packageName, serviceInfo.name) == component
                }
        }

        /**
         * 打开系统无障碍设置，优先直达本服务的详情页，失败再回退到列表/系统设置。
         * 返回值告诉界面实际落点，用于匹配对应的引导文案；返回 [AccessibilitySettingsTarget.None] 表示都没打开。
         */
        fun openSettings(context: Context): AccessibilitySettingsTarget =
            openAccessibilitySettings(context, ClanAccessibilityService::class.java)
    }
}
