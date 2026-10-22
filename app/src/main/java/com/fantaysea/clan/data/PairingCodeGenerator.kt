package com.fantaysea.clan.data

import kotlin.random.Random

/**
 * 配对码纯逻辑(无 Android 依赖, 便于单测)。
 *
 * 规则: 固定 6 位十进制数字, 有效期 [VALIDITY_MS](60 秒)。
 * 真实实现中生成逻辑在服务端(PairingCode 表), 此处的随机生成仅供 Mock 与测试对齐。
 */
object PairingCodeGenerator {
    const val CODE_LENGTH = 6
    const val VALIDITY_MS = 60_000L

    /** 生成 6 位数字码(允许前导 0)。 */
    fun generate(random: Random = Random.Default): String =
        buildString(CODE_LENGTH) { repeat(CODE_LENGTH) { append(random.nextInt(10)) } }

    /** 校验是否为合法 6 位纯数字。 */
    fun isValid(code: String): Boolean =
        code.length == CODE_LENGTH && code.all { it.isDigit() }

    /** 是否已过期(now 默认当前时间)。 */
    fun isExpired(expiresAt: Long, now: Long = System.currentTimeMillis()): Boolean =
        now >= expiresAt

    /** 剩余毫秒数(最小为 0)。 */
    fun remainingMillis(expiresAt: Long, now: Long = System.currentTimeMillis()): Long =
        (expiresAt - now).coerceAtLeast(0L)
}
