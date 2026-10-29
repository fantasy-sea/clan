package com.fantaysea.clan.service

/**
 * 把控制端的归一化坐标映射到被控端实际屏幕像素。
 *
 * 单独抽成无 Android 依赖的纯函数，便于在 JVM 单测里覆盖边界值。
 * 用 `sizePx - 1` 作为最大坐标，避免 1.0 落在屏幕外一像素。
 *
 * @param normalized 服务端约定的 0.0~1.0 坐标，越界值会被 clamp。
 * @param sizePx 当前屏幕宽或高（像素）。
 * @param offsetPx 屏幕 bounds 的 left/top（通常为 0）。
 */
internal fun normalizedToScreenCoordinate(
    normalized: Double,
    sizePx: Int,
    offsetPx: Int = 0,
): Float {
    if (sizePx <= 1) return offsetPx.toFloat()
    val clamped = normalized.coerceIn(0.0, 1.0)
    return offsetPx + (clamped * (sizePx - 1)).toFloat()
}
