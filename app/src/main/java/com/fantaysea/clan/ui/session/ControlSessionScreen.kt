package com.fantaysea.clan.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Lock
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
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fantaysea.clan.core.di.LocalClanRepository
import com.fantaysea.clan.core.di.LocalNavigator
import com.fantaysea.clan.core.di.LocalWebRtcManager
import com.fantaysea.clan.data.model.ControlCommand
import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.data.model.SessionRole
import com.fantaysea.clan.data.model.SessionStatus
import com.fantaysea.clan.data.repository.RemoteEvent
import com.fantaysea.clan.media.RemoteVideoSize
import com.fantaysea.clan.media.RemoteVideoState
import com.fantaysea.clan.media.WebRtcStartResult
import com.fantaysea.clan.media.WebRtcState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * 控制端页面（有状态入口）。
 *
 * 进入 CONNECTING 后由 [LocalWebRtcManager] 创建 PeerConnection 并发送 offer；
 * `SESSION_CONNECTED` 只在 PeerConnection 真正连通后由 manager 上报。
 * 页面负责渲染远端画面、按画面区域归一化触摸坐标，并通过 CONTROL 发送
 * 点击/滑动/返回/Home/最近任务，展示 PERMISSION_STATUS 与命令结果。
 *
 * 该函数只承担副作用与数据绑定：读取 CompositionLocal、启动/停止媒体、收集仓库事件。
 * 纯界面部分在 [ControlSessionScreenContent]，可在不依赖真实媒体与仓库的情况下预览。
 *
 * 远端画面按 [fitVideoRect] 等比居中；触摸事件绑定在同一矩形上，避免黑边造成坐标偏移。
 *
 * 操作前提是“视频流正常”：服务端 CONNECTED + 本地 PeerConnection Connected + 已收到首帧
 * + 最近仍持续收帧。任一条件不满足时页面锁定全部操作，`sendCommand` 也会在发送前
 * 再次校验，避免状态竞态期间误发 CONTROL。
 */
