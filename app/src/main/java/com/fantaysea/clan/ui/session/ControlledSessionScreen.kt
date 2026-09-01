package com.fantaysea.clan.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PresentToAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fantaysea.clan.data.mock.MockServer
import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.data.repository.ClanRepository
import com.fantaysea.clan.data.repository.RemoteEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 被控端页面(桩): 屏幕正在共享提示 + 停止共享。
 * TODO(真实实现): 此处由 ScreenCaptureService + WebRTC 持续发送屏幕流。
 */
@Composable
fun ControlledSessionScreen(
    peer: Device,
    onBack: () -> Unit,
) {
    val repository: ClanRepository = MockServer.instance
    val scope = rememberCoroutineScope()
    var showStopConfirm by remember { mutableStateOf(false) }
    var seconds by remember { mutableStateOf(0) }

    BackHandler { showStopConfirm = true }
    RememberSessionEnd(repository) { onBack() }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            seconds++
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // 被控制横幅(红色脉冲)
            PulsingBanner(controllerName = peer.deviceName)

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.PresentToAll,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(56.dp),
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "屏幕共享中",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "正在将本机屏幕画面发送给「${peer.deviceName}」\n已共享 ${formatDuration(seconds)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = { showStopConfirm = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text("停止共享", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showStopConfirm) {
        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            title = { Text("停止共享") },
            text = { Text("确定停止共享屏幕并结束本次远程控制吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showStopConfirm = false
                    scope.launch { repository.endSession(peer.id) }
                    onBack()
                }) { Text("停止", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirm = false }) { Text("继续共享") }
            },
        )
    }
}

@Composable
private fun PulsingBanner(controllerName: String) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        0.55f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulseAlpha",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = alpha))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "正在被「${controllerName}」控制",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}

/** 监听远端结束会话(跳过 replay 历史事件, 只响应实时事件) */
@Composable
internal fun RememberSessionEnd(repository: ClanRepository, onEnded: () -> Unit) {
    LaunchedEffect(Unit) {
        var live = false
        repository.events.collect { event ->
            if (live && event is RemoteEvent.SessionEnded) onEnded()
            live = true
        }
    }
}

/** 时长格式化: mm:ss */
internal fun formatDuration(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}