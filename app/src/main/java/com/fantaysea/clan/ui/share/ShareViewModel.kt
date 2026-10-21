package com.fantaysea.clan.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantaysea.clan.data.PairingCodeGenerator
import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.data.repository.ClanRepository
import com.fantaysea.clan.data.repository.BackendCapability
import com.fantaysea.clan.data.repository.RemoteEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 屏幕共享授权状态 */
enum class SharePermissionState { Unknown, Granted, Denied }

data class ShareUiState(
    val permissionState: SharePermissionState = SharePermissionState.Unknown,
    val deviceName: String = "",
    val model: String = "",
    val platform: String = "",
    val deviceIdShort: String = "",
    val pairingAvailable: Boolean = true,
    val code: String = "",
    val expiresAt: Long = 0L,
    val remainingMillis: Long = 0L,
    val generating: Boolean = false,
    val pendingMatch: RemoteEvent.MatchRequest? = null,
    val pendingControl: RemoteEvent.ControlRequest? = null,
    /** 会话建立后, 需要进入被控页的控制方(非空即导航) */
    val controlledSessionPeer: Device? = null,
)

class ShareViewModel(
    private val repository: ClanRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ShareUiState(pairingAvailable = BackendCapability.PAIRING in repository.capabilities),
    )
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val handled = mutableSetOf<String>()
    private var codeJob: Job? = null

    init {
        viewModelScope.launch {
            repository.self.filterNotNull().collect { self ->
                _uiState.update {
                    it.copy(
                        deviceName = self.deviceName,
                        model = self.model,
                        platform = self.platform,
                        deviceIdShort = self.id.takeLast(8),
                    )
                }
            }
        }
        viewModelScope.launch { observeEvents() }
    }

    /** 屏幕共享授权结果(授权或拒绝) */
    fun onPermissionResult(granted: Boolean) {
        if (!_uiState.value.pairingAvailable) return
        _uiState.update {
            it.copy(permissionState = if (granted) SharePermissionState.Granted else SharePermissionState.Denied)
        }
        if (granted) startPairingCodeSession() else stopPairingCodeSession()
    }

    private fun startPairingCodeSession() {
        codeJob?.cancel()
        codeJob = viewModelScope.launch {
            refreshPairingCode()
            while (isActive) {
                val remaining = PairingCodeGenerator.remainingMillis(_uiState.value.expiresAt)
                if (remaining <= 0) {
                    refreshPairingCode()
                } else {
                    _uiState.update { it.copy(remainingMillis = remaining) }
                    delay(200)
                }
            }
        }
    }

    private fun stopPairingCodeSession() {
        codeJob?.cancel()
        codeJob = null
        _uiState.update { it.copy(code = "", remainingMillis = 0L) }
    }

    private suspend fun refreshPairingCode() {
        _uiState.update { it.copy(generating = true) }
        runCatching { repository.createPairingCode() }
            .onSuccess { pc ->
                _uiState.update {
                    it.copy(
                        code = pc.code,
                        expiresAt = pc.expiresAt,
                        remainingMillis = PairingCodeGenerator.VALIDITY_MS,
                        generating = false,
                    )
                }
            }
            .onFailure {
                _uiState.update { it.copy(generating = false) }
                _messages.tryEmit("生成配对码失败，请重试")
            }
    }

    private suspend fun observeEvents() {
        repository.events.collect { event ->
            when (event) {
                is RemoteEvent.MatchRequest -> handleMatchRequest(event)
                is RemoteEvent.PairingSucceeded -> handlePairingSucceeded(event)
                is RemoteEvent.ControlRequest -> handleControlRequest(event)
                is RemoteEvent.SessionStarted -> handleSessionStarted(event)
                else -> Unit
            }
        }
    }

    private fun handleMatchRequest(event: RemoteEvent.MatchRequest) {
        if (!handled.add(event.requestId)) return
        if (_uiState.value.pendingMatch == null) {
            _uiState.update { it.copy(pendingMatch = event) }
        }
    }

    private fun handlePairingSucceeded(event: RemoteEvent.PairingSucceeded) {
        if (!handled.add(event.requestId)) return
        _messages.tryEmit("配对成功，设备已添加")
    }

    private fun handleControlRequest(event: RemoteEvent.ControlRequest) {
        if (!handled.add(event.requestId)) return
        if (_uiState.value.pendingControl == null) {
            _uiState.update { it.copy(pendingControl = event) }
        }
    }

    private fun handleSessionStarted(event: RemoteEvent.SessionStarted) {
        if (!handled.add(event.requestId)) return
        // 本机作为被控端(由共享屏幕页代表被控)且正在等待对方确认 → 进入被控页
        if (_uiState.value.pendingControl != null) {
            _uiState.update {
                it.copy(
                    pendingControl = null,
                    controlledSessionPeer = event.controller,
                )
            }
        }
    }

    // ── 用户操作 ─────────────────────────────────────────────

    fun respondMatch(accept: Boolean) {
        val request = _uiState.value.pendingMatch ?: return
        viewModelScope.launch {
            runCatching { repository.respondMatch(request.requestId, accept) }
                .onSuccess {
                    _uiState.update { it.copy(pendingMatch = null) }
                    _messages.tryEmit(if (accept) "已同意配对" else "已拒绝配对")
                }
                .onFailure { _messages.tryEmit("操作失败，请重试") }
        }
    }

    fun respondControl(accept: Boolean) {
        val request = _uiState.value.pendingControl ?: return
        viewModelScope.launch {
            runCatching { repository.respondControl(request.requestId, accept) }
                .onSuccess {
                    _uiState.update { it.copy(pendingControl = null) }
                    if (!accept) _messages.tryEmit("已拒绝控制")
                }
                .onFailure { _messages.tryEmit("操作失败，请重试") }
        }
    }

    fun consumeSessionPeer() {
        _uiState.update { it.copy(controlledSessionPeer = null) }
    }

    override fun onCleared() {
        codeJob?.cancel()
        super.onCleared()
    }
}