@Composable
fun ControlSessionScreen(peer: Device) {
    val navigator = LocalNavigator.current
    val repository = LocalClanRepository.current
    val webRtc = LocalWebRtcManager.current
    val active by repository.activeSession.collectAsState()
    val session = active?.takeIf { it.role == SessionRole.CONTROLLER && it.peer.id == peer.id }
    val sessionId = session?.info?.sessionId
    val connected = session?.info?.status == SessionStatus.CONNECTED
    val mediaState by webRtc.state.collectAsState()
    val remoteVideoSize by webRtc.remoteVideoSize.collectAsState()
    val remoteVideoState by webRtc.remoteVideoState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showEndConfirm by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }
    var permissionStatus by remember(sessionId) { mutableStateOf<RemoteEvent.PermissionStatus?>(null) }
    var lastNotifiedFailure by remember(sessionId) { mutableStateOf<String?>(null) }

    BackHandler(enabled = session != null) { showEndConfirm = true }
    // 媒体失败时保留会话页展示具体原因，避免自动返回后 Toast/Snackbar 一闪而过。
    RememberSessionEnd(repository, sessionId) {
        if (webRtc.state.value !is WebRtcState.Failed) navigator.back()
    }

    // 进入 CONNECTING 后创建 PeerConnection 并发 offer；重复调用由 manager 幂等处理，
    // Activity 重建/旋转时只会重新绑定渲染器，不会重建媒体链路。
    LaunchedEffect(sessionId, session?.info?.status) {
        val sid = sessionId ?: return@LaunchedEffect
        val currentSession = session ?: return@LaunchedEffect
        if (currentSession.info.status != SessionStatus.CONNECTING &&
            currentSession.info.status != SessionStatus.CONNECTED
        ) {
            return@LaunchedEffect
        }
        if (webRtc.startController(sid) == WebRtcStartResult.UNAVAILABLE) {
            snackbarHostState.showSnackbar("当前无法启动视频连接")
        }
    }

    // 媒体失败时 manager 会结束服务端会话；这里只负责把原因展示出来。
    LaunchedEffect(mediaState) {
        val failed = mediaState as? WebRtcState.Failed ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(failed.reason)
    }

    LaunchedEffect(sessionId) {
        val sid = sessionId ?: return@LaunchedEffect
        repository.events.collect { event ->
            when (event) {
                is RemoteEvent.PermissionStatus -> if (event.sessionId == sid) {
                    permissionStatus = event
                }

                is RemoteEvent.ControlCommandResult -> if (event.sessionId == sid) {
                    if (event.result == RemoteEvent.ControlResultStatus.FAILED) {
                        // 连续失败（例如被控端无障碍被关闭）只提示一次相同原因，避免刷屏。
                        val reason = event.reason ?: "被控端未执行"
                        if (lastNotifiedFailure != reason) {
                            lastNotifiedFailure = reason
                            scope.launch { snackbarHostState.showSnackbar("操作失败：$reason") }
                        }
                    } else {
                        lastNotifiedFailure = null
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

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000.milliseconds)
            seconds++
        }
    }

    val sendCommand: (ControlCommand) -> Unit = { command ->
        // 发送前按实时值复核所有前置条件：拖动过程中画面可能刚好停滞/重连，
        // 只看重组时捕获的布尔值不够。
        val activeSession = repository.activeSession.value
        val canSend = activeSession != null &&
            activeSession.info.sessionId == sessionId &&
            activeSession.role == SessionRole.CONTROLLER &&
            activeSession.info.status == SessionStatus.CONNECTED &&
            webRtc.state.value is WebRtcState.Connected &&
            webRtc.remoteVideoState.value == RemoteVideoState.Streaming &&
            webRtc.remoteVideoSize.value != null
        if (!canSend) {
            scope.launch { snackbarHostState.showSnackbar("画面未就绪，暂不能操作") }
        } else {
            val sid = sessionId
            if (sid != null) {
                scope.launch {
                    runCatching { repository.sendControl(sid, command) }
                        .onFailure {
                            snackbarHostState.showSnackbar(
                                it.message ?: "发送控制指令失败"
                            )
                        }
                }
            }
        }
    }

    ControlSessionScreenContent(
        state = ControlSessionUiState(
            peer = peer,
            connected = connected,
            mediaState = mediaState,
            remoteVideoState = remoteVideoState,
            remoteVideoSize = remoteVideoSize,
            permissionStatus = permissionStatus,
            seconds = seconds,
            endConfirmVisible = showEndConfirm,
        ),
        snackbarHostState = snackbarHostState,
        video = { modifier -> RemoteVideoSurface(manager = webRtc, modifier = modifier) },
        onRequestEnd = { showEndConfirm = true },
        onConfirmEnd = {
            showEndConfirm = false
            scope.launch {
                sessionId?.let { sid ->
                    runCatching { repository.endSession(sid) }
                    webRtc.stopFor(sid)
                }
                navigator.back()
            }
        },
        onDismissEnd = { showEndConfirm = false },
        onTap = { x, y -> sendCommand(ControlCommand.Tap(x, y)) },
        onSwipe = { x1, y1, x2, y2 -> sendCommand(ControlCommand.Swipe(x1, y1, x2, y2)) },
        onBackCommand = { sendCommand(ControlCommand.Back) },
        onHomeCommand = { sendCommand(ControlCommand.Home) },
        onRecentsCommand = { sendCommand(ControlCommand.Recents) },
    )
}

/**
 * 控制端界面渲染所需的全部状态。
 *
 * 只包含可展示/可交互的数据，不包含媒体管理器或仓库引用，便于构造任意状态做预览。
 */
@Immutable
internal data class ControlSessionUiState(
    val peer: Device,
    val connected: Boolean = false,
    val mediaState: WebRtcState = WebRtcState.Idle,
    val remoteVideoState: RemoteVideoState = RemoteVideoState.Idle,
    val remoteVideoSize: RemoteVideoSize? = null,
    val permissionStatus: RemoteEvent.PermissionStatus? = null,
    val seconds: Int = 0,
    val endConfirmVisible: Boolean = false,
) {
    /** WebRTC 已连通、已收到首帧，并且最近仍在持续收帧。 */
    val videoReady: Boolean
        get() = mediaState is WebRtcState.Connected &&
            remoteVideoState == RemoteVideoState.Streaming &&
            remoteVideoSize != null

    /**
     * 控制端是否允许操作被控端。
     *
     * 服务端只在会话 CONNECTED 后转发 CONTROL，因此服务端状态也必须满足；否则即使本地
     * 已看到画面，发送也会被服务端拒绝。
     */
    val canControl: Boolean
        get() = connected && videoReady
}

/**
 * 控制端界面（纯 UI，无副作用）。
 *
 * 不读取 CompositionLocal、不注册 BackHandler、不启动/停止媒体，也不订阅仓库事件：
 * 状态全部由 [state] 传入，交互全部通过回调上抛，远端画面通过 [video] 插槽注入。
 * 因此可以直接在 `@Preview` 中渲染（见 `ControlSessionPreview.kt`）。
 *
 * 生产代码传入真实的 [RemoteVideoSurface]；预览传入静态占位画面。
 */
@Composable
internal fun ControlSessionScreenContent(
    state: ControlSessionUiState,
    snackbarHostState: SnackbarHostState,
    video: @Composable (Modifier) -> Unit,
    onRequestEnd: () -> Unit,
    onConfirmEnd: () -> Unit,
    onDismissEnd: () -> Unit,
    onTap: (Double, Double) -> Unit,
    onSwipe: (Double, Double, Double, Double) -> Unit,
    onBackCommand: () -> Unit,
    onHomeCommand: () -> Unit,
    onRecentsCommand: () -> Unit,
) {
    val peer = state.peer
    val canControl = state.canControl

    Column (
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141414))
    ) {


        // ── 顶部状态条：带上状态栏 inset，避免压到系统状态栏下面 ──
        Surface(
            modifier = Modifier
                .fillMaxWidth(),
//                .statusBarsPadding(),
            color = Color(0xCC141414),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(canControl)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = peer.deviceName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                    val statusText = when {
                        state.mediaState is WebRtcState.Failed -> "媒体连接失败"
                        state.mediaState is WebRtcState.Reconnecting -> "画面重连中…"
                        canControl -> "画面正常 · 可远程操作"
                        state.connected && state.remoteVideoState == RemoteVideoState.Stalled ->
                            "画面中断 · 操作暂停"

                        state.connected -> "已连接 · 等待画面"
                        else -> "连接中…"
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (canControl) Color(0xFF81C784) else Color(0xFFFFB74D),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatDuration(state.seconds),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFBDBDBD),
                )
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(onClick = onRequestEnd) {
                    Text("结束", color = Color(0xFFFF6B6B))
                }
            }
        }
        // ── 远端画面：只占顶部状态条与底部控制条之间的剩余高度 ──
        // weight(1f) 让容器拿到两条栏测量后的剩余空间（fillMaxSize 会吃掉全部高度，
        // 把底栏挤成 0 高）；fillMaxWidth 保证等比缩放后画面水平居中。
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val density = LocalDensity.current
            val containerWidth = with(density) { maxWidth.toPx() }
            val containerHeight = with(density) { maxHeight.toPx() }
            val videoSize = state.remoteVideoSize
            val videoRect = videoSize?.let {
                fitVideoRect(it.width, it.height, containerWidth, containerHeight)
            }
            val videoModifier = if (videoRect != null) {
                Modifier
                    .align(Alignment.Center)
                    .size(
                        width = with(density) { videoRect.width.toDp() },
                        height = with(density) { videoRect.height.toDp() },
                    )
            } else {
                Modifier.fillMaxSize()
            }

            Box(
                modifier = videoModifier,
                contentAlignment = Alignment.Center,
            ) {
                video(Modifier.fillMaxSize(1F))
                // 触摸层独立于 AndroidView，保证 SurfaceView 不会吞掉 Compose 手势；
                // 坐标以视频显示区域为 [0,1]，黑边区域不产生控制指令。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(canControl, videoSize) {
                            // 视频未就绪/停滞/重连时不识别任何手势；发送侧还会再复核一次。
                            if (!canControl || videoSize == null) {
                                return@pointerInput
                            }
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val start = down.position
                                var end = start
                                var dragged = false
                                var pressed = true
                                while (pressed) {
                                    val event = awaitPointerEvent()
                                    val change =
                                        event.changes.firstOrNull { it.id == down.id } ?: break
                                    end = change.position
                                    if ((end - start).getDistance() > viewConfiguration.touchSlop) dragged =
                                        true
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
                                    onSwipe(startX, startY, endX, endY)
                                } else {
                                    onTap(startX, startY)
                                }
                            }
                        },
                )
                if (videoSize == null) {
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
                            text = when (state.mediaState) {
                                WebRtcState.Idle,
                                WebRtcState.Preparing -> "正在准备画面…"

                                WebRtcState.Negotiating -> "正在建立画面连接…"
                                WebRtcState.Reconnecting -> "画面重连中…"
                                WebRtcState.Connected -> "正在等待第一帧…"
                                is WebRtcState.Failed -> "媒体连接失败"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF757575),
                        )
                    }
                }
            }

            // 操作锁提示叠在画面顶部；不消费触摸事件，锁定期间由触摸层自己 early-return。
            if (!canControl) {
                LockBanner(
                    text = controlLockMessage(state),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                )
            }

            // Snackbar 叠在画面区域内、控制条上方，不参与 Column 布局，
            // 避免显示/消失时挤压画面（原来的 150dp 占位会顶起底栏）。
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
            )
        }

        // ── 底部控制条：带上导航栏 inset，避免压到系统导航栏下面 ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xE6141414))
