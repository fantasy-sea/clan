package com.fantaysea.clan.ui.control

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fantaysea.clan.ui.components.SixDigitCodeInput
import com.fantaysea.clan.ui.components.shake

/**
 * 「添加新设备」配对码弹窗。
 * 输入 6 位后自动收起键盘并校验; 失败抖动+提示后自动清空重输;
 * 通过后进入「配对等待中」; 对方确认后显示成功并自动关闭。
 */
@Composable
internal fun PairCodeDialog(
    state: ControlHomeUiState,
    onDismiss: () -> Unit,
    onCodeChanged: (String) -> Unit,
    onComplete: (String) -> Unit,
) {
    val isError = state.pairPhase == PairPhase.Error
    val validating = state.pairPhase == PairPhase.Validating
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !validating,
            dismissOnClickOutside = !validating,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (state.pairPhase) {
                    PairPhase.Success -> SuccessContent()
                    PairPhase.Waiting -> WaitingContent(deviceHint = null)
                    else -> InputContent(
                        state = state,
                        isError = isError,
                        onCodeChanged = onCodeChanged,
                        onComplete = onComplete,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                // 底部按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (state.pairPhase == PairPhase.Success) {
                        TextButton(onClick = onDismiss) { Text("完成") }
                    } else {
                        TextButton(onClick = onDismiss, enabled = state.pairPhase != PairPhase.Validating) {
                            Text(if (state.pairPhase == PairPhase.Waiting) "取消配对" else "取消")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InputContent(
    state: ControlHomeUiState,
    isError: Boolean,
    onCodeChanged: (String) -> Unit,
    onComplete: (String) -> Unit,
) {
    Text(
        text = "添加新设备",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "输入对方设备「共享屏幕」页显示的 6 位配对码",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(28.dp))

    Box(modifier = Modifier.shake(trigger = isError)) {
        androidx.compose.runtime.key(state.resetNonce) {
            SixDigitCodeInput(
                value = state.pairCode,
                onValueChange = onCodeChanged,
                onComplete = onComplete,
                error = isError,
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // 固定高度错误/校验收起区, 避免布局跳动
    Box(modifier = Modifier.height(40.dp), contentAlignment = Alignment.Center) {
        when {
            isError -> {
                Text(
                    text = state.pairError ?: "配对码错误",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
            state.pairPhase == PairPhase.Validating -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.size(10.dp))
                    Text("正在校验…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun WaitingContent(deviceHint: String?) {
    Text(
        text = "配对等待中",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(20.dp))
    BouncingDots()
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = deviceHint ?: "请在被控设备上确认配对请求",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun BouncingDots() {
    val transition = rememberInfiniteTransition(label = "dots")
    val dotStates = listOf(
        transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "d0"),
        transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600, delayMillis = 150), RepeatMode.Reverse), label = "d1"),
        transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600, delayMillis = 300), RepeatMode.Reverse), label = "d2"),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        dotStates.forEach { dot ->
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .alpha(dot.value)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun SuccessContent() {
    Icon(
        imageVector = Icons.Filled.CheckCircle,
        contentDescription = null,
        tint = Color(0xFF2E7D32),
        modifier = Modifier.size(64.dp),
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "配对成功",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "新设备已添加到设备列表",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
