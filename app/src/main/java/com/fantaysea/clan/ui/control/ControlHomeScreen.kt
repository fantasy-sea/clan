package com.fantaysea.clan.ui.control

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fantaysea.clan.data.mock.MockServer
import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.data.repository.PairedDevice
import com.fantaysea.clan.core.navigation.HomeDestination
import com.fantaysea.clan.ui.theme.ClanTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val previewOnlineDevice = PairedDevice(
    device = Device(
        id = 1L,
        deviceToken = "preview-mom",
        deviceName = "妈妈的手机",
        platform = "Android",
        model = "HUAWEI Mate 40",
        lastOnlineAt = 0L,
        createdAt = 0L,
    ),
    online = true,
)

private val previewOfflineDevice = PairedDevice(
    device = Device(
        id = 2L,
        deviceToken = "preview-dad",
        deviceName = "爸爸的手机",
        platform = "Android",
        model = "Xiaomi 12",
        lastOnlineAt = null,
        createdAt = 0L,
    ),
    online = false,
)

@Preview(name = "设备列表", group = "页面状态", showBackground = true)
@Composable
fun ControlHomeScreenPreview() {
    ControlHomePreviewContent(
        state = ControlHomeUiState(
            loading = false,
            devices = listOf(previewOnlineDevice, previewOfflineDevice),
        ),
    )
}

@Preview(name = "加载中", group = "页面状态", showBackground = true)
@Composable
private fun ControlHomeLoadingPreview() {
    ControlHomePreviewContent(state = ControlHomeUiState())
}

@Preview(name = "空设备列表", group = "页面状态", showBackground = true)
@Composable
private fun ControlHomeEmptyPreview() {
    ControlHomePreviewContent(state = ControlHomeUiState(loading = false))
}

@Preview(name = "加载失败", group = "页面状态", showBackground = true)
@Composable
private fun ControlHomeErrorPreview() {
    ControlHomePreviewContent(
        state = ControlHomeUiState(
            loading = false,
            error = "网络连接失败，请稍后重试",
        ),
    )
}

@Preview(name = "等待控制确认", group = "页面状态", showBackground = true)
@Composable
private fun ControlHomeWaitingPreview() {
    ControlHomePreviewContent(
        state = ControlHomeUiState(
            loading = false,
            devices = listOf(previewOnlineDevice, previewOfflineDevice),
            pendingControl = previewOnlineDevice,
        ),
    )
}

@Preview(name = "删除确认弹窗", group = "弹窗", showBackground = true)
@Composable
private fun DeleteDeviceConfirmDialogPreview() {
    ClanTheme {
        DeleteDeviceConfirmDialog(
            target = previewOnlineDevice,
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Composable
private fun ControlHomePreviewContent(state: ControlHomeUiState) {
    ClanTheme {
        ControlHomeScreenContent(
            state = state,
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onOpenPairDialog = {},
            onRetry = {},
            onPairDialogDismiss = {},
            onPairCodeChanged = {},
            onPairCodeComplete = {},
            onRequestControl = {},
            onDeleteDevice = {},
            onCancelControl = {},
        )
    }
}

@Composable
fun ControlHomeScreen(
    onNavigate: (HomeDestination) -> Unit,
    onBack: () -> Unit,
) {
    val vm: ControlHomeViewModel = viewModel { ControlHomeViewModel(MockServer.instance) }
    val state by vm.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler { onBack() }

    LaunchedEffect(state.controlSessionPeer) {
        val peer = state.controlSessionPeer ?: return@LaunchedEffect
        vm.consumeSessionPeer()
        onNavigate(HomeDestination.ControlSession(peer.id))
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    ControlHomeScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onOpenPairDialog = vm::openPairDialog,
        onRetry = vm::retry,
        onPairDialogDismiss = vm::closePairDialog,
        onPairCodeChanged = vm::onCodeChanged,
        onPairCodeComplete = vm::submitPairCode,
        onRequestControl = vm::requestControl,
        onDeleteDevice = vm::deleteDevice,
        onCancelControl = vm::cancelControl,
    )
}

@Composable
private fun ControlHomeScreenContent(
    state: ControlHomeUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenPairDialog: () -> Unit,
    onRetry: () -> Unit,
    onPairDialogDismiss: () -> Unit,
    onPairCodeChanged: (String) -> Unit,
    onPairCodeComplete: (String) -> Unit,
    onRequestControl: (PairedDevice) -> Unit,
    onDeleteDevice: (PairedDevice) -> Unit,
    onCancelControl: () -> Unit,
) {
    var deviceToDelete by remember { mutableStateOf<PairedDevice?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "远程控制",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "已配对 ${state.devices.size} 台设备",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onOpenPairDialog) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("添加设备")
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.loading -> LoadingView()
                state.error != null -> ErrorView(state.error, onRetry = onRetry)
                state.devices.isEmpty() -> EmptyView(onAdd = onOpenPairDialog)
                else -> DeviceList(
                    devices = state.devices,
                    onClick = onRequestControl,
                    onDelete = { deviceToDelete = it },
                )
            }
        }
    }

    // 配对码弹窗
    if (state.pairDialogVisible) {
        PairCodeDialog(
            state = state,
            onDismiss = onPairDialogDismiss,
            onCodeChanged = onPairCodeChanged,
            onComplete = onPairCodeComplete,
        )
    }

    // 删除确认
    deviceToDelete?.let { target ->
        DeleteDeviceConfirmDialog(
            target = target,
            onConfirm = {
                onDeleteDevice(target)
                deviceToDelete = null
            },
            onDismiss = { deviceToDelete = null },
        )
    }

    // 等待对方同意控制
    state.pendingControl?.let { target ->
        WaitingControlOverlay(
            device = target,
            onCancel = onCancelControl,
        )
    }
}

