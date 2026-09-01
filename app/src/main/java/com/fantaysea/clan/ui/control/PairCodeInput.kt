package com.fantaysea.clan.ui.control

/**
 * 配对码输入纯逻辑(无 Android/Compose 依赖, 便于单测)。
 */
object PairCodeInput {
    const val LENGTH = 6

    /** 过滤为非数字并截断到 6 位 */
    fun sanitize(raw: String): String = raw.filter { it.isDigit() }.take(LENGTH)

    /** 是否已输入完整 6 位数字 */
    fun isComplete(digits: String): Boolean =
        digits.length == LENGTH && digits.all { it.isDigit() }
}
