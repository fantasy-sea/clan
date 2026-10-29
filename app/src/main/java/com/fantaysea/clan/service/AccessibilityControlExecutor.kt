package com.fantaysea.clan.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.view.WindowManager
import com.fantaysea.clan.data.model.ControlCommand
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** 被控端执行一条 CONTROL 指令的结果。 */
sealed interface ControlExecutionResult {
    data object Success : ControlExecutionResult
    data class Failure(val reason: String) : ControlExecutionResult
}

/**
 * 通过 [ClanAccessibilityService] 真正执行控制指令。
 *
 * - TAP / SWIPE 使用 `dispatchGesture`，等待系统回调后返回；
 * - BACK / HOME / RECENTS 使用 `performGlobalAction`；
 * - 指令串行执行：前一个手势结束（或超时）后才处理下一个，避免快速连点互相取消；
 * - 坐标每次执行时按被控端当前屏幕 bounds 重新映射，支持控制过程中旋转屏幕。
 *
 * 执行前会再次校验归一化坐标和滑动时长，防止旧版/异常客户端绕过服务端校验。
 */
object AccessibilityControlExecutor {

    private const val TAP_DURATION_MS = 50L
    private const val GESTURE_TIMEOUT_MARGIN_MS = 2_000L
    private const val MIN_SWIPE_DURATION_MS = 50
    private const val MAX_SWIPE_DURATION_MS = 5_000

    private val mutex = Mutex()

    suspend fun execute(command: ControlCommand): ControlExecutionResult = mutex.withLock {
        command.validationError()?.let { error ->
            return@withLock ControlExecutionResult.Failure(error)
        }
        val service = ClanAccessibilityService.runningService()
            ?: return@withLock ControlExecutionResult.Failure("无障碍服务未开启")

        withContext(Dispatchers.Main.immediate) {
            try {
                executeOnMain(service, command)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                ControlExecutionResult.Failure("执行失败，请重试")
            }
        }
    }

    private suspend fun executeOnMain(
        service: ClanAccessibilityService,
        command: ControlCommand,
    ): ControlExecutionResult {
        val bounds = screenBounds(service)
        return when (command) {
            is ControlCommand.Tap -> {
                val path = Path().apply {
                    moveTo(
                        normalizedToScreenCoordinate(command.x, bounds.width(), bounds.left),
                        normalizedToScreenCoordinate(command.y, bounds.height(), bounds.top),
                    )
                }
                dispatchGesture(
                    service = service,
                    path = path,
                    durationMs = TAP_DURATION_MS,
                    timeoutMs = TAP_DURATION_MS + GESTURE_TIMEOUT_MARGIN_MS,
                )
            }

            is ControlCommand.Swipe -> {
                val path = Path().apply {
                    moveTo(
                        normalizedToScreenCoordinate(command.startX, bounds.width(), bounds.left),
                        normalizedToScreenCoordinate(command.startY, bounds.height(), bounds.top),
                    )
                    lineTo(
                        normalizedToScreenCoordinate(command.endX, bounds.width(), bounds.left),
                        normalizedToScreenCoordinate(command.endY, bounds.height(), bounds.top),
                    )
                }
                val durationMs = command.duration
                    .coerceIn(MIN_SWIPE_DURATION_MS, MAX_SWIPE_DURATION_MS)
                    .toLong()
                dispatchGesture(
                    service = service,
                    path = path,
                    durationMs = durationMs,
                    timeoutMs = durationMs + GESTURE_TIMEOUT_MARGIN_MS,
                )
            }

            ControlCommand.Back -> globalAction(service, AccessibilityService.GLOBAL_ACTION_BACK)
            ControlCommand.Home -> globalAction(service, AccessibilityService.GLOBAL_ACTION_HOME)
            ControlCommand.Recents -> globalAction(service, AccessibilityService.GLOBAL_ACTION_RECENTS)
        }
    }

    private suspend fun dispatchGesture(
        service: AccessibilityService,
        path: Path,
        durationMs: Long,
        timeoutMs: Long,
    ): ControlExecutionResult {
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val result = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<ControlExecutionResult> { continuation ->
                val dispatched = runCatching {
                    service.dispatchGesture(
                        gesture,
                        object : AccessibilityService.GestureResultCallback() {
                            override fun onCompleted(gestureDescription: GestureDescription?) {
                                if (continuation.isActive) {
                                    continuation.resume(ControlExecutionResult.Success)
                                }
                            }

                            override fun onCancelled(gestureDescription: GestureDescription?) {
                                if (continuation.isActive) {
                                    continuation.resume(
                                        ControlExecutionResult.Failure("操作被系统取消"),
                                    )
                                }
                            }
                        },
                        null,
                    )
                }.getOrDefault(false)

                if (!dispatched && continuation.isActive) {
                    continuation.resume(ControlExecutionResult.Failure("系统无法执行该操作"))
                }
            }
        }
        return result ?: ControlExecutionResult.Failure("操作执行超时")
    }

    private fun globalAction(
        service: AccessibilityService,
        action: Int,
    ): ControlExecutionResult =
        if (service.performGlobalAction(action)) {
            ControlExecutionResult.Success
        } else {
            ControlExecutionResult.Failure("系统无法执行该操作")
        }

    /**
     * 手势坐标使用屏幕坐标系，因此 offsets 不能丢；正常情况下 bounds.left/top 为 0。
     * 极少数设备取不到 WindowManager 时退回 displayMetrics，保证至少能注入。
     */
    private fun screenBounds(service: AccessibilityService): Rect {
        val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        windowManager?.maximumWindowMetrics?.bounds?.let { return it }
        val metrics = service.resources.displayMetrics
        return Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
    }
}