@Composable
private fun DeleteDeviceConfirmDialog(
    target: PairedDevice,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移除设备") },
        text = { Text("确定解除与「${target.device.deviceName}」的配对关系吗？") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun LoadingView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("加载失败", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("重试") }
    }
}

@Composable
private fun EmptyView(onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.PhoneAndroid,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text("还没有配对设备", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "在对方设备的「共享屏幕」页获取匹配码",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onAdd, shape = RoundedCornerShape(16.dp)) { Text("添加设备") }
    }
}

@Composable
private fun DeviceList(
    devices: List<PairedDevice>,
    onClick: (PairedDevice) -> Unit,
    onDelete: (PairedDevice) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(devices, key = { it.device.id }) { device ->
            SwipeRevealDeviceCard(
                device = device,
                onClick = { onClick(device) },
                onDelete = { onDelete(device) },
            )
        }
    }
}

@Composable
private fun SwipeRevealDeviceCard(
    device: PairedDevice,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val revealWidth = 92.dp
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val revealPx = with(density) { revealWidth.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .clip(RoundedCornerShape(20.dp)),
    ) {
        // 左滑唤出的删除按钮
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(revealWidth)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.error)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = Color.White)
                Text("删除", color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }

        // 前景卡片
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(device.device.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val target = if (offsetX.value < -revealPx / 2f) -revealPx else 0f
                                offsetX.animateTo(target, spring())
                            }
                        },
                        onDragCancel = {
                            scope.launch { offsetX.animateTo(0f, spring()) }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            offsetX.snapTo((offsetX.value + dragAmount).coerceIn(-revealPx, 0f))
                        }
                    }
                }
                .clickable {
                    if (offsetX.value != 0f) {
                        scope.launch { offsetX.animateTo(0f, spring()) }
                    } else {
                        onClick()
                    }
                }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            DeviceRow(device)
        }
    }
}

@Composable
private fun DeviceRow(device: PairedDevice) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val avatarColor = avatarColor(device.device.deviceName)
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(avatarColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = device.device.deviceName.take(1),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = device.device.deviceName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                StatusPill(online = device.online)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (device.online) device.device.model
                else "${device.device.model} · ${relativeTime(device.device.lastOnlineAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusPill(online: Boolean) {
    val bg = if (online) Color(0xFF2E7D32) else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (online) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (online) Color(0xFF81C784) else MaterialTheme.colorScheme.outline),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (online) "在线" else "离线",
            style = MaterialTheme.typography.labelSmall,
            color = fg,
        )
    }
}

@Composable
private fun WaitingControlOverlay(
    device: PairedDevice,
    onCancel: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(20.dp))
            Text("正在请求控制", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "等待「${device.device.deviceName}」同意…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(20.dp))
            TextButton(onClick = onCancel) { Text("取消") }
        }
    }
}

private fun avatarColor(name: String): Color {
    val palette = listOf(
        Color(0xFFF57C00), Color(0xFF6D4C41), Color(0xFF2E7D32),
        Color(0xFF1565C0), Color(0xFF7B1FA2), Color(0xFFC62828),
    )
    return palette[(name.hashCode().let { if (it < 0) -it else it }) % palette.size]
}

private fun relativeTime(epoch: Long?): String {
    if (epoch == null) return "从未在线"
    val diff = System.currentTimeMillis() - epoch
    val minutes = diff / 60_000
    return when {
        minutes < 1 -> "刚刚在线"
        minutes < 60 -> "${minutes} 分钟前在线"
        diff < 24L * 3600_000 -> "${diff / 3600_000} 小时前在线"
        else -> "${diff / (24L * 3600_000)} 天前在线"
    }
}
