package com.fantaysea.clan.ui.session

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fantaysea.clan.core.di.LocalClanRepository
import com.fantaysea.clan.core.di.LocalNavigator
import com.fantaysea.clan.core.di.LocalScreenCaptureManager
import com.fantaysea.clan.core.di.LocalWebRtcManager
import com.fantaysea.clan.data.model.ControlCommand
import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.data.model.LocalPermissions
import com.fantaysea.clan.data.model.SessionRole
import com.fantaysea.clan.data.model.SessionStatus
import com.fantaysea.clan.data.repository.ClanRepository
import com.fantaysea.clan.data.repository.RemoteEvent
import com.fantaysea.clan.media.WebRtcStartResult
import com.fantaysea.clan.media.WebRtcState
import com.fantaysea.clan.service.AccessibilityControlExecutor
import com.fantaysea.clan.service.ClanAccessibilityService
import com.fantaysea.clan.service.ControlExecutionResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * 被控端页面。
 *
 * 进入会话后由 [LocalWebRtcManager] 启动屏幕采集/WebRTC 视频发送；页面本身负责：
 * - 消费 [LocalScreenCaptureManager] 的一次性屏幕捕获授权（缺失时引导重新授权）；
 * - 回传 PERMISSION_STATUS、接收 CONTROL 并交给 [AccessibilityControlExecutor] 真正执行；
 * - 按执行结果回传 CONTROL_RESULT，并展示媒体状态与会话计时。
 *
 * 执行前会再次确认会话仍是 CONTROLLED/CONNECTED 且媒体已连通；缺少无障碍服务时回传
 * FAILED 和原因，不再静默 ACK。
 */
