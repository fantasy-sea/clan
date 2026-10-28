package com.fantaysea.clan.ui.control

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.data.model.SessionRole
import com.fantaysea.clan.data.model.SessionStatus
import com.fantaysea.clan.data.repository.ClanRepository
import com.fantaysea.clan.data.repository.BackendCapability
import com.fantaysea.clan.data.repository.DeviceOfflineException
import com.fantaysea.clan.data.repository.ExpiredPairingCodeException
import com.fantaysea.clan.data.repository.InvalidPairingCodeException
import com.fantaysea.clan.data.repository.PairedDevice
import com.fantaysea.clan.data.repository.RemoteEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

//
@Immutable
data class ControlHomeUiState(
    val pageState: DeviceListState = DeviceListState.Loading,
    val pairDialogVisible: Boolean = false,
    val pairPhase: PairPhase = PairPhase.Input,
    val pairCode: String = "",
    val pairError: String? = null,
    /** 当前等待应答的配对请求；用于把 WS 结果关联回本次操作 */
    val pendingPairRequestId: String? = null,
    /** 递增以触发输入框重建/重新聚焦 */
    val resetNonce: Int = 0,
    /** 正在等待对方同意的控制请求目标与会话 ID */
    val pendingControl: PendingControl? = null,
    /** 会话建立后, 需要进入控制页的被控设备(非空即导航) */
    val controlSessionPeer: Device? = null,
    /** 待确认移除的已配对设备(非空即弹确认框) */
    val deleteTarget: PairedDevice? = null,
    /** 多选模式: 长按列表项进入, 列表左侧显示单选框 */
    val selectionMode: Boolean = false,
    /** 多选模式下已勾选的设备 id */
    val selectedDeviceIds: Set<String> = emptySet(),
    /** 是否展示「批量删除」确认框 */
    val batchDeleteConfirm: Boolean = false,
    /** 正在编辑的已配对设备(非空即弹编辑框) */
    val editTarget: PairedDevice? = null,
    /** 编辑框中的设备名称草稿 */
    val editDeviceName: String = "",
    /** 编辑保存中(禁用输入与按钮) */
    val editSaving: Boolean = false,
    /** 编辑失败提示 */
    val editError: String? = null,
)

/** 等待对端应答的控制请求；sessionId 用于结束/关联服务端事件。 */
data class PendingControl(
    val device: PairedDevice,
    val sessionId: String,
)

sealed interface DeviceListState {
    data object Loading : DeviceListState
    data class Error(
        val message: String
    ) : DeviceListState

    data object Empty : DeviceListState
    data object Unavailable : DeviceListState
    data class Success(
        val devices: List<PairedDevice>
    ) : DeviceListState
}

