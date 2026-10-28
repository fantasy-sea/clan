package com.fantaysea.clan.ui.control

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fantaysea.clan.core.di.LocalClanRepository
import com.fantaysea.clan.core.di.LocalNavigator
import com.fantaysea.clan.core.navigation.HomeDestination
import com.fantaysea.clan.data.repository.PairedDevice
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 远程设备列表页
 */
@Composable
fun ControlHomeScreen() {
    val navigator = LocalNavigator.current
    val repository = LocalClanRepository.current
    val vm: ControlHomeViewModel = viewModel { ControlHomeViewModel(repository) }
    val state by vm.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val actions = remember(vm, navigator) {
        ControlHomeActions(
            onBack = navigator::back,
            //重试
            onRetry = vm::retry,
            //打开配对码弹窗
            onOpenPairDialog = vm::openPairDialog,
            //取消配对弹窗
            onPairDialogDismiss = vm::closePairDialog,
            //配对码发生了变化
            onPairCodeChanged = vm::onCodeChanged,
            //输入配对码
            onPairCodeComplete = vm::submitPairCode,
            //请求控制指定设备
            onRequestControl = vm::requestControl,
            //打开删除设备弹窗
            onAskDeleteDevice = vm::askDeleteDevice,
            //删除设备
            onDeleteDevice = vm::deleteDevice,
            //取消删除
            onDismissDeleteDevice = vm::dismissDeleteDevice,
            //等待配对过程中控制方主动发起取消
            onCancelControl = vm::cancelControl,
            //长按进入多选
            onLongPressDevice = vm::enterSelection,
            //多选态点击切换勾选
            onToggleDeviceSelection = vm::toggleSelection,
            //退出多选
            onExitSelection = vm::exitSelectionMode,
            //全选 / 取消全选
            onSelectAll = vm::selectAllDevices,
            onClearSelection = vm::clearSelection,
            //批量删除
            onAskBatchDelete = vm::askBatchDeleteDevices,
            onConfirmBatchDelete = vm::deleteSelectedDevices,
            onDismissBatchDelete = vm::dismissBatchDelete,
            //设备编辑
            onAskEditDevice = vm::askEditDevice,
            onEditDeviceNameChanged = vm::onEditDeviceNameChanged,
            onConfirmEditDevice = vm::confirmEditDevice,
            onDismissEditDevice = vm::dismissEditDevice,
        )
    }
    //监听是否有被控设备接入
    LaunchedEffect(state.controlSessionPeer) {
        val peer = state.controlSessionPeer ?: return@LaunchedEffect
        //清空被控设备信息
        vm.consumeSessionPeer()
        //前往设备控制页
        navigator.navigateTo(HomeDestination.ControlSession(peer.id))
    }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    //多选态下返回键先退出多选, 而不是离开页面
    BackHandler(enabled = state.selectionMode) { vm.exitSelectionMode() }

    ControlHomeScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        actions = actions,
    )
}

