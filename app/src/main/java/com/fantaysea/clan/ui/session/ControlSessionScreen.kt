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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fantaysea.clan.core.navigation.LocalNavigator
import com.fantaysea.clan.data.mock.MockServer
import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.data.repository.ClanRepository
import com.fantaysea.clan.data.repository.RemoteEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 控制端页面(桩): 展示被控端画面占位 + 远程操作控制条。
 * TODO(真实实现): WebRTC 远端视频轨渲染到 SurfaceViewRenderer,
 *   点击/滑动/按键通过数据通道发送给被控端执行。
 */
@Composable
fun ControlSessionScreen(peer: Device) {
    val navigator = LocalNavigator.current
    val repository: ClanRepository = MockServer.instance
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showEndConfirm by remember { mutableStateOf(false) }
    var seconds by remember { mutableStateOf(0) }

    BackHandler { showEndConfirm = true }
    RememberSessionEnd(repository) { navigator.back() }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            seconds++
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF141414))) {
        // ── 视频流占位区(预留 WebRTC VideoTrack) ──
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                    text = "远程画面(模拟) · 真实画面经 WebRTC 视频流传输",
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
                ConnectingDot()
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "${peer.deviceName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
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

        // ── 底部控制条(桩) ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xE6141414))
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                ControlPadButton(Icons.Filled.KeyboardArrowLeft, "返回") {
                    scope.launch { snackbarHostState.showSnackbar("已发送「返回」指令(模拟)") }
                }
                ControlPadButton(Icons.Filled.Home, "主页") {
                    scope.launch { snackbarHostState.showSnackbar("已发送「主页」指令(模拟)") }
                }
                ControlPadButton(Icons.Filled.Apps, "最近任务") {
                    scope.launch { snackbarHostState.showSnackbar("已发送「最近任务」指令(模拟)") }
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
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp),
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
                    scope.launch { repository.endSession(peer.id) }
                    navigator.back()
                }) { Text("结束") }
            },
            dismissButton = {
                TextButton(onClick = { showEndConfirm = false }) { Text("继续控制") }
            },
        )
    }
}

@Composable
private fun ConnectingDot() {
    val transition = rememberInfiniteTransition(label = "conn")
    val alpha by transition.animateFloat(
        0.4f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "connAlpha",
    )
    Box(
        modifier = Modifier
            .size(10.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(Color(0xFF81C784)),
    )
}

@Composable
private fun ControlPadButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFF2A2A2A))
                .border(1.dp, Color(0xFF4A4A4A), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color(0xFF9E9E9E))
    }
}