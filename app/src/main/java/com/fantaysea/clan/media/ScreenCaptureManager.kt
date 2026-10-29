package com.fantaysea.clan.media

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 屏幕捕获授权的进程内状态。 */
sealed interface ScreenCaptureState {
    /** 没有可消费的授权令牌（未授权、已消费或已被系统撤销）。 */
    data object NoConsent : ScreenCaptureState

    /** 已拿到系统授权，令牌待消费。 */
    data object Ready : ScreenCaptureState

    /** 令牌已被消费，前台服务与 MediaProjection 正在建立。 */
    data object Starting : ScreenCaptureState

    /** 采集正在运行。 */
    data object Capturing : ScreenCaptureState

    data class Error(val message: String) : ScreenCaptureState
}

/** 系统授权回调里的一次性令牌。Android 14+ 每个令牌只能创建一次 MediaProjection。 */
data class ScreenCaptureConsent(
    val resultCode: Int,
    val resultData: Intent,
)

/**
 * 进程级屏幕捕获授权存储。
 *
 * MediaProjection 不是普通运行时权限：
 * - 每次 `createScreenCaptureIntent()` 都会弹系统授权框；
 * - 回调里的 [ScreenCaptureConsent.resultData] 是单次令牌，必须先由 `mediaProjection` 类型
 *   前台服务开始前台、再调用 `getMediaProjection()`（Android 14+）；
 * - 令牌消费后不能复用，因此这里只保存「待消费」的一次性授权，采集停止/失败即清空。
 *
 * 该实例由 `ClanApplication` 持有，ShareScreen 与 ControlledSessionScreen 共享；页面切换或
 * Activity 重建不会丢失授权状态。
 */
class ScreenCaptureManager(context: Context) {

    private val appContext = context.applicationContext
    private val projectionManager =
        appContext.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    private val lock = Any()
    private var pendingConsent: ScreenCaptureConsent? = null
    private val _state = MutableStateFlow<ScreenCaptureState>(ScreenCaptureState.NoConsent)
    val state: StateFlow<ScreenCaptureState> = _state.asStateFlow()

    /** UI 门控含义：有可用授权或正在采集。注意这不代表已经拿到 MediaProjection 实例。 */
    val isAuthorized: Boolean
        get() = when (_state.value) {
            ScreenCaptureState.Ready,
            ScreenCaptureState.Starting,
            ScreenCaptureState.Capturing -> true

            else -> false
        }

    fun createCaptureIntent(): Intent = projectionManager.createScreenCaptureIntent()

    /** 保存系统授权结果；取消授权会清空旧令牌。 */
    fun onConsent(resultCode: Int, data: Intent?) {
        synchronized(lock) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                pendingConsent = ScreenCaptureConsent(resultCode, data)
                _state.value = ScreenCaptureState.Ready
            } else {
                pendingConsent = null
                _state.value = ScreenCaptureState.NoConsent
            }
        }
    }

    fun hasPendingConsent(): Boolean = synchronized(lock) { pendingConsent != null }

    /**
     * 消费一次性授权令牌。
     * 只有 [ScreenCaptureState.Ready] 才可能返回非空；返回后状态推进到 Starting。
     */
    fun takeConsent(): ScreenCaptureConsent? = synchronized(lock) {
        val consent = pendingConsent ?: return@synchronized null
        pendingConsent = null
        _state.value = ScreenCaptureState.Starting
        consent
    }

    fun markCapturing() {
        synchronized(lock) { _state.value = ScreenCaptureState.Capturing }
    }

    /** 采集停止：令牌已消费，必须重新授权；同时结束当前授权状态。 */
    fun markStopped() {
        synchronized(lock) {
            pendingConsent = null
            _state.value = ScreenCaptureState.NoConsent
        }
    }

    fun markError(message: String) {
        synchronized(lock) {
            pendingConsent = null
            _state.value = ScreenCaptureState.Error(message)
        }
    }
}
