package com.fantaysea.clan.data.model

/**
 * 与服务端 CONTROL payload 对齐的控制指令。
 * 坐标统一归一化到 0.0~1.0，由被控端映射到实际屏幕像素。
 */
sealed interface ControlCommand {
    val action: String

    data class Tap(val x: Double, val y: Double) : ControlCommand {
        override val action: String get() = "TAP"
    }

    data class Swipe(
        val startX: Double,
        val startY: Double,
        val endX: Double,
        val endY: Double,
        val duration: Int = DEFAULT_SWIPE_DURATION,
    ) : ControlCommand {
        override val action: String get() = "SWIPE"
    }

    data object Back : ControlCommand {
        override val action: String get() = "BACK"
    }

    data object Home : ControlCommand {
        override val action: String get() = "HOME"
    }

    data object Recents : ControlCommand {
        override val action: String get() = "RECENTS"
    }

    /**
     * 被控端执行前的参数兜底校验。
     *
     * 服务端已经做过同样的范围校验；这里再检查一次，避免旧版本/异常客户端把 NaN、
     * 超范围坐标或非法滑动时长直接送进手势构造，导致执行侧崩溃。
     *
     * @return 非法时返回可直接回传 CONTROL_RESULT 的短文案；合法时返回 null。
     */
    fun validationError(): String? = when (this) {
        is Tap -> if (x.isFinite() && y.isFinite() && x in NORMALIZED_RANGE && y in NORMALIZED_RANGE) {
            null
        } else {
            INVALID_PARAMETER_REASON
        }

        is Swipe -> if (
            listOf(startX, startY, endX, endY).all { it.isFinite() && it in NORMALIZED_RANGE } &&
            duration in DURATION_RANGE
        ) {
            null
        } else {
            INVALID_PARAMETER_REASON
        }

        Back, Home, Recents -> null
    }

    companion object {
        private val NORMALIZED_RANGE = 0.0..1.0
        private val DURATION_RANGE = 50..5000
        const val INVALID_PARAMETER_REASON = "指令参数无效"

        /** 把服务端 CONTROL payload 转成领域指令；缺字段/未知 action 返回 null。 */
        fun fromWire(
            action: String,
            x: Double? = null,
            y: Double? = null,
            startX: Double? = null,
            startY: Double? = null,
            endX: Double? = null,
            endY: Double? = null,
            duration: Int? = null,
        ): ControlCommand? = when (action.uppercase()) {
            "TAP" -> if (x != null && y != null) Tap(x, y) else null
            "SWIPE" -> if (startX != null && startY != null && endX != null && endY != null) {
                Swipe(startX, startY, endX, endY, duration ?: DEFAULT_SWIPE_DURATION)
            } else null

            "BACK" -> Back
            "HOME" -> Home
            "RECENTS" -> Recents
            else -> null
        }
    }
}

private const val DEFAULT_SWIPE_DURATION = 300
