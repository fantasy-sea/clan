package com.fantaysea.clan.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 错误提示用的水平抖动。当 [trigger] 由 false 变为 true 时执行一次左右抖动并归位。
 */
fun Modifier.shake(
    trigger: Boolean,
    times: Int = 5,
    amplitude: Float = 12f,
): Modifier = composed {
    val offset = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger) {
            repeat(times) {
                offset.animateTo(amplitude, tween(40))
                offset.animateTo(-amplitude, tween(40))
            }
            offset.animateTo(0f, tween(30))
        }
    }
    graphicsLayer { translationX = offset.value }
}
