package com.fantaysea.clan.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fantaysea.clan.core.di.LocalClanRepository
import com.fantaysea.clan.core.di.LocalNavigator
import com.fantaysea.clan.data.model.ControlCommand
import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.data.model.SessionRole
import com.fantaysea.clan.data.model.SessionStatus
import com.fantaysea.clan.data.repository.RemoteEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * 控制端页面。
 *
 * 当前已完成：读取服务端会话、发送 SESSION_CONNECTED（WebRTC 媒体接入前的占位）、
 * 通过 CONTROL 发送点击/滑动/返回/Home/最近任务、展示 PERMISSION_STATUS 与命令结果。
 * TODO(真实实现): 把 [peer] 的画面渲染到 SurfaceViewRenderer，并由 WebRtcManager 回调
 *   触发 SESSION_CONNECTED；当前会话页发送该消息是为了在无媒体阶段仍能走通控制链路。
 */
@Composable
fun ControlSessionScreen(peer: Device) {
    val navigator = LocalNavigator.current
    val repository = LocalClanRepository.current
    val active by repository.activeSession.collectAsState()
    val session = active?.takeIf { it.role == SessionRole.CONTROLLER && it.peer.id == peer.id }
    val sessionId = session?.info?.sessionId
    val connected = session?.info?.status == SessionStatus.CONNECTED

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showEndConfirm by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }
    var permissionStatus by remember(sessionId) { mutableStateOf<RemoteEvent.PermissionStatus?>(null) }

    BackHandler(enabled = session != null) { showEndConfirm = true }
    RememberSessionEnd(repository, sessionId) { navigator.back() }

    // 无媒体阶段占位：进入会话页即上报 CONNECTED，让 CONTROL 能通过服务端状态检查。
    // 真实 WebRTC 接入后应改为 PeerConnection.onConnectionChange(CONNECTED) 时发送。
    LaunchedEffect(sessionId) {
        val sid = sessionId ?: return@LaunchedEffect
        runCatching { repository.sendSessionConnected(sid) }
            .onFailure { scope.launch { snackbarHostState.showSnackbar(it.message ?: "建立连接失败") } }
    }

    LaunchedEffect(sessionId) {
        val sid = sessionId ?: return@LaunchedEffect
        repository.events.collect { event ->
            when (event) {
                is RemoteEvent.PermissionStatus -> if (event.sessionId == sid) {
                    permissionStatus = event
                }

                is RemoteEvent.ControlCommandResult -> if (event.sessionId == sid) {
                    val text = if (event.result == RemoteEvent.ControlResultStatus.OK) {
                        "指令已送达（执行待接入）"
                    } else {
                        "指令失败：${event.reason ?: "未知原因"}"
                    }
                    scope.launch { snackbarHostState.showSnackbar(text) }
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

    val sendCommand: (ControlCommand) -> Unit = { command ->
        if (connected) {
            val sid = sessionId
            if (sid != null) {
                scope.launch {
                    runCatching { repository.sendControl(sid, command) }
                        .onFailure { snackbarHostState.showSnackbar(it.message ?: "发送控制指令失败") }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF141414))) {
        // ── 视频流占位区；WebRTC 媒体接入后替换为 SurfaceViewRenderer ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(sessionId, connected) {
                    if (!connected || sessionId == null) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val start = down.position
                        var end = start
                        var dragged = false
                        var pressed = true
                        while (pressed) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            end = change.position
                            if ((end - start).getDistance() > viewConfiguration.touchSlop) dragged = true
                            change.consume()
                            pressed = event.changes.any { it.pressed }
                        }
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val height = size.height.toFloat().coerceAtLeast(1f)
                        val startX = (start.x / width).coerceIn(0f, 1f).toDouble()
                        val startY = (start.y / height).coerceIn(0f, 1f).toDouble()
                        val endX = (end.x / width).coerceIn(0f, 1f).toDouble()
                        val endY = (end.y / height).coerceIn(0f, 1f).toDouble()
                        if (dragged) {
                            sendCommand(ControlCommand.Swipe(startX, startY, endX, endY))
                        } else {
                            sendCommand(ControlCommand.Tap(startX, startY))
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.TouchApp,
                    contentDescription = null,
                    tint = Color(0xFF6E6E6E),
                    modifier = Modifier.size(72.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "正在查看「${peer.deviceName}」的屏幕",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFBDBDBD),
                )
                Text(
                    text = when (session.info.status) {
                        SessionStatus.CONNECTING -> "正在建立会话…"
                        SessionStatus.CONNECTED -> "已连接 · 媒体画面待 WebRTC 接入"
                        else -> "信令已同步 · 媒体画面待 WebRTC 接入"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF757575),
                )
            }
        }

        // ── 顶部状态条 ──
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
            color = Color(0xCC141414),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(connected)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = peer.deviceName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                    Text(
                        text = if (connected) "已连接" else "连接中…",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (connected) Color(0xFF81C784) else Color(0xFFFFB74D),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatDuration(seconds),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFBDBDBD),
                )
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(onClick = { showEndConfirm = true }) {
                    Text("结束", color = Color(0xFFFF6B6B))
                }
            }
        }

        // ── 底部控制条 ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xE6141414))
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = permissionStatus?.let {
                    "屏幕共享 ${if (it.screenCapture) "✓" else "✗"} · 远程操作 ${if (it.accessibility) "✓" else "✗"}"
                } ?: "权限状态同步中…",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9E9E9E),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                ControlPadButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "返回", enabled = connected) {
                    sendCommand(ControlCommand.Back)
                }
                ControlPadButton(Icons.Filled.Home, "主页", enabled = connected) {
                    sendCommand(ControlCommand.Home)
                }
                ControlPadButton(Icons.Filled.Apps, "最近任务", enabled = connected) {
                    sendCommand(ControlCommand.Recents)
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "点击画面 = 轻触 · 滑动画面 = 滑动屏幕",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9E9E9E),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 150.dp),
        )
    }

    if (showEndConfirm) {
        AlertDialog(
            onDismissRequest = { showEndConfirm = false },
            title = { Text("结束控制") },
            text = { Text("确定结束对「${peer.deviceName}」的远程控制吗？") },
            confirmButton = {
                TextButton(onClick = {
                    showEndConfirm = false
                    scope.launch {
                        sessionId?.let { runCatching { repository.endSession(it) } }
                        navigator.back()
                    }
                }) { Text("结束") }
            },
            dismissButton = {
                TextButton(onClick = { showEndConfirm = false }) { Text("继续控制") }
            },
        )
    }
}

@Composable
internal fun SessionUnavailable(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("会话已结束", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "远程控制会话已结束或尚未建立，请重新发起控制。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onBack) { Text("返回") }
    }
}

@Composable
private fun StatusDot(connected: Boolean) {
    val transition = rememberInfiniteTransition(label = "conn")
    val alpha by transition.animateFloat(
        0.4f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "connAlpha",
    )
    Box(
        modifier = Modifier
            .size(10.dp)
            .alpha(if (connected) 1f else alpha)
            .clip(CircleShape)
            .background(if (connected) Color(0xFF81C784) else Color(0xFFFFB74D)),
    )
}

@Composable
private fun ControlPadButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .alpha(if (enabled) 1f else 0.35f)
                .clip(CircleShape)
                .background(Color(0xFF2A2A2A))
                .border(1.dp, Color(0xFF4A4A4A), CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF9E9E9E))
    }
}
