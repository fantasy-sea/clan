package com.fantaysea.clan.service

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.fantaysea.clan.MainActivity

/**
 * 「开启无障碍后自动回到 App」的进程内协调器。
 *
 * 只有用户从本应用点了「去打开设置」才会记住标记（[markAwaitingEnable]），
 * 因此不会在用户从别处开启无障碍时抢前台。无障碍服务由系统绑定，
 * 连接回调里拉起 Activity 通常不受后台启动限制；被拦截时用 Toast 兜底提示按返回键。
 *
 * 局限：标记在内存里，进程在系统设置期间被杀则不会自动返回；
 * 此时用户手动返回后仍由共享屏幕页的 ON_RESUME 检测兜底。
 */
object AccessibilitySetupCoordinator {

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var awaitingEnable = false

    @Volatile
    private var pendingReturn: Runnable? = null

    /** 发起系统设置跳转前调用。 */
    fun markAwaitingEnable() {
        awaitingEnable = true
    }

    /** 用户放弃、未开启就返回、或引导关闭时调用；同时取消已排队的自动返回。 */
    fun clear() {
        awaitingEnable = false
        pendingReturn?.let { mainHandler.removeCallbacks(it) }
        pendingReturn = null
    }

    /** 由 [ClanAccessibilityService.onServiceConnected] 调用；只在本次从本应用发起时动作。 */
    fun onServiceConnected(context: Context) {
        if (!awaitingEnable) return
        awaitingEnable = false
        val appContext = context.applicationContext
        val runnable = Runnable {
            pendingReturn = null
            Toast.makeText(appContext, "无障碍已开启，正在返回家助", Toast.LENGTH_SHORT).show()
            val intent = Intent(appContext, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
            val launched = runCatching { appContext.startActivity(intent) }.isSuccess
            if (!launched) {
                Toast.makeText(appContext, "无障碍已开启，请按返回键回到家助", Toast.LENGTH_LONG).show()
            }
        }
        pendingReturn = runnable
        // 稍微等一下：让系统把「已开启」的确认弹窗/提示收起后再切前台。
        mainHandler.postDelayed(runnable, RETURN_DELAY_MS)
    }

    private const val RETURN_DELAY_MS = 400L
}