/** 配对弹窗阶段 */
enum class PairPhase {
    /**
     * 弹窗 输入阶段
     */
    Input,
    /** 弹窗 验证阶段 */
    Validating,
    Error,
    /**
     * 弹窗等待阶段
     */
    Waiting,
    Success
}
class ControlHomeViewModel(
    private val repository: ClanRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ControlHomeUiState())
    val uiState: StateFlow<ControlHomeUiState> = _uiState.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /**
     *  handled 用来对
     *      WebSocket 推送的 RemoteEvent 做去重（幂等处
     *      理），防止同一个事件被重复处理
     *      每次处理事件时，都会用 handled.add(key) 尝试加
     *      入一个标记。如果该 key 已经存在，add() 会返回
     *      false，此时直接 return 跳过后续逻辑，从而只处理
     *      该事件的第一次。
     */
    private val handled = mutableSetOf<String>()

    init {
        // 能力判断同步完成，页面首帧即可展示“不可用”，也避免无谓请求。
        if (BackendCapability.DEVICE_BINDINGS in repository.capabilities) {
            viewModelScope.launch { refreshDeviceList() }
        } else {
            _uiState.update { it.copy(pageState = DeviceListState.Unavailable) }
        }
        viewModelScope.launch {
            observeEvents()
        }
        // 服务端把会话推进到 CONNECTING/CONNECTED 后进入控制页。
        // 以仓库的 activeSession 为准，避免 SESSION_STARTED 事件与 REST 响应竞态时卡在等待框。
        viewModelScope.launch {
            repository.activeSession.collect { active ->
                val pending = _uiState.value.pendingControl ?: return@collect
                if (active != null &&
                    active.role == SessionRole.CONTROLLER &&
                    active.info.sessionId == pending.sessionId &&
                    (active.info.status == SessionStatus.CONNECTING || active.info.status == SessionStatus.CONNECTED) &&
                    handled.add("session-started-${pending.sessionId}")
                ) {
                    _uiState.update {
                        it.copy(pendingControl = null, controlSessionPeer = pending.device.device)
                    }
                }
            }
        }
    }
    /**
     * 刷新设备列表
     */
    private suspend fun refreshDeviceList() {
        // 服务端未开放设备绑定时不请求列表，直接展示不可用状态。
        if (BackendCapability.DEVICE_BINDINGS !in repository.capabilities) {
            _uiState.update { it.copy(pageState = DeviceListState.Unavailable) }
            return
        }
        _uiState.update { it.copy(pageState = DeviceListState.Loading) }
        // runCatching 可以处理异常 若没有异常则可以进一步链式处理
        runCatching {
            repository.fetchDevices()
        }.onSuccess { devices ->
            _uiState.update { state ->
                // 列表刷新后剔除已不存在的选中项, 选空则自动退出多选
                val existingIds = devices.map { it.device.id }.toSet()
                val selected = state.selectedDeviceIds intersect existingIds
                //返回的设备列表若为空则更新为空状态
                state.copy(
                    pageState = if (devices.isEmpty()) DeviceListState.Empty
                    else DeviceListState.Success(devices),
                    selectedDeviceIds = selected,
                    selectionMode = state.selectionMode && selected.isNotEmpty(),
                )
            }
        }.onFailure { e ->
            _uiState.update {
                it.copy(
                    pageState = DeviceListState.Error(
                        message = e.message ?: "未知错误"
                    ),
                    selectionMode = false,
                    selectedDeviceIds = emptySet(),
                )
            }
        }
    }

    //观察服务端推送的事件
    private suspend fun observeEvents() {
        repository.events.collect { event ->
            when (event) {
                is RemoteEvent.MatchResult -> handleMatchResult(event)
                is RemoteEvent.PairingSucceeded -> handlePairingSucceeded(event)
                is RemoteEvent.RealtimeReady -> refreshDeviceList()
                is RemoteEvent.DevicePresence -> applyPresence(event)
                is RemoteEvent.ControlResult -> handleControlResult(event)
                is RemoteEvent.SessionEnded -> handleSessionEnded(event)
                is RemoteEvent.SessionError -> handleSessionError(event)
                else -> Unit
            }
        }
    }

    /**
     * 服务端上下线推送到达时就地更新列表，避免重新拉取整张表造成 Loading 闪烁。
     * 列表尚未加载成功时忽略：此时已有 refresh() 在路上的权威结果。
     */
    private fun applyPresence(event: RemoteEvent.DevicePresence) {
        _uiState.update { state ->
            val page = state.pageState as? DeviceListState.Success ?: return@update state
            val updated = page.devices.map { paired ->
                if (paired.device.id != event.deviceId) paired
                else paired.copy(
                    online = event.online,
                    device = paired.device.copy(
                        lastOnlineAt = event.lastSeenAt ?: paired.device.lastOnlineAt,
                    ),
                )
            }
            if (updated == page.devices) state
            else state.copy(pageState = DeviceListState.Success(updated))
        }
    }

    /*
    处理匹配结果
     */
    private fun handleMatchResult(event: RemoteEvent.MatchResult) {
        val pending = _uiState.value.pendingPairRequestId ?: return
        if (pending != event.requestId) return

        if (event.accepted) {
            // 判断设备是否存在 并且这个事件还没有被处理
            if (event.pairedDevice != null && handled.add("result-${event.requestId}")) {
                completePairing()
            }
            return
        }

        if (!handled.add("result-${event.requestId}")) return
        toError(if (event.expired) "配对请求已过期，请重试" else "对方拒绝了配对")
    }

    private fun handlePairingSucceeded(event: RemoteEvent.PairingSucceeded) {
        if (!handled.add("paired-${event.requestId}")) return
        if (_uiState.value.pairPhase == PairPhase.Success) return

        val pending = _uiState.value.pendingPairRequestId
        if (pending != null && pending != event.requestId) return
        if (pending == null) {
            // 等待中但未记录到 requestId（极端竞态）也应完成；取消后才到的事件只刷新列表。
            if (_uiState.value.pairPhase == PairPhase.Waiting) completePairing()
            else viewModelScope.launch { refreshDeviceList() }
            return
        }
        completePairing()
    }

    /**
     * 完成设备配对
     */
    private fun completePairing() {
        //修改配对弹窗状态为成功
        _uiState.update { it.copy(pairPhase = PairPhase.Success, pendingPairRequestId = null) }
        viewModelScope.launch { refreshDeviceList() }
        viewModelScope.launch {
            // 为了展示成功样式等待一段时间
            delay(1200.milliseconds)
            _uiState.update {
                //判断配对弹窗状态是否为成功，成功之后关闭弹窗
                if (it.pairPhase == PairPhase.Success) it.copy(pairDialogVisible = false) else it
            }
        }
    }

    private fun handleControlResult(event: RemoteEvent.ControlResult) {
        val pending = _uiState.value.pendingControl ?: return
        if (pending.sessionId != event.sessionId) return
        if (event.result == RemoteEvent.SessionRequestResult.ACCEPTED) return
        if (!handled.add("control-result-${event.sessionId}-${event.result}")) return
        _uiState.update { it.copy(pendingControl = null) }
        val message = when (event.result) {
            RemoteEvent.SessionRequestResult.EXPIRED -> "控制请求已过期，请重试"
            RemoteEvent.SessionRequestResult.REJECTED -> "对方拒绝了控制请求"
            RemoteEvent.SessionRequestResult.FAILED -> "控制请求失败，请重试"
            RemoteEvent.SessionRequestResult.ACCEPTED -> null
        }
        message?.let { _messages.tryEmit(it) }
    }

    private fun handleSessionEnded(event: RemoteEvent.SessionEnded) {
        val pending = _uiState.value.pendingControl ?: return
        if (pending.sessionId != event.sessionId) return
        _uiState.update { it.copy(pendingControl = null) }
        _messages.tryEmit(if (event.failed) "会话建立失败，请重试" else "会话已结束")
    }

    private fun handleSessionError(event: RemoteEvent.SessionError) {
        val pending = _uiState.value.pendingControl ?: return
        if (event.sessionId != pending.sessionId) return
        _uiState.update { it.copy(pendingControl = null) }
        _messages.tryEmit(event.message)
    }

    /**
     * 等待超时判断
     */
    private fun startWaitingTimeout() {
        viewModelScope.launch {
//            等待一分钟
            delay(60_000.milliseconds)
            var timedOut = false
            _uiState.update {
                //如果状态还在等待判为超时，其他状态则不更新
                if (it.pairPhase == PairPhase.Waiting) {
                    timedOut = true
                    it.copy(
                        pairPhase = PairPhase.Error,
                        pairError = "配对超时，请重试",
                        pairCode = "",
                        pendingPairRequestId = null,
                    )
                } else it
            }
            if (timedOut) {
                // 迟到的 PAIRING_SUCCEEDED 可能已建绑，刷新列表对账。
                refreshDeviceList()
                toErrorReset()
            }
        }
    }

    private fun toError(message: String) {
        _uiState.update {
            it.copy(
                pairPhase = PairPhase.Error,
                pairError = message,
                pairCode = "",
                pendingPairRequestId = null,
            )
        }
        toErrorReset()
    }

    /**
     * 错误重试，若配对弹窗出现错误则让其重新输入
     */
    private fun toErrorReset() {
        viewModelScope.launch {
            delay(1200.milliseconds)
            _uiState.update {
                if (it.pairPhase == PairPhase.Error) {
                    it.copy(
                        pairPhase = PairPhase.Input,
                        pairCode = "",
                        pairError = null,
                        resetNonce = it.resetNonce + 1,
                    )
                } else it
            }
        }
    }

    /**
     * ui 重新获取设备列表
     */
    fun retry() = viewModelScope.launch { refreshDeviceList() }
    // ── 配对弹窗 ─────────────────────────────────────────────
    /**
    ui 打开配对弹窗
     */
    fun openPairDialog() {
        _uiState.update {
            it.copy(
                pairDialogVisible = true,
                pairPhase = PairPhase.Input,
                pairCode = "",
                pairError = null,
                pendingPairRequestId = null,
                resetNonce = it.resetNonce + 1,
            )
        }
    }

    /**
    ui 关闭配对弹窗
     */
    fun closePairDialog() {
        val requestId = _uiState.value.pendingPairRequestId
        val wasWaiting = _uiState.value.pairPhase == PairPhase.Waiting
        _uiState.update {
            it.copy(
                pairDialogVisible = false,
                pairPhase = PairPhase.Input,
                pairCode = "",
                pairError = null,
                pendingPairRequestId = null,
            )
        }
        if (wasWaiting && requestId != null) {
            // 取消是尽力而为：即使本地已超时或请求已被处理，也只需关闭本端弹窗。
            viewModelScope.launch { runCatching { repository.cancelPairingRequest(requestId) } }
        }
    }

    /**
    ui 更新配对码
     */
    fun onCodeChanged(code: String) {
        _uiState.update { it.copy(pairCode = code) }
    }

    /**
     * ui 提交配对码
     */
    fun submitPairCode(code: String) {
        //过滤没有完整输入的匹配码
        if (!PairCodeInput.isComplete(code)) return

        viewModelScope.launch {
            _uiState.update { it.copy(pairPhase = PairPhase.Validating, pairError = null) }
            try {
                val requestId = repository.submitPairingCode(code)
                _uiState.update {
                    it.copy(pairPhase = PairPhase.Waiting, pendingPairRequestId = requestId)
                }
                startWaitingTimeout()
            } catch (e: InvalidPairingCodeException) {
                toError(e.message ?: "配对码错误，请重新输入")
            } catch (e: ExpiredPairingCodeException) {
                toError(e.message ?: "配对码已过期，请重新获取")
            } catch (e: Exception) {
                toError(e.message ?: "配对失败，请重试")
            }
        }
    }



    // ── 设备列表操作 ─────────────────────────────────────────
    /** ui 长按设备项: 进入多选并选中该设备 */
    fun enterSelection(device: PairedDevice) {
        _uiState.update {
            it.copy(
                selectionMode = true,
                selectedDeviceIds = it.selectedDeviceIds + device.device.id,
            )
        }
    }

    /** 多选模式下点击设备项: 切换勾选 */
    fun toggleSelection(device: PairedDevice) {
        _uiState.update { state ->
            val next = if (device.device.id in state.selectedDeviceIds) {
                state.selectedDeviceIds - device.device.id
            } else {
                state.selectedDeviceIds + device.device.id
            }
            // 取消最后一个勾选即退出多选
            state.copy(
                selectedDeviceIds = next,
                selectionMode = next.isNotEmpty(),
            )
        }
    }

    fun selectAllDevices() {
        _uiState.update { state ->
            val ids = (state.pageState as? DeviceListState.Success)
                ?.devices
                ?.map { it.device.id }
                ?.toSet()
                .orEmpty()
            if (ids.isEmpty()) state
            else state.copy(selectionMode = true, selectedDeviceIds = ids)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectionMode = false, selectedDeviceIds = emptySet()) }
    }

    /** 退出多选 */
    fun exitSelectionMode() = clearSelection()

    /** 请求批量删除: 打开确认弹窗 */
    fun askBatchDeleteDevices() {
        if (_uiState.value.selectedDeviceIds.isEmpty()) return
        _uiState.update { it.copy(batchDeleteConfirm = true) }
    }

    fun dismissBatchDelete() {
        _uiState.update { it.copy(batchDeleteConfirm = false) }
    }

    /** 批量删除已勾选设备: 逐个解绑, 全部结束后刷新列表 */
    fun deleteSelectedDevices() {
        val ids = _uiState.value.selectedDeviceIds.toList()
        if (ids.isEmpty()) return
        _uiState.update { it.copy(batchDeleteConfirm = false) }
        viewModelScope.launch {
            var failed = 0
            ids.forEach { id ->
                runCatching { repository.unbindDevice(id) }.onFailure { failed++ }
            }
            _uiState.update { it.copy(selectionMode = false, selectedDeviceIds = emptySet()) }
            val deleted = ids.size - failed
            _messages.tryEmit(
                when {
                    failed == 0 -> "已删除 $deleted 台设备"
                    deleted == 0 -> "删除失败，请重试"
                    else -> "已删除 $deleted 台，${failed} 台删除失败"
                }
            )
            refreshDeviceList()
        }
    }

    // ── 设备编辑 ─────────────────────────────────────────────

    /** 打开编辑设备名称弹窗 */
    fun askEditDevice(device: PairedDevice) {
        _uiState.update {
            it.copy(
                editTarget = device,
                editDeviceName = device.device.deviceName,
                editSaving = false,
                editError = null,
            )
        }
    }

    fun onEditDeviceNameChanged(value: String) {
        _uiState.update { it.copy(editDeviceName = value, editError = null) }
    }

    fun dismissEditDevice() {
        if (_uiState.value.editSaving) return
        _uiState.update { it.copy(editTarget = null, editDeviceName = "", editError = null) }
    }

    /** 保存新的设备名称 */
    fun confirmEditDevice() {
        val state = _uiState.value
        val target = state.editTarget ?: return
        if (state.editSaving) return

        val name = state.editDeviceName.trim()
        val error = when {
            name.isEmpty() -> "设备名称不能为空"
            name.length > MAX_DEVICE_NAME_LENGTH -> "设备名称最多 $MAX_DEVICE_NAME_LENGTH 个字符"
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(editError = error) }
            return
        }
        if (name == target.device.deviceName) {
            dismissEditDevice()
            return
        }

        _uiState.update { it.copy(editSaving = true, editError = null) }
        viewModelScope.launch {
            runCatching { repository.updateDeviceName(target.device.id, name) }
                .onSuccess {
                    _uiState.update {
                        it.copy(editSaving = false, editTarget = null, editDeviceName = "")
                    }
                    _messages.tryEmit("设备名称已更新")
                    refreshDeviceList()
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(editSaving = false, editError = e.message ?: "保存失败，请重试")
                    }
                }
        }
    }

    /** 请求移除设备: 打开确认弹窗 */
    fun askDeleteDevice(device: PairedDevice) {
        _uiState.update { it.copy(deleteTarget = device) }
    }

    /** 取消移除: 关闭确认弹窗 */
    fun dismissDeleteDevice() {
        _uiState.update { it.copy(deleteTarget = null) }
    }

    /**
     * 移除设备
     */
    fun deleteDevice(device: PairedDevice) {
        // 确认即关闭弹窗, 失败时仅提示不恢复弹窗
        _uiState.update {
            it.copy(
                deleteTarget = null,
                selectedDeviceIds = it.selectedDeviceIds - device.device.id,
                selectionMode = it.selectionMode && (it.selectedDeviceIds - device.device.id).isNotEmpty(),
            )
        }
        viewModelScope.launch {
            runCatching { repository.unbindDevice(device.device.id) }
                .onSuccess {
                    _messages.tryEmit("已删除「${device.device.deviceName}」")
                    refreshDeviceList()
                }
                .onFailure { _messages.tryEmit("删除失败，请重试") }
        }
    }

    fun requestControl(device: PairedDevice) {
        if (!device.online) {
            _messages.tryEmit("设备不在线，无法发起控制")
            return
        }
        viewModelScope.launch {
            try {
                val session = repository.sendControlRequest(device.device)
                _uiState.update {
                    it.copy(pendingControl = PendingControl(device, session.sessionId))
                }
                // 极端竞态兜底：REST 返回前对端已接受，activeSession 已推进到 CONNECTING。
                val active = repository.activeSession.value
                if (active != null &&
                    active.role == SessionRole.CONTROLLER &&
                    active.info.sessionId == session.sessionId &&
                    (active.info.status == SessionStatus.CONNECTING || active.info.status == SessionStatus.CONNECTED) &&
                    handled.add("session-started-${session.sessionId}")
                ) {
                    _uiState.update {
                        it.copy(pendingControl = null, controlSessionPeer = device.device)
                    }
                }
            } catch (e: DeviceOfflineException) {
                _messages.tryEmit(e.message ?: "设备不在线")
            } catch (e: Exception) {
                _messages.tryEmit(e.message ?: "发起控制失败")
            }
        }
    }

    fun cancelControl() {
        val pending = _uiState.value.pendingControl ?: return
        _uiState.update { it.copy(pendingControl = null) }
        // 服务端 /end 接受 REQUESTING 状态；取消后对方收到 SESSION_ENDED。
        viewModelScope.launch { runCatching { repository.endSession(pending.sessionId) } }
    }

    fun consumeSessionPeer() {
        _uiState.update { it.copy(controlSessionPeer = null) }
    }

    companion object {
        /** 与服务端 deviceName 字段校验上限(@Size max = 128)一致 */
        const val MAX_DEVICE_NAME_LENGTH = 128
    }
}