@Composable
fun ControlledSessionScreen(peer: Device) {
    val navigator = LocalNavigator.current
    val repository = LocalClanRepository.current
    val screenCapture = LocalScreenCaptureManager.current
    val webRtc = LocalWebRtcManager.current
    val active by repository.activeSession.collectAsState()
    val session = active?.takeIf { it.role == SessionRole.CONTROLLED && it.peer.id == peer.id }
    val sessionId = session?.info?.sessionId
    val localPermissions by repository.localPermissions.collectAsState()
    val captureState by screenCapture.state.collectAsState()
    val mediaState by webRtc.state.collectAsState()
    val accessibilityRunning by ClanAccessibilityService.running.collectAsState()

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showStopConfirm by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }
    var lastCommand by remember { mutableStateOf<String?>(null) }
    var lastCommandStatus by remember { mutableStateOf<String?>(null) }
    var lastNotifiedFailure by remember(sessionId) { mutableStateOf<String?>(null) }
    var consentDialogVisible by rememberSaveable(sessionId) { mutableStateOf(false) }
    var consentAttempted by rememberSaveable(sessionId) { mutableStateOf(false) }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        screenCapture.onConsent(result.resultCode, result.data)
        consentAttempted = true
        consentDialogVisible = false
        val sid = sessionId
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            sid?.let { webRtc.startControlled(it) }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("未授权屏幕捕获，会话已结束")
                if (sid != null) {
                    runCatching { repository.endSession(sid) }
                    webRtc.stopFor(sid)
                }
            }
        }
    }

    BackHandler(enabled = session != null) { showStopConfirm = true }
    // 媒体失败时保留会话页展示具体原因，避免自动返回后 Toast/Snackbar 一闪而过。
    RememberSessionEnd(repository, sessionId) {
        if (webRtc.state.value !is WebRtcState.Failed) navigator.back()
    }

    // 进入 CONNECTING 后启动被控端媒体：有授权则启动前台服务；没有授权时提示重新授权。
    LaunchedEffect(sessionId, session?.info?.status, captureState) {
        val sid = sessionId ?: return@LaunchedEffect
        val currentSession = session ?: return@LaunchedEffect
        if (currentSession.info.status != SessionStatus.CONNECTING &&
            currentSession.info.status != SessionStatus.CONNECTED
        ) {
            return@LaunchedEffect
        }
        when (webRtc.startControlled(sid)) {
            WebRtcStartResult.NEEDS_CAPTURE_CONSENT -> {
                if (!consentAttempted) consentDialogVisible = true
            }

            WebRtcStartResult.STARTED,
            WebRtcStartResult.ALREADY_STARTED -> Unit

            WebRtcStartResult.UNAVAILABLE -> scope.launch {
                snackbarHostState.showSnackbar("当前无法启动屏幕共享")
            }
        }
    }

    // 媒体失败时 manager 会结束会话；页面只展示失败原因。
    LaunchedEffect(mediaState) {
        val failed = mediaState as? WebRtcState.Failed ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(failed.reason)
    }

    // 无障碍服务可能被用户/系统中途关闭：把“当前是否真的能执行手势”同步到本机权限，
    // 再由下面的权限回传效应通知控制端。只有屏幕捕获状态需要保留原值。
    LaunchedEffect(sessionId, accessibilityRunning) {
        if (sessionId == null) return@LaunchedEffect
        val current = repository.localPermissions.value
        if (current.accessibility != accessibilityRunning) {
            repository.updateLocalPermissions(
                screenCapture = current.screenCapture,
                accessibility = accessibilityRunning,
            )
        }
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
                    lastCommandStatus = "执行中…"
                    scope.launch {
                        val active = repository.activeSession.value
                        if (active == null ||
                            active.info.sessionId != sid ||
                            active.role != SessionRole.CONTROLLED ||
                            active.info.status != SessionStatus.CONNECTED
                        ) {
                            return@launch
                        }
                        // 被控端自己的视频发送也必须处于连通状态：画面没发出去时执行操作，
                        // 控制端看不到结果，不符合“看得见才允许操作”的前提。
                        val result = if (webRtc.state.value is WebRtcState.Connected) {
                            AccessibilityControlExecutor.execute(event.command)
                        } else {
                            ControlExecutionResult.Failure("画面未就绪，无法执行")
                        }
                        lastCommandStatus = when (result) {
                            ControlExecutionResult.Success -> "已执行"
                            is ControlExecutionResult.Failure -> "执行失败：${result.reason}"
                        }
                        if (result is ControlExecutionResult.Failure) {
                            // 相同原因只提示一次；独立协程展示，避免 Snackbar 的展示时长拖慢结果回传。
                            if (lastNotifiedFailure != result.reason) {
                                lastNotifiedFailure = result.reason
                                scope.launch {
                                    snackbarHostState.showSnackbar("远程操作失败：${result.reason}")
                                }
                            }
                        } else {
                            lastNotifiedFailure = null
                        }
                        // 手势执行期间会话可能已经结束：不再回传注定失败的 CONTROL_RESULT。
                        if (repository.activeSession.value?.info?.sessionId == sid) {
                            runCatching {
                                repository.sendControlResult(
                                    sessionId = sid,
                                    requestId = event.requestId,
                                    ok = result is ControlExecutionResult.Success,
                                    reason = (result as? ControlExecutionResult.Failure)?.reason,
                                )
                            }.onFailure {
                                scope.launch {
                                    snackbarHostState.showSnackbar(it.message ?: "回传指令结果失败")
                                }
                            }
                        }
                    }
                }

                is RemoteEvent.SessionError -> if (event.sessionId == sid) {
                    scope.launch { snackbarHostState.showSnackbar(event.message) }
                }

                else -> Unit
            }
        }
    }

    if (session == null) {
        SessionUnavailable(
            onBack = { navigator.back() },
            reason = (mediaState as? WebRtcState.Failed)?.reason,
        )
        return
    }

    // 被控期间保持屏幕常亮：锁屏后 MediaProjection 只能采到黑屏。
    val view = LocalView.current
    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000.milliseconds)
            seconds++
        }
    }

    val mediaStatusText = when (val state = mediaState) {
        WebRtcState.Idle -> "等待画面连接"
        WebRtcState.Preparing -> "正在准备屏幕采集…"
        WebRtcState.Negotiating -> "正在建立画面连接…"
        WebRtcState.Connected -> "画面发送中"
        WebRtcState.Reconnecting -> "画面重连中…"
        is WebRtcState.Failed -> "画面连接失败：${state.reason}"
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
                text = "正在将本机屏幕画面发送给「${peer.deviceName}」\n" +
                    "$mediaStatusText\n已共享 ${formatDuration(seconds)}",
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
                        text = buildString {
                            append("最近控制指令：")
                            append(command)
                            lastCommandStatus?.let { append("（$it）") }
                        },
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
                        sessionId?.let { sid ->
                            runCatching { repository.endSession(sid) }
                            webRtc.stopFor(sid)
                        }
                        navigator.back()
                    }
                }) { Text("停止", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirm = false }) { Text("继续共享") }
            },
        )
    }

    // 进程内没有可用的单次授权时（例如被系统回收后重进），引导用户重新授权。
    if (consentDialogVisible) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("需要屏幕捕获授权") },
            text = { Text("共享屏幕需要系统屏幕捕获授权。授权后本机画面才会发送给控制端。") },
            confirmButton = {
                TextButton(onClick = {
                    consentDialogVisible = false
                    consentAttempted = true
                    consentLauncher.launch(screenCapture.createCaptureIntent())
                }) { Text("授权并开始") }
            },
            dismissButton = {
                TextButton(onClick = {
                    consentDialogVisible = false
                    consentAttempted = true
                    scope.launch {
                        sessionId?.let { sid ->
                            runCatching { repository.endSession(sid) }
                            webRtc.stopFor(sid)
                        }
                        navigator.back()
                    }
                }) { Text("结束会话", color = MaterialTheme.colorScheme.error) }
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
                text = if (permissions.accessibility) {
                    "无障碍控制：已开启（可执行远程操作）"
                } else {
                    "无障碍控制：未开启（家人无法操作本机）"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (permissions.accessibility) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
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
