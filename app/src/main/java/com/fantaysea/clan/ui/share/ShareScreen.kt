package com.fantaysea.clan.ui.share

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.outlined.ScreenShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fantaysea.clan.data.PairingCodeGenerator
import com.fantaysea.clan.data.mock.MockServer
import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.media.ScreenCaptureManager
import com.fantaysea.clan.ui.components.CountdownRing
import com.fantaysea.clan.core.navigation.HomeDestination

private const val VALIDITY_MS = PairingCodeGenerator.VALIDITY_MS

@Composable
fun ShareScreen(
    onNavigate: (HomeDestination) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val vm: ShareViewModel = viewModel { ShareViewModel(MockServer.instance) }
    val state by vm.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val captureManager = remember { ScreenCaptureManager(context.applicationContext) }

    BackHandler { onBack() }

    LaunchedEffect(state.controlledSessionPeer) {
        val peer = state.controlledSessionPeer ?: return@LaunchedEffect
        vm.consumeSessionPeer()
        onNavigate(HomeDestination.ControlledSession(peer.id))
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        captureManager.onConsent(result.resultCode, result.data)
        vm.onPermissionResult(captureManager.isGranted)
    }

    // 与系统授权保持同步(进程内已授权则直接进入)
    LaunchedEffect(Unit) { vm.onPermissionResult(captureManager.isGranted) }

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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "共享屏幕",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "本机作为被控端，等待配对与控制",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusChip(granted = state.permissionState == SharePermissionState.Granted)
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when (state.permissionState) {
                SharePermissionState.Granted -> ShareContent(state = state)
                else -> PermissionGate(
                    denied = state.permissionState == SharePermissionState.Denied,
                    onRequest = {
                        permissionLauncher.launch(captureManager.createCaptureIntent())
                    },
                )
            }
        }
    }

    // 配对前确认弹窗
    state.pendingMatch?.let { request ->
        PairConfirmDialog(
            requester = request.requester,
            onReject = { vm.respondMatch(accept = false) },
            onAccept = { vm.respondMatch(accept = true) },
        )
    }

    // 控制请求确认弹窗
    state.pendingControl?.let { request ->
        ControlRequestDialog(
            controller = request.controller,
            onReject = { vm.respondControl(accept = false) },
            onAccept = { vm.respondControl(accept = true) },
        )
    }
}

@Composable
private fun StatusChip(granted: Boolean) {
    val bg = if (granted) ColorGreen else MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = Modifier
            .padding(end = 16.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (granted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (granted) "投屏就绪" else "未授权",
            style = MaterialTheme.typography.labelMedium,
            color = if (granted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionGate(denied: Boolean, onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.ScreenShare,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = if (denied) "未获得屏幕捕获授权" else "授权屏幕捕获",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (denied) {
                "共享屏幕需要系统屏幕捕获权限。\n授权后仅在主动连接时才会开启投屏。"
            } else {
                "共享屏幕需要系统屏幕捕获权限。\n授权后本机屏幕画面将通过安全通道传输给控制端。"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(28.dp))
        Button(
            onClick = onRequest,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text(if (denied) "重新授权" else "同意并授权")
        }
    }
}

@Composable
private fun ShareContent(state: ShareUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── 本机设备信息 ──────────────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = state.deviceName.ifEmpty { "本机设备" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${state.platform.ifEmpty { "Android" }} · ${state.model} · 标识 ${state.deviceTokenShort}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── 配对码卡片 ────────────────────────────────
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "配对码",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(14.dp))

                if (state.generating) {
                    Text(
                        text = "—— ——",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Light,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.outline,
                    )
                } else {
                    Text(
                        text = state.code.chunked(3).joinToString("  "),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 4.sp,
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                // 有效时间环形指示器
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CountdownRing(
                        fraction = if (VALIDITY_MS <= 0) 0f else (state.remainingMillis.toFloat() / VALIDITY_MS.toFloat()),
                        remainingSeconds = (state.remainingMillis / 1000L).toInt(),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "有效时间",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${(state.remainingMillis / 1000L).toInt()} 秒",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "到期自动刷新配对码",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "让对方打开「远程控制 → 添加设备」输入上方配对码",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PairConfirmDialog(
    requester: Device,
    onReject: () -> Unit,
    onAccept: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onReject,
        title = { Text("配对请求") },
        text = {
            Column {
                Text("设备「${requester.deviceName}」请求与本机配对。")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${requester.platform} · ${requester.model}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "确认后，对方即可在设备列表中看到本机并随时发起控制。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text("确认配对", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onReject) { Text("拒绝") }
        },
    )
}

@Composable
private fun ControlRequestDialog(
    controller: Device,
    onReject: () -> Unit,
    onAccept: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onReject,
        title = { Text("控制请求") },
        text = {
            Column {
                Text("设备「${controller.deviceName}」请求控制你的设备。")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "确认后，对方将实时看到本机屏幕画面，并可代为操作。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text("允许控制", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onReject) { Text("拒绝") }
        },
    )
}

private val ColorGreen = androidx.compose.ui.graphics.Color(0xFF2E7D32)