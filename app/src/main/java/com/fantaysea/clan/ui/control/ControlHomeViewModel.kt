package com.fantaysea.clan.ui.control

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantaysea.clan.data.model.Device
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
    /** 正在等待对方同意的控制请求目标 */
    val pendingControl: PairedDevice? = null,
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
enum class PairPhase { Input, Validating, Error, Waiting, Success }

/** 控制端导航事件(仅用于会话进入, 其余用状态驱动) */
sealed interface ControlNavEvent {
    data class EnterSession(val peer: Device) : ControlNavEvent
}

class ControlHomeViewModel(
    private val repository: ClanRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ControlHomeUiState())
    val uiState: StateFlow<ControlHomeUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val handled = mutableSetOf<String>()

    init {
        //判断当前后端是否支持设备绑定，支持则获取设备列表
        if (BackendCapability.DEVICE_BINDINGS in repository.capabilities) {
            viewModelScope.launch { refresh() }
        } else {
            //当前后端若不支持设备绑定，更新为不可用状态
            _uiState.update { it.copy(pageState = DeviceListState.Unavailable) }
        }
        viewModelScope.launch { observeEvents() }
    }

    private suspend fun refresh() {
        //当前后端若不支持设备绑定，更新为不可用状态
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

    fun retry() = viewModelScope.launch { refresh() }

    private suspend fun observeEvents() {
        repository.events.collect { event ->
            when (event) {
                is RemoteEvent.MatchResult -> handleMatchResult(event)
                is RemoteEvent.PairingSucceeded -> handlePairingSucceeded(event)
                is RemoteEvent.RealtimeReady -> refresh()
                is RemoteEvent.DevicePresence -> applyPresence(event)
                is RemoteEvent.ControlResult -> handleControlResult(event)
                is RemoteEvent.SessionStarted -> handleSessionStarted(event)
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

    private fun handleMatchResult(event: RemoteEvent.MatchResult) {
        val pending = _uiState.value.pendingPairRequestId ?: return
        if (pending != event.requestId) return

        if (event.accepted) {
            // 后端的 PAIRING_RESULT(ACCEPTED) 不带设备，随后会到 PAIRING_SUCCEEDED。
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
            else viewModelScope.launch { refresh() }
            return
        }
        completePairing()
    }

    private fun completePairing() {
        _uiState.update { it.copy(pairPhase = PairPhase.Success, pendingPairRequestId = null) }
        viewModelScope.launch { refresh() }
        viewModelScope.launch {
            delay(1200.milliseconds)
            _uiState.update {
                if (it.pairPhase == PairPhase.Success) it.copy(pairDialogVisible = false) else it
            }
        }
    }

    private fun handleControlResult(event: RemoteEvent.ControlResult) {
        if (!handled.add(event.requestId)) return
        if (!event.accepted) {
            _uiState.update { it.copy(pendingControl = null) }
            _messages.tryEmit("对方拒绝了控制请求")
        }
    }

    private fun handleSessionStarted(event: RemoteEvent.SessionStarted) {
        if (!handled.add(event.requestId)) return
        val selfId = repository.self.value?.id ?: return
        // 仅当本机是控制方且正在等待对方同意时才进入控制页
        if (event.controller.id == selfId && _uiState.value.pendingControl != null) {
            _uiState.update {
                it.copy(pendingControl = null, controlSessionPeer = event.controlled)
            }
        }
    }

    // ── 配对弹窗 ─────────────────────────────────────────────
    fun openPairDialog() {
        if (BackendCapability.PAIRING !in repository.capabilities) {
            _messages.tryEmit("服务端暂未支持")
            return
        }
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

    fun onCodeChanged(code: String) {
        _uiState.update { it.copy(pairCode = code) }
    }

    fun submitPairCode(code: String) {
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

    private fun startWaitingTimeout() {
        viewModelScope.launch {
            delay(60_000.milliseconds)
            var timedOut = false
            _uiState.update {
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
                refresh()
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

    // ── 设备列表操作 ─────────────────────────────────────────

    /** 长按设备项: 进入多选并选中该设备 */
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
            refresh()
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

    /** 保存新的设备名称(写回服务端) */
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
                    refresh()
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
                    refresh()
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
                repository.sendControlRequest(device.device.id)
                _uiState.update { it.copy(pendingControl = device) }
            } catch (e: DeviceOfflineException) {
                _messages.tryEmit(e.message ?: "设备不在线")
            } catch (e: Exception) {
                _messages.tryEmit(e.message ?: "发起控制失败")
            }
        }
    }

    fun cancelControl() {
        _uiState.update { it.copy(pendingControl = null) }
    }

    fun consumeSessionPeer() {
        _uiState.update { it.copy(controlSessionPeer = null) }
    }

    companion object {
        /** 与服务端 deviceName 字段校验上限(@Size max = 128)一致 */
        const val MAX_DEVICE_NAME_LENGTH = 128
    }
}
