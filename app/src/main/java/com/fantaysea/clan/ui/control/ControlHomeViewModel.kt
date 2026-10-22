package com.fantaysea.clan.ui.control

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
data class ControlHomeUiState(
    val pageState: DeviceListState = DeviceListState.Loading,
    val pairDialogVisible: Boolean = false,
    val pairPhase: PairPhase = PairPhase.Input,
    val pairCode: String = "",
    val pairError: String? = null,
    /** 递增以触发输入框重建/重新聚焦 */
    val resetNonce: Int = 0,
    /** 正在等待对方同意的控制请求目标 */
    val pendingControl: PairedDevice? = null,
    /** 会话建立后, 需要进入控制页的被控设备(非空即导航) */
    val controlSessionPeer: Device? = null,
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
        if (BackendCapability.DEVICE_BINDINGS in repository.capabilities) {
            viewModelScope.launch { refresh() }
        } else {
            _uiState.update { it.copy(pageState = DeviceListState.Unavailable) }
        }
        viewModelScope.launch { observeEvents() }
    }

    private suspend fun refresh() {
        if (BackendCapability.DEVICE_BINDINGS !in repository.capabilities) {
            _uiState.update { it.copy(pageState = DeviceListState.Unavailable) }
            return
        }
        _uiState.update {
            it.copy(pageState = DeviceListState.Loading)
        }
        runCatching {
            repository.fetchDevices()
        }.onSuccess { devices ->
            _uiState.update {
                it.copy(
                    pageState = if (devices.isEmpty()) {
                        DeviceListState.Empty
                    } else {
                        DeviceListState.Success(devices)
                    }
                )
            }

        }.onFailure { e ->
            _uiState.update {
                it.copy(
                    pageState = DeviceListState.Error(
                        message = e.message ?: "未知错误"
                    )
                )
            }
        }
    }

    fun retry() = viewModelScope.launch { refresh() }

    private suspend fun observeEvents() {
        repository.events.collect { event ->
            when (event) {
                is RemoteEvent.MatchResult -> handleMatchResult(event)
                is RemoteEvent.ControlResult -> handleControlResult(event)
                is RemoteEvent.SessionStarted -> handleSessionStarted(event)
                else -> Unit
            }
        }
    }

    private fun handleMatchResult(event: RemoteEvent.MatchResult) {
        if (!handled.add(event.requestId)) return
        if (event.accepted && event.pairedDevice != null) {
            _uiState.update { it.copy(pairPhase = PairPhase.Success) }
            viewModelScope.launch { refresh() }
            viewModelScope.launch {
                delay(1200.milliseconds)
                _uiState.update {
                    if (it.pairPhase == PairPhase.Success) it.copy(pairDialogVisible = false) else it
                }
            }
        } else {
            toError("对方拒绝了配对")
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
                resetNonce = it.resetNonce + 1,
            )
        }
    }
    fun closePairDialog() {
        _uiState.update {
            it.copy(
                pairDialogVisible = false,
                pairPhase = PairPhase.Input,
                pairCode = "",
                pairError = null
            )
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
                repository.submitPairingCode(code)
                _uiState.update { it.copy(pairPhase = PairPhase.Waiting) }
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
            _uiState.update {
                if (it.pairPhase == PairPhase.Waiting) {
                    it.copy(
                        pairPhase = PairPhase.Error,
                        pairError = "配对超时，请重试",
                        pairCode = ""
                    )
                } else it
            }
            toErrorReset()
        }
    }

    private fun toError(message: String) {
        _uiState.update {
            it.copy(pairPhase = PairPhase.Error, pairError = message, pairCode = "")
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

    fun deleteDevice(device: PairedDevice) {
        viewModelScope.launch {
            runCatching { repository.unbindDevice(device.device.id) }
                .onSuccess { refresh() }
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
}