@Composable
internal fun ControlHomeScreenContent(
    state: ControlHomeUiState,
    snackbarHostState: SnackbarHostState,
    actions: ControlHomeActions,
) {
    val devices = (state.pageState as? DeviceListState.Success)?.devices.orEmpty()
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (state.selectionMode) {
                SelectionTopBar(
                    selectedCount = state.selectedDeviceIds.size,
                    totalCount = devices.size,
                    allSelected = devices.isNotEmpty() &&
                        state.selectedDeviceIds.size == devices.size,
                    onExit = actions.onExitSelection,
                    onToggleAll = {
                        if (state.selectedDeviceIds.size == devices.size) actions.onClearSelection()
                        else actions.onSelectAll()
                    },
                    onDelete = actions.onAskBatchDelete,
                )
            } else {
                ControlHomeTopBar(
                    deviceCount = devices.size.takeIf { state.pageState is DeviceListState.Success },
                    addEnabled = state.pageState !is DeviceListState.Unavailable,
                    onBack = actions.onBack,
                    onAddDevice = actions.onOpenPairDialog,
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            //根据当前设备列表的状态显示同步结果
            when (val pageState = state.pageState) {
                is DeviceListState.Loading -> LoadingView()
                is DeviceListState.Error -> ErrorView(pageState.message, onRetry = actions.onRetry)
                is DeviceListState.Empty -> EmptyView(onAdd = actions.onOpenPairDialog)
                is DeviceListState.Unavailable -> UnavailableView()
                is DeviceListState.Success ->
                    DeviceList(
                        devices = pageState.devices,
                        selectionMode = state.selectionMode,
                        selectedIds = state.selectedDeviceIds,
                        onClick = actions.onRequestControl,
                        onLongPress = actions.onLongPressDevice,
                        onToggleSelect = actions.onToggleDeviceSelection,
                        onEdit = actions.onAskEditDevice,
                        onDelete = actions.onAskDeleteDevice,
                    )
            }
        }
    }
    // 配对码弹窗
    if (state.pairDialogVisible) {
        PairCodeDialog(
            state = state,
            onDismiss = actions.onPairDialogDismiss,
            onCodeChanged = actions.onPairCodeChanged,
            onComplete = actions.onPairCodeComplete,
        )
    }

    // 删除确认
    state.deleteTarget?.let { target ->
        DeleteDeviceConfirmDialog(
            target = target,
            onConfirm = { actions.onDeleteDevice(target) },
            onDismiss = actions.onDismissDeleteDevice,
        )
    }

    // 批量删除确认
    if (state.batchDeleteConfirm) {
        BatchDeleteDevicesDialog(
            count = state.selectedDeviceIds.size,
            onConfirm = actions.onConfirmBatchDelete,
            onDismiss = actions.onDismissBatchDelete,
        )
    }

    // 编辑设备
    state.editTarget?.let { target ->
        EditDeviceDialog(
            target = target,
            name = state.editDeviceName,
            saving = state.editSaving,
            error = state.editError,
            onNameChanged = actions.onEditDeviceNameChanged,
            onConfirm = actions.onConfirmEditDevice,
            onDismiss = actions.onDismissEditDevice,
        )
    }

    // 等待对方同意控制
    state.pendingControl?.let { target ->
        WaitingControlOverlay(
            device = target.device,
            onCancel = actions.onCancelControl,
        )
    }
}

@Composable
private fun ControlHomeTopBar(
    deviceCount: Int?,
    addEnabled: Boolean,
    onBack: () -> Unit,
    onAddDevice: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
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
            if (deviceCount != null) {
                Text(
                    text = "已配对 $deviceCount 台设备",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(
            onClick = onAddDevice,
            enabled = addEnabled,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("添加设备")
        }
    }
}

/** 多选态顶栏: 退出、全选、批量删除 */
@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    totalCount: Int,
    allSelected: Boolean,
    onExit: () -> Unit,
    onToggleAll: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onExit) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "退出多选",
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "已选择 $selectedCount 项",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "共 $totalCount 台设备",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onToggleAll, enabled = totalCount > 0) {
            Text(if (allSelected) "取消全选" else "全选")
        }
        IconButton(onClick = onDelete, enabled = selectedCount > 0) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "删除所选",
                tint = if (selectedCount > 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            )
        }
    }
}

@Composable
internal fun DeleteDeviceConfirmDialog(
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

/** 批量删除确认 */
@Composable
internal fun BatchDeleteDevicesDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除设备") },
        text = {
            Text("确定删除选中的 $count 台设备吗？删除后需要重新配对才能远程控制对方。")
        },
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

/** 编辑设备名称 */
@Composable
internal fun EditDeviceDialog(
    target: PairedDevice,
    name: String,
    saving: Boolean,
    error: String?,
    onNameChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("编辑设备") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("设备名称") },
                    singleLine = true,
                    enabled = !saving,
                    isError = error != null,
                    supportingText = {
                        Text(error ?: "最多 ${ControlHomeViewModel.MAX_DEVICE_NAME_LENGTH} 个字符")
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm() }),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "型号：${target.device.model}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !saving) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") }
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
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
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
        Text(
            "还没有配对设备",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
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
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onClick: (PairedDevice) -> Unit,
    onLongPress: (PairedDevice) -> Unit,
    onToggleSelect: (PairedDevice) -> Unit,
    onEdit: (PairedDevice) -> Unit,
    onDelete: (PairedDevice) -> Unit,
) {
    // 同一时间只展开一行的滑动操作
    var openRowId by remember { mutableStateOf<String?>(null) }
    // 进入多选 / 列表变化时收起所有滑动操作
    LaunchedEffect(selectionMode, devices) {
        if (selectionMode) openRowId = null
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(devices, key = { it.device.id }) { device ->
            SwipeRevealDeviceCard(
                device = device,
                selectionMode = selectionMode,
                selected = device.device.id in selectedIds,
                expanded = openRowId == device.device.id,
                onExpand = { openRowId = device.device.id },
                onCollapse = {
                    if (openRowId == device.device.id) openRowId = null
                },
                onClick = {
                    if (selectionMode) onToggleSelect(device) else onClick(device)
                },
                onLongPress = {
                    if (selectionMode) onToggleSelect(device) else onLongPress(device)
                },
                onEdit = {
                    openRowId = null
                    onEdit(device)
                },
                onDelete = {
                    openRowId = null
                    onDelete(device)
                },
            )
        }
    }
}

