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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fantaysea.clan.core.di.LocalClanRepository
import com.fantaysea.clan.core.di.LocalNavigator
import com.fantaysea.clan.core.di.LocalScreenCaptureManager
import com.fantaysea.clan.core.navigation.HomeDestination
import com.fantaysea.clan.data.PairingCodeGenerator
import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.service.AccessibilitySettingsTarget
import com.fantaysea.clan.service.AccessibilitySetupCoordinator
import com.fantaysea.clan.service.ClanAccessibilityService
import com.fantaysea.clan.ui.components.CountdownRing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


private const val VALIDITY_MS = PairingCodeGenerator.VALIDITY_MS

/** 返回后给系统同步无障碍状态留的宽限：250ms × 5 ≈ 1.2s，避免刚开启就误报「未开启」。 */
private const val ACCESSIBILITY_RECHECK_ATTEMPTS = 5
private const val ACCESSIBILITY_RECHECK_INTERVAL_MS = 250L

/** 成功态停留时长，让用户看清「已开启」。 */
private const val ACCESSIBILITY_SUCCESS_DISMISS_MS = 1_600L

@Composable
fun ShareScreen() {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val repository = LocalClanRepository.current
    val vm: ShareViewModel = viewModel {
        ShareViewModel(
            repository = repository,
            initialAccessibilityEnabled = ClanAccessibilityService.isEnabled(context),
        )
    }
    val state by vm.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val screenCapture = LocalScreenCaptureManager.current
    val captureState by screenCapture.state.collectAsState()
    val scope = rememberCoroutineScope()

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
        screenCapture.onConsent(result.resultCode, result.data)
        //让vm处理权限请求结果
        vm.onPermissionResult(screenCapture.isAuthorized)
    }

    // 授权存储在应用级 ScreenCaptureManager：令牌被会话消费/采集结束后同步回 UI，
    // 避免从被控页返回时仍显示「已授权」而实际需要重新授权。
    LaunchedEffect(captureState) {
        vm.onPermissionResult(screenCapture.isAuthorized)
    }

    // 无障碍授权在系统设置中完成，没有 Activity 结果回调；回到前台时重新检测并同步给 ViewModel。
    // 刚开启时系统状态可能还没同步完，因此引导在「等待」阶段时会在宽限期内多检测几次，避免误报未开启。
    val view = LocalView.current
    val lifecycleOwner = remember(view) { view.findViewTreeLifecycleOwner() }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            val enabled = ClanAccessibilityService.isEnabled(context)
            vm.onAccessibilityResult(enabled)
            if (enabled) {
                AccessibilitySetupCoordinator.clear()
                return@LifecycleEventObserver
            }
            val guide = vm.uiState.value.accessibilityGuide
            if (guide.visible && guide.stage == AccessibilityGuideStage.Waiting) {
                scope.launch {
                    repeat(ACCESSIBILITY_RECHECK_ATTEMPTS) {
                        delay(ACCESSIBILITY_RECHECK_INTERVAL_MS)
                        if (ClanAccessibilityService.isEnabled(context)) {
                            vm.onAccessibilitySetupReturned(true)
                            AccessibilitySetupCoordinator.clear()
                            return@launch
                        }
                    }
                    vm.onAccessibilitySetupReturned(false)
                    AccessibilitySetupCoordinator.clear()
                }
            } else {
                AccessibilitySetupCoordinator.clear()
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    // 成功态停留一下再自动收起，让用户看清「已开启」；若此时有等待中的控制请求，确认弹窗会随之出现。
    LaunchedEffect(state.accessibilityGuide.visible, state.accessibilityGuide.stage) {
        if (state.accessibilityGuide.visible && state.accessibilityGuide.stage == AccessibilityGuideStage.Enabled) {
            delay(ACCESSIBILITY_SUCCESS_DISMISS_MS)
            vm.dismissAccessibilityGuide()
        }
    }

    val requestCapture = { permissionLauncher.launch(screenCapture.createCaptureIntent()) }
    // 跳系统设置前先记下「本次由本应用发起」，开启成功后服务连接时才能自动把 App 拉回来。
    val openAccessibilitySettings = {
        AccessibilitySetupCoordinator.markAwaitingEnable()
        val target = ClanAccessibilityService.openSettings(context)
        if (target == AccessibilitySettingsTarget.None) {
            AccessibilitySetupCoordinator.clear()
            vm.onAccessibilitySettingsLaunchFailed()
        } else {
            vm.onAccessibilitySettingsOpened(target)
        }
    }
    // 「检测一下」：用户自己在设置里开好后手动触发一次判定。
    val recheckAccessibility = {
        val enabled = ClanAccessibilityService.isEnabled(context)
        vm.onAccessibilitySetupReturned(enabled)
        if (enabled) AccessibilitySetupCoordinator.clear()
    }
    // 发起当前缺失的授权：优先屏幕共享，其次弹出无障碍分步引导。
    val grantMissingPermission = {
        if (state.permissionState != SharePermissionState.Granted) {
            requestCapture()
        } else if (!state.accessibilityGranted) {
            vm.openAccessibilityGuide()
        }
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
                    ready = state.allPermissionsGranted,
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
                    onRequestCapture = requestCapture,
                    onOpenAccessibilityGuide = { vm.openAccessibilityGuide() },
                    onRetryPairingCode = { vm.retryPairingCode() },
                    onRejectPendingControl = { vm.respondControl(accept = false) },
                )
            }
        }
    }

    // 进入页面时的授权提示：两项权限未齐时展示，避免用户不知道需要授权。
    if (state.permissionPromptVisible &&
        !state.accessibilityGuide.visible &&
        state.pairingAvailable &&
        !state.allPermissionsGranted &&
        state.pendingMatch == null &&
        state.pendingControl == null
    ) {
        SharePermissionPromptDialog(
            screenCaptureGranted = state.permissionState == SharePermissionState.Granted,
            accessibilityGranted = state.accessibilityGranted,
            onConfirm = {
                vm.dismissPermissionPrompt()
                grantMissingPermission()
            },
            onDismiss = { vm.dismissPermissionPrompt() },
        )
    }

    // 配对前确认弹窗
    state.pendingMatch?.let { request ->
        PairConfirmDialog(
            requester = request.requester,
            onReject = { vm.respondMatch(accept = false) },
            onAccept = { vm.respondMatch(accept = true) },
        )
    }

    // 控制请求确认弹窗（服务端未开放控制会话时不展示）
    val pendingControl = state.pendingControl?.takeIf { state.controlAvailable }
    if (pendingControl != null) {
        if (state.allPermissionsGranted) {
            ControlRequestDialog(
                controller = pendingControl.controller,
                onReject = { vm.respondControl(accept = false) },
                onAccept = { vm.respondControl(accept = true) },
            )
        } else {
            // “去授权”只隐藏本弹窗，控制请求保持 pending；权限补齐后自动切换为正常确认弹窗。
            var blockedDialogHidden by rememberSaveable { mutableStateOf(false) }
            if (!blockedDialogHidden && !state.accessibilityGuide.visible) {
                ControlPermissionRequiredDialog(
                    controller = pendingControl.controller,
                    screenCaptureGranted = state.permissionState == SharePermissionState.Granted,
                    accessibilityGranted = state.accessibilityGranted,
                    onGrant = {
                        blockedDialogHidden = true
                        grantMissingPermission()
                    },
                    onReject = { vm.respondControl(accept = false) },
                    onDismiss = { blockedDialogHidden = true },
                )
            }
        }
    }

    // 无障碍分步引导放在最后，确保盖在其他弹窗之上。
    if (state.accessibilityGuide.visible) {
        AccessibilitySetupGuide(
            state = state.accessibilityGuide,
            onOpenSettings = openAccessibilitySettings,
            onRecheck = recheckAccessibility,
            onDismiss = {
                AccessibilitySetupCoordinator.clear()
                vm.dismissAccessibilityGuide()
            },
        )
    }
}

