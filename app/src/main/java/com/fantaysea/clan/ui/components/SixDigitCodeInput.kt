package com.fantaysea.clan.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/** 6 位输入格在宽屏下的最大边长与格间距。 */
private val MaxDigitBoxSize = 52.dp
private val DigitBoxGap = 8.dp
private const val CodeLength = 6

/**
 * 通用 6 位数字码输入框:
 * - 底层隐藏 BasicTextField 承接 IME 输入(数字键盘);
 * - 上方渲染 6 个格子, 逐位高亮 + 光标闪烁 + 输入缩放动画;
 * - 格子按可用宽度等分, 窄屏/弹窗内不会裁掉最后几位;
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

    BoxWithConstraints(modifier = modifier) {
        // 预留 8dp 余量: 吸收 dp→px 的取整误差, 也给错误抖动动画留出左右空间。
        val boxSize = ((maxWidth - DigitBoxGap * (CodeLength - 1) - 8.dp) / CodeLength)
            .coerceIn(0.dp, MaxDigitBoxSize)
        val digitFontSize = with(LocalDensity.current) { (boxSize * 0.46f).toSp() }
        val boxShape = RoundedCornerShape(boxSize * 0.27f)
        // 输入完成后不再把光标画在已有数字上。
        val cursorIndex = value.length.coerceAtMost(CodeLength - 1)

        // 格子
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DigitBoxGap, Alignment.CenterHorizontally),
        ) {
            repeat(CodeLength) { index ->
                val digit = value.getOrNull(index)
                val isCurrent = focused && index == cursorIndex
                val boxBorder = when {
                    error -> MaterialTheme.colorScheme.error
                    isCurrent -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outline
                }

                val digitScale by animateFloatAsState(
                    targetValue = if (digit != null) 1f else 0.7f,
                    animationSpec = tween(120),
                    label = "digitScale",
                )

                Box(
                    modifier = Modifier
                        .size(boxSize)
                        .clip(boxShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.5.dp, boxBorder, boxShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = digit?.toString() ?: "",
                        style = TextStyle(fontSize = digitFontSize, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.scale(digitScale),
                    )
                    if (isCurrent && digit == null) {
                        Box(
                            modifier = Modifier
                                .size(width = 2.dp, height = boxSize * 0.54f)
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
                val digits = raw.filter { it.isDigit() }.take(CodeLength)
                if (digits != value) onValueChange(digits)
                if (digits.length == CodeLength) {
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

        // 放在 BoxWithConstraints 内部, 确保子组合中的输入框节点已挂载后再请求焦点。
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
    }
}
