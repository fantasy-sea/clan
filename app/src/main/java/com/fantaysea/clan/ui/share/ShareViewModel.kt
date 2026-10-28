package com.fantaysea.clan.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fantaysea.clan.data.PairingCodeGenerator
import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.data.model.SessionRole
import com.fantaysea.clan.data.model.SessionStatus
import com.fantaysea.clan.data.repository.ClanRepository
import com.fantaysea.clan.data.repository.BackendCapability
import com.fantaysea.clan.data.repository.RemoteEvent
import kotlinx.coroutines.CancellationException
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
    /** 服务端是否支持远程控制会话；决定控制请求弹窗是否展示。 */
    val controlAvailable: Boolean = false,
    val code: String = "",
    val expiresAt: Long = 0L,
    val remainingMillis: Long = 0L,
    val generating: Boolean = false,
    /** 最近一次生成配对码失败的原因；卡片上直接展示，并提供「重新生成」。 */
    val pairError: String? = null,
    val pendingMatch: RemoteEvent.MatchRequest? = null,
    val pendingControl: RemoteEvent.ControlRequest? = null,
    /** 会话建立后, 需要进入被控页的控制方(非空即导航) */
    val controlledSessionPeer: Device? = null,
)

class ShareViewModel(
    private val repository: ClanRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ShareUiState(
            // 进程内已授权过时保持“投屏就绪”，避免重新进入页面又显示未授权。
            permissionState = if (repository.localPermissions.value.screenCapture) {
                SharePermissionState.Granted
            } else {
                SharePermissionState.Unknown
            },
            pairingAvailable = BackendCapability.PAIRING in repository.capabilities,
            controlAvailable = BackendCapability.CONTROL_SESSIONS in repository.capabilities,
        ),
    )
    val uiState: StateFlow<ShareUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val handled = mutableSetOf<String>()
    private var codeJob: Job? = null
    private var codeFailureNotified = false

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
        // 服务端把被控会话推进到 CONNECTING/CONNECTED 后进入被控页。
        // 以 activeSession 兜底 REST 响应与 SESSION_STARTED 事件的竞态。
        viewModelScope.launch {
            repository.activeSession.collect { active ->
                val pending = _uiState.value.pendingControl ?: return@collect
                if (active == null || active.role != SessionRole.CONTROLLED) return@collect
                if (active.info.sessionId != pending.sessionId) return@collect
                if (active.info.status != SessionStatus.CONNECTING && active.info.status != SessionStatus.CONNECTED) return@collect
                if (!handled.add("session-started-${pending.sessionId}")) return@collect
                _uiState.update {
                    it.copy(pendingControl = null, controlledSessionPeer = active.peer)
                }
            }
        }
        // 配对与投屏权限解耦：进入页面即可生成配对码，投屏权限只影响后续共享。
        if (_uiState.value.pairingAvailable) startPairingCodeSession()
    }

    /** 屏幕共享授权结果(授权或拒绝)；配对码生成不依赖该权限。 */
    fun onPermissionResult(granted: Boolean) {
        if (!_uiState.value.pairingAvailable) return
        _uiState.update {
            it.copy(permissionState = if (granted) SharePermissionState.Granted else SharePermissionState.Denied)
        }
        // 真正的屏幕捕获/无障碍能力仍未接入；先把系统授权结果同步给会话层。
        repository.updateLocalPermissions(screenCapture = granted, accessibility = false)
    }

    private fun startPairingCodeSession() {
        if (!_uiState.value.pairingAvailable) return
        codeJob?.cancel()
        codeJob = viewModelScope.launch {
            // 失败后退避重试：3s → 6s → 12s，避免实时连接还没起来就反复刷服务端。
            var retryDelay = CODE_RETRY_DELAY_MS
            while (isActive) {
                if (!refreshPairingCode()) {
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(CODE_RETRY_MAX_DELAY_MS)
                    continue
                }
                retryDelay = CODE_RETRY_DELAY_MS
                while (isActive) {
                    val remaining = PairingCodeGenerator.remainingMillis(_uiState.value.expiresAt)
                    if (remaining <= 0) break
                    _uiState.update { it.copy(remainingMillis = remaining) }
                    delay(200)
                }
            }
        }
    }

    /** 用户手动重试：立刻重新走一轮生成，不等退避计时。 */
    fun retryPairingCode() {
        if (!_uiState.value.pairingAvailable) return
        codeFailureNotified = false
        startPairingCodeSession()
    }

    private suspend fun refreshPairingCode(): Boolean {
        _uiState.update { it.copy(generating = true, pairError = null) }
        return try {
            val pc = repository.createPairingCode()
            codeFailureNotified = false
            _uiState.update {
                it.copy(
                    code = pc.code,
                    expiresAt = pc.expiresAt,
                    remainingMillis = PairingCodeGenerator.remainingMillis(pc.expiresAt),
                    generating = false,
                    pairError = null,
                )
            }
            true
        } catch (cancelled: CancellationException) {
            // 页面退出/重开一轮会取消这个协程：不要当成业务失败，也不要吞掉取消信号。
            throw cancelled
        } catch (error: Exception) {
            val message = error.message?.takeIf { it.isNotBlank() } ?: "生成配对码失败，请重试"
            _uiState.update {
                it.copy(
                    code = "",
                    expiresAt = 0L,
                    remainingMillis = 0L,
                    generating = false,
                    pairError = message,
                )
            }
            if (!codeFailureNotified) {
                codeFailureNotified = true
                _messages.tryEmit(message)
            }
            false
        }
    }

    private suspend fun observeEvents() {
        repository.events.collect { event ->
            when (event) {
                is RemoteEvent.MatchRequest -> handleMatchRequest(event)
                is RemoteEvent.MatchResult -> handleMatchResult(event)
                is RemoteEvent.PairingSucceeded -> handlePairingSucceeded(event)
                is RemoteEvent.ControlRequest -> handleControlRequest(event)
                is RemoteEvent.ControlResult -> handleControlResult(event)
                is RemoteEvent.SessionEnded -> handleSessionEnded(event)
                is RemoteEvent.SessionError -> handleSessionError(event)
                else -> Unit
            }
        }
    }

    private fun handleMatchRequest(event: RemoteEvent.MatchRequest) {
        if (!handled.add("request-${event.requestId}")) return
        if (_uiState.value.pendingMatch == null) {
            _uiState.update { it.copy(pendingMatch = event) }
        }
    }

    private fun handleMatchResult(event: RemoteEvent.MatchResult) {
        val pending = _uiState.value.pendingMatch ?: return
        if (pending.requestId != event.requestId) return
        _uiState.update { it.copy(pendingMatch = null) }
        if (!event.accepted) {
            _messages.tryEmit(if (event.expired) "配对请求已过期" else "对方已取消配对")
            // 配对码在校验时已被服务端消费，立即换一张新码。
            startPairingCodeSession()
        }
    }

    private fun handlePairingSucceeded(event: RemoteEvent.PairingSucceeded) {
        if (!handled.add("paired-${event.requestId}")) return
        _messages.tryEmit("配对成功，设备已添加")
        // 本机作为被控端已完成一次配对；为下一次配对准备新码。
        startPairingCodeSession()
    }

    private fun handleControlRequest(event: RemoteEvent.ControlRequest) {
        if (!handled.add("control-${event.sessionId}")) return
        if (_uiState.value.pendingControl == null) {
            _uiState.update { it.copy(pendingControl = event) }
        }
    }

    private fun handleControlResult(event: RemoteEvent.ControlResult) {
        val pending = _uiState.value.pendingControl ?: return
        if (pending.sessionId != event.sessionId) return
        if (event.result == RemoteEvent.SessionRequestResult.ACCEPTED) return
        if (!handled.add("control-result-${event.sessionId}-${event.result}")) return
        _uiState.update { it.copy(pendingControl = null) }
        val message = when (event.result) {
            RemoteEvent.SessionRequestResult.EXPIRED -> "控制请求已过期"
            RemoteEvent.SessionRequestResult.REJECTED -> "已拒绝控制"
            RemoteEvent.SessionRequestResult.FAILED -> "控制请求失败，请重试"
            RemoteEvent.SessionRequestResult.ACCEPTED -> null
        }
        message?.let { _messages.tryEmit(it) }
    }

    private fun handleSessionEnded(event: RemoteEvent.SessionEnded) {
        val pending = _uiState.value.pendingControl ?: return
        if (pending.sessionId != event.sessionId) return
        _uiState.update { it.copy(pendingControl = null) }
        _messages.tryEmit(if (event.failed) "会话建立失败" else "会话已结束")
    }

    private fun handleSessionError(event: RemoteEvent.SessionError) {
        val pending = _uiState.value.pendingControl ?: return
        if (event.sessionId != pending.sessionId) return
        _uiState.update { it.copy(pendingControl = null) }
        _messages.tryEmit(event.message)
    }

    // ── 用户操作 ─────────────────────────────────────────────

    fun respondMatch(accept: Boolean) {
        val request = _uiState.value.pendingMatch ?: return
        viewModelScope.launch {
            runCatching { repository.respondMatch(request.requestId, accept) }
                .onSuccess {
                    _uiState.update { it.copy(pendingMatch = null) }
                    _messages.tryEmit(if (accept) "已同意配对" else "已拒绝配对")
                    // 拒绝后没有 PAIRING_SUCCEEDED；立即重新生成被消费掉的配对码。
                    if (!accept) startPairingCodeSession()
                }
                .onFailure { error -> _messages.tryEmit(error.message ?: "操作失败，请重试") }
        }
    }

    fun respondControl(accept: Boolean) {
        val request = _uiState.value.pendingControl ?: return
        viewModelScope.launch {
            runCatching { repository.respondControl(request.sessionId, accept) }
                .onSuccess {
                    val navigate = accept && handled.add("session-started-${request.sessionId}")
                    _uiState.update {
                        it.copy(
                            pendingControl = null,
                            controlledSessionPeer = if (navigate) request.controller else it.controlledSessionPeer,
                        )
                    }
                    if (!accept) _messages.tryEmit("已拒绝控制")
                }
                .onFailure { error -> _messages.tryEmit(error.message ?: "操作失败，请重试") }
        }
    }

    fun consumeSessionPeer() {
        _uiState.update { it.copy(controlledSessionPeer = null) }
    }

    override fun onCleared() {
        codeJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val CODE_RETRY_DELAY_MS = 3_000L
        const val CODE_RETRY_MAX_DELAY_MS = 15_000L
    }
}