@Composable
private fun StatusChip(ready: Boolean, available: Boolean) {
    val bg = if (ready) ColorGreen else MaterialTheme.colorScheme.surfaceVariant
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
                .background(if (ready) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = if (!available) "暂不可用" else if (ready) "已就绪" else "待授权",
            style = MaterialTheme.typography.labelMedium,
            color = if (ready) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ShareUnavailable() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
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

/**
 * 控制权限设置区：屏幕共享(MediaProjection) + 无障碍控制。
 * 两项都授予后才会允许接受控制端的控制请求。
 */
@Composable
private fun SharePermissionSection(
    permission: SharePermissionState,
    accessibilityGranted: Boolean,
    pendingControllerName: String?,
    onRequestCapture: () -> Unit,
    onOpenAccessibilityGuide: () -> Unit,
    onRejectPendingControl: () -> Unit,
) {
    val captureGranted = permission == SharePermissionState.Granted
    val grantedCount = (if (captureGranted) 1 else 0) + (if (accessibilityGranted) 1 else 0)
    val allGranted = grantedCount == 2

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "控制权限",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (allGranted) "已就绪" else "已授权 $grantedCount / 2",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (allGranted) ColorGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "处理对方发起的控制请求前，需完成以下两项授权",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))

            PermissionRow(
                icon = Icons.AutoMirrored.Outlined.ScreenShare,
                title = "屏幕共享",
                granted = captureGranted,
                grantedText = "已授权，对方可实时看到本机画面",
                missingText = "用于把本机屏幕画面发送给对方",
                actionLabel = if (permission == SharePermissionState.Denied) "重新授权" else "授权",
                onAction = onRequestCapture,
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            PermissionRow(
                icon = Icons.Outlined.Accessibility,
                title = "无障碍控制",
                granted = accessibilityGranted,
                grantedText = "已开启，对方可代为执行点击、滑动等操作",
                missingText = "打开后家人才可以替你点屏幕（需要手动开启）",
                actionLabel = "去开启",
                onAction = onOpenAccessibilityGuide,
            )

            if (!allGranted && pendingControllerName != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "「$pendingControllerName」的控制请求正在等待授权",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRejectPendingControl) {
                        Text("拒绝", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    granted: Boolean,
    grantedText: String,
    missingText: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
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
                imageVector = icon,
                contentDescription = null,
                tint = if (granted) ColorGreen else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (granted) grantedText else missingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (granted) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "已授权",
                tint = ColorGreen,
                modifier = Modifier.size(20.dp),
            )
        } else {
            TextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun ShareContent(
    state: ShareUiState,
    onRequestCapture: () -> Unit,
    onOpenAccessibilityGuide: () -> Unit,
    onRetryPairingCode: () -> Unit,
    onRejectPendingControl: () -> Unit,
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

        Spacer(modifier = Modifier.height(20.dp))

        // ── 控制权限（处理控制请求的前置条件）────────────
        SharePermissionSection(
            permission = state.permissionState,
            accessibilityGranted = state.accessibilityGranted,
            pendingControllerName = state.pendingControl
                ?.takeIf { state.controlAvailable }
                ?.controller
                ?.deviceName,
            onRequestCapture = onRequestCapture,
            onOpenAccessibilityGuide = onOpenAccessibilityGuide,
            onRejectPendingControl = onRejectPendingControl,
        )

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
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "配对码用于和其他设备建立信任关系，只有信任的设备才允许向你发起控制",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "让对方打开「远程控制 → 添加设备」输入上方配对码",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SharePermissionPromptDialog(
    screenCaptureGranted: Boolean,
    accessibilityGranted: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (screenCaptureGranted) "还差一步：开启无障碍" else "共享屏幕需要授权") },
        text = {
            Column {
                Text(
                    if (screenCaptureGranted) {
                        "还差一项授权就能接受控制："
                    } else {
                        "有人发起远程控制时，本机需要先完成以下两项授权："
                    },
                )
                Spacer(modifier = Modifier.height(10.dp))
                PromptPermissionLine(
                    title = "屏幕共享",
                    description = "让对方实时看到本机画面",
                    granted = screenCaptureGranted,
                )
                Spacer(modifier = Modifier.height(6.dp))
                PromptPermissionLine(
                    title = "无障碍控制",
                    description = "打开后家人才能替你点屏幕（需手动开启）",
                    granted = accessibilityGranted,
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "任一权限未授予，将无法接受控制请求。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(if (!screenCaptureGranted) "授权屏幕共享" else "去开启无障碍")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("稍后") }
        },
    )
}

@Composable
private fun PromptPermissionLine(
    title: String,
    description: String,
    granted: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (granted) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (granted) ColorGreen else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ControlPermissionRequiredDialog(
    controller: Device,
    screenCaptureGranted: Boolean,
    accessibilityGranted: Boolean,
    onGrant: () -> Unit,
    onReject: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("需要先完成授权") },
        text = {
            Column {
                Text("设备「${controller.deviceName}」请求控制你的设备。")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "接受控制请求需要同时具备屏幕共享与无障碍控制权限。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(10.dp))
                PromptPermissionLine(
                    title = "屏幕共享",
                    description = "让对方实时看到本机画面",
                    granted = screenCaptureGranted,
                )
                Spacer(modifier = Modifier.height(6.dp))
                PromptPermissionLine(
                    title = "无障碍控制",
                    description = "打开后家人才能替你点屏幕（需手动开启）",
                    granted = accessibilityGranted,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onGrant) {
                Text("去授权", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onReject) {
                Text("拒绝", color = MaterialTheme.colorScheme.error)
            }
        },
    )
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
