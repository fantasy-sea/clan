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

    companion object {
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