//                .navigationBarsPadding()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            if (!canControl) {
                Text(
                    text = "画面正常后才能操作被控端",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFFB74D),
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            if (state.permissionStatus?.accessibility == false) {
                Text(
                    text = "被控端未开启无障碍控制，操作可能无效",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFF8A65),
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                ControlPadButton(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    "返回",
                    enabled = canControl,
                ) {
                    onBackCommand()
                }
                ControlPadButton(Icons.Filled.Home, "主页", enabled = canControl) {
                    onHomeCommand()
                }
                ControlPadButton(Icons.Filled.Apps, "最近任务", enabled = canControl) {
                    onRecentsCommand()
                }
            }

        }
    }

    if (state.endConfirmVisible) {
        AlertDialog(
            onDismissRequest = onDismissEnd,
            title = { Text("结束控制") },
            text = { Text("确定结束对「${peer.deviceName}」的远程控制吗？") },
            confirmButton = {
                TextButton(onClick = onConfirmEnd) { Text("结束") }
            },
            dismissButton = {
                TextButton(onClick = onDismissEnd) { Text("继续控制") }
            },
        )
    }
}

@Composable
internal fun SessionUnavailable(onBack: () -> Unit, reason: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "会话已结束",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            reason ?: "远程控制会话已结束或尚未建立，请重新发起控制。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onBack) { Text("返回") }
    }
}

