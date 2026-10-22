package com.fantaysea.clan.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 通用 6 位数字码输入框:
 * - 底层隐藏 BasicTextField 承接 IME 输入(数字键盘);
 * - 上方渲染 6 个格子, 逐位高亮 + 光标闪烁 + 输入缩放动画;
 * - 满 6 位自动收起键盘并回调 [onComplete](由调用方做校验/清空)。
 */
@Composable
fun SixDigitCodeInput(
    value: String,
    onValueChange: (String) -> Unit,
    onComplete: (String) -> Unit,
    error: Boolean,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }

    // 光标闪烁
    val cursorTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by cursorTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "cursorAlpha",
    )

    val borderColor = when {
        error -> MaterialTheme.colorScheme.error
        focused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    Box(modifier = modifier) {
        // 格子
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(6) { index ->
                val digit = value.getOrNull(index)
                val isCurrent = focused && index == value.length.coerceAtMost(5)
                val boxBorder = if (error) MaterialTheme.colorScheme.error
                else if (isCurrent) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline

                val digitScale by animateFloatAsState(
                    targetValue = if (digit != null) 1f else 0.7f,
                    animationSpec = tween(120),
                    label = "digitScale",
                )

                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.5.dp, boxBorder, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = digit?.toString() ?: "",
                        style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.scale(digitScale),
                    )
                    if (isCurrent) {
                        Box(
                            modifier = Modifier
                                .size(width = 2.dp, height = 28.dp)
                                .alpha(cursorAlpha)
                                .clip(RoundedCornerShape(1.dp))
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
        }

        // 隐藏输入框(置于顶层承接焦点与 IME)
        BasicTextField(
            value = value,
            onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }.take(6)
                if (digits != value) onValueChange(digits)
                if (digits.length == 6) {
                    keyboard?.hide()
                    onComplete(digits)
                }
            },
            modifier = Modifier
                .matchParentSize()
                .alpha(0f)
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            cursorBrush = SolidColor(Color.Transparent),
            textStyle = TextStyle(color = Color.Transparent),
        )
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