/**
 * 左滑唤出「编辑 / 删除」操作，长按进入多选。
 *
 * 注意: offset 必须排在 background 之前，否则背景不随内容一起位移、
 * 会把滑出的操作按钮整片盖住（表现为「能滑动但看不到删除」）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeRevealDeviceCard(
    device: PairedDevice,
    selectionMode: Boolean,
    selected: Boolean,
    expanded: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val actionWidth = 76.dp
    val actionsWidth = actionWidth * 2
    val revealPx = with(LocalDensity.current) { actionsWidth.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }

    // 其他行展开、进入多选或退出多选时同步收起
    LaunchedEffect(expanded, selectionMode) {
        if (selectionMode) dragging = false
        if (dragging) return@LaunchedEffect
        val target = if (expanded && !selectionMode) -revealPx else 0f
        if (offsetX.value != target) offsetX.animateTo(target, spring())
    }

    val rowHeight = 84.dp
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(rowHeight)
            .clip(RoundedCornerShape(20.dp)),
    ) {
        // 左滑唤出的操作按钮(位于前景卡片下方)
        if (!selectionMode) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(actionsWidth),
            ) {
                SwipeActionButton(
                    label = "编辑",
                    icon = Icons.Filled.Edit,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onCollapse()
                        scope.launch { offsetX.animateTo(0f, spring()) }
                        onEdit()
                    },
                )
                SwipeActionButton(
                    label = "删除",
                    icon = Icons.Filled.Delete,
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onCollapse()
                        scope.launch { offsetX.animateTo(0f, spring()) }
                        onDelete()
                    },
                )
            }
        }

        // 前景卡片
        Row(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surface
                )
                .then(
                    if (selectionMode) Modifier
                    else Modifier.pointerInput(device.device.id, revealPx) {
                        detectHorizontalDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = {
                                dragging = false
                                scope.launch {
                                    if (offsetX.value <= -revealPx / 2f) {
                                        onExpand()
                                        offsetX.animateTo(-revealPx, spring())
                                    } else {
                                        onCollapse()
                                        offsetX.animateTo(0f, spring())
                                    }
                                }
                            },
                            onDragCancel = {
                                dragging = false
                                onCollapse()
                                scope.launch { offsetX.animateTo(0f, spring()) }
                            },
                        ) { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                offsetX.snapTo(
                                    (offsetX.value + dragAmount).coerceIn(-revealPx, 0f)
                                )
                            }
                        }
                    }
                )
                .combinedClickable(
                    onClick = {
                        // 已经滑出操作按钮时，点击只收起，避免误触进入控制
                        if (offsetX.value < -1f) {
                            onCollapse()
                            scope.launch { offsetX.animateTo(0f, spring()) }
                        } else {
                            onClick()
                        }
                    },
                    onLongClick = {
                        if (offsetX.value >= -1f) onLongPress()
                    },
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 多选态左侧单选框
            AnimatedVisibility(
                visible = selectionMode,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = null,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            DeviceRowContent(
                device = device,
                showMenu = !selectionMode,
                onEdit = onEdit,
                onDelete = onDelete,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SwipeActionButton(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(containerColor)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, color = contentColor, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DeviceRowContent(
    device: PairedDevice,
    showMenu: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showMenu) {
            DeviceRowMenu(onEdit = onEdit, onDelete = onDelete)
        }
    }
}

/** 行尾「更多」菜单: 编辑 / 删除；让编辑入口不必只依赖左滑发现 */
@Composable
private fun DeviceRowMenu(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "更多操作",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("编辑设备") },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text("删除设备", color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    expanded = false
                    onDelete()
                },
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
            Text(
                "正在请求控制",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
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

@Composable
private fun UnavailableView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.PhoneAndroid,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("服务端暂未支持", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "设备绑定、配对和远程控制功能尚未开放",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
