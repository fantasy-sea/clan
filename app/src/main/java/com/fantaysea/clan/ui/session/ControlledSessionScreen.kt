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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fantaysea.clan.core.di.LocalClanRepository
import com.fantaysea.clan.core.di.LocalNavigator
import com.fantaysea.clan.data.model.ControlCommand
import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.data.model.LocalPermissions
import com.fantaysea.clan.data.model.SessionRole
import com.fantaysea.clan.data.model.SessionStatus
import com.fantaysea.clan.data.repository.ClanRepository
import com.fantaysea.clan.data.repository.RemoteEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * 被控端页面。
 *
 * 当前已完成：上报 SESSION_CONNECTED（媒体接入前占位）、回传 PERMISSION_STATUS、
 * 接收 CONTROL 并回传 CONTROL_RESULT。真实点击/滑动执行仍待 AccessibilityService 接入，
 * 目前收到指令后只展示并 ack OK，保证控制端链路可闭环。
 * TODO(真实实现): ScreenCaptureService + WebRTC 发送屏幕视频；AccessibilityService 执行控制指令。
 */
@Composable
fun ControlledSessionScreen(peer: Device) {
    val navigator = LocalNavigator.current
    val repository = LocalClanRepository.current
    val active by repository.activeSession.collectAsState()
    val session = active?.takeIf { it.role == SessionRole.CONTROLLED && it.peer.id == peer.id }
    val sessionId = session?.info?.sessionId
    val localPermissions by repository.localPermissions.collectAsState()

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showStopConfirm by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }
    var lastCommand by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = session != null) { showStopConfirm = true }
    RememberSessionEnd(repository, sessionId) { navigator.back() }

    // 无媒体阶段占位：进入会话页即上报 CONNECTED，让 CONTROL 能通过服务端状态检查。
    // 真实 WebRTC 接入后应改为视频轨开始发送/PeerConnection 连通时发送。
    LaunchedEffect(sessionId) {
        val sid = sessionId ?: return@LaunchedEffect
        runCatching { repository.sendSessionConnected(sid) }
            .onFailure { scope.launch { snackbarHostState.showSnackbar(it.message ?: "建立连接失败") } }
    }

    // 权限变化时同步给控制端；服务端在 CONNECTING/CONNECTED 都允许该消息。
    LaunchedEffect(sessionId, localPermissions) {
        val sid = sessionId ?: return@LaunchedEffect
        runCatching {
            repository.sendPermissionStatus(
                sessionId = sid,
                screenCapture = localPermissions.screenCapture,
                accessibility = localPermissions.accessibility,
            )
        }
    }

    // 进入 CONNECTED 后再补发一次，确保已经打开控制页的对端能收到初始权限状态。
    LaunchedEffect(sessionId, session?.info?.status) {
        val sid = sessionId ?: return@LaunchedEffect
        if (session?.info?.status == SessionStatus.CONNECTED) {
            runCatching {
                repository.sendPermissionStatus(
                    sessionId = sid,
                    screenCapture = localPermissions.screenCapture,
                    accessibility = localPermissions.accessibility,
                )
            }
        }
    }

    LaunchedEffect(sessionId) {
        val sid = sessionId ?: return@LaunchedEffect
        repository.events.collect { event ->
            when (event) {
                is RemoteEvent.ControlCommandEvent -> if (event.sessionId == sid) {
                    lastCommand = describeCommand(event.command)
                    // TODO(真实实现): 交给 AccessibilityService 执行后按执行结果回传。
                    runCatching { repository.sendControlResult(sid, event.requestId, ok = true) }
                        .onFailure { scope.launch { snackbarHostState.showSnackbar(it.message ?: "回传指令结果失败") } }
                }

                is RemoteEvent.SessionError -> if (event.sessionId == sid) {
                    scope.launch { snackbarHostState.showSnackbar(event.message) }
                }

                else -> Unit
            }
        }
    }

    if (session == null) {
        SessionUnavailable(onBack = { navigator.back() })
        return
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000.milliseconds)
            seconds++
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // 被控制横幅(红色脉冲)
            PulsingBanner(controllerName = peer.deviceName)

            Spacer(modifier = Modifier.height(20.dp))
            PermissionCard(localPermissions)

            Spacer(modifier = Modifier.height(24.dp))

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
                text = "正在将本机屏幕画面发送给「${peer.deviceName}」\n已共享 ${
                    formatDuration(seconds)
                }",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            lastCommand?.let { command ->
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Text(
                        text = "最近控制指令：$command",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp),
        )
    }

    if (showStopConfirm) {
        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            title = { Text("停止共享") },
            text = { Text("确定停止共享屏幕并结束本次远程控制吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showStopConfirm = false
                    scope.launch {
                        sessionId?.let { runCatching { repository.endSession(it) } }
                        navigator.back()
                    }
                }) { Text("停止", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirm = false }) { Text("继续共享") }
            },
        )
    }
}

@Composable
private fun PermissionCard(permissions: LocalPermissions) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "本机权限",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "屏幕捕获：${if (permissions.screenCapture) "已授权" else "未授权"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "无障碍远程操作：待接入（当前仅同步权限状态）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun describeCommand(command: ControlCommand): String = when (command) {
    is ControlCommand.Tap -> "轻触 (${command.x.toPercent()}, ${command.y.toPercent()})"
    is ControlCommand.Swipe ->
        "滑动 (${command.startX.toPercent()}, ${command.startY.toPercent()}) → " +
            "(${command.endX.toPercent()}, ${command.endY.toPercent()})"

    ControlCommand.Back -> "返回"
    ControlCommand.Home -> "主页"
    ControlCommand.Recents -> "最近任务"
}

private fun Double.toPercent(): String = "${(this * 100).toInt()}%"

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

/**
 * 监听远端结束会话。sessionId 为空时（会话未建立/已清理）不注册监听。
 * SharedFlow 不重放历史事件，因此不需要“跳过 replay”标记。
 */
@Composable
internal fun RememberSessionEnd(
    repository: ClanRepository,
    sessionId: String?,
    onEnded: () -> Unit,
) {
    LaunchedEffect(sessionId) {
        if (sessionId == null) return@LaunchedEffect
        repository.events.collect { event ->
            if (event is RemoteEvent.SessionEnded && event.sessionId == sessionId) onEnded()
        }
    }
}

/** 时长格式化: mm:ss */
internal fun formatDuration(totalSeconds: Int): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}
