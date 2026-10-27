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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ScreenShare
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fantaysea.clan.core.di.LocalClanRepository
import com.fantaysea.clan.core.di.LocalNavigator
import com.fantaysea.clan.core.navigation.HomeDestination
import com.fantaysea.clan.data.PairingCodeGenerator
import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.media.ScreenCaptureManager
import com.fantaysea.clan.ui.components.CountdownRing


private const val VALIDITY_MS = PairingCodeGenerator.VALIDITY_MS

@Composable
fun ShareScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val repository = LocalClanRepository.current
    val vm: ShareViewModel = viewModel { ShareViewModel(repository) }
    val state by vm.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val captureManager = remember { ScreenCaptureManager(context.applicationContext) }

    BackHandler { navigator.back() }

    LaunchedEffect(state.controlledSessionPeer) {
        val peer = state.controlledSessionPeer ?: return@LaunchedEffect
        vm.consumeSessionPeer()
        navigator.navigateTo(HomeDestination.ControlledSession(peer.id))
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        captureManager.onConsent(result.resultCode, result.data)
        //让vm处理权限请求结果
        vm.onPermissionResult(captureManager.isGranted)
    }

    // 与系统授权保持同步(进程内已授权则直接显示为已授权)；配对码生成不依赖它。
    LaunchedEffect(Unit) {
        if (captureManager.isGranted) vm.onPermissionResult(granted = true)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { navigator.back() }) {
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
                StatusChip(
                    granted = state.permissionState == SharePermissionState.Granted,
                    available = state.pairingAvailable,
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            if (!state.pairingAvailable) {
                ShareUnavailable()
            } else {
                ShareContent(
                    state = state,
                    onRequestCapture = {
                        permissionLauncher.launch(captureManager.createCaptureIntent())
                    },
                    onRetryPairingCode = { vm.retryPairingCode() },
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
private fun StatusChip(granted: Boolean, available: Boolean) {
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
            text = if (!available) "暂不可用" else if (granted) "投屏就绪" else "未授权",
            style = MaterialTheme.typography.labelMedium,
            color = if (granted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ShareUnavailable() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ScreenShare,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("服务端暂未支持", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "配对与实时会话接口尚未开放",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CapturePermissionCard(
    permission: SharePermissionState,
    onRequest: () -> Unit,
) {
    val granted = permission == SharePermissionState.Granted
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
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (granted) ColorGreen.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ScreenShare,
                    contentDescription = null,
                    tint = if (granted) ColorGreen else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (granted) "投屏权限已授权" else "投屏权限未授权",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (granted) "收到控制请求并可共享屏幕"
                    else "配对无需该权限；需要投屏时再授权",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!granted) {
                TextButton(onClick = onRequest) {
                    Text(if (permission == SharePermissionState.Denied) "重新授权" else "授权")
                }
            }
        }
    }
}

@Composable
private fun ShareContent(
    state: ShareUiState,
    onRequestCapture: () -> Unit,
    onRetryPairingCode: () -> Unit,
) {
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
                        text = "${state.platform.ifEmpty { "ANDROID" }} · ${state.model} · 标识 ${state.deviceIdShort}",
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

                if (state.code.isBlank()) {
                    Text(
                        text = "—— ——",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Light,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    val pairError = state.pairError
                    if (pairError != null) {
                        Text(
                            text = pairError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(onClick = onRetryPairingCode) { Text("重新生成") }
                    } else {
                        Text(
                            text = "正在获取配对码…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(
                        text = state.code.chunked(3).joinToString("  "),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 4.sp,
                    )

                    Spacer(modifier = Modifier.height(22.dp))

                    // 有效时间环形指示器
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CountdownRing(
                            fraction = state.remainingMillis.toFloat() / VALIDITY_MS.toFloat(),
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
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "让对方打开「远程控制 → 添加设备」输入上方配对码",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(20.dp))
        CapturePermissionCard(
            permission = state.permissionState,
            onRequest = onRequestCapture,
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