/**
 * 画面未就绪时叠在视频顶部的锁定提示。
 *
 * 不注册 pointerInput，因此不会拦截下层触摸层的事件；锁定本身由触摸层 early-return 实现。
 */
@Composable
private fun LockBanner(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = Color(0xCC000000),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = Color(0xFFFFB74D),
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFE0E0E0),
            )
        }
    }
}

/** 把当前媒体/视频状态翻译成用户能理解的锁定原因。 */
private fun controlLockMessage(state: ControlSessionUiState): String = when {
    state.mediaState is WebRtcState.Failed -> "媒体连接失败，无法操作"
    state.mediaState is WebRtcState.Reconnecting -> "画面重连中，操作已锁定"
    !state.connected -> "正在同步会话状态，操作暂不可用"
    state.remoteVideoState == RemoteVideoState.Stalled -> "画面已中断，操作已锁定"
    state.remoteVideoState == RemoteVideoState.WaitingFirstFrame ||
        state.remoteVideoSize == null -> "正在等待被控端画面…"

    else -> "画面未就绪，操作已锁定"
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
    Icon(
        icon,
        contentDescription = label,
        tint = if (enabled) Color.White else Color(0xFF5F5F5F),
        modifier = Modifier
            .size(26.dp)
            .clickable(enabled = enabled, onClick = onClick)
    )
}
