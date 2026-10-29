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
import com.fantaysea.clan.service.AccessibilitySettingsTarget
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

/** 无障碍分步引导的阶段：介绍 → 等待系统设置 → 未开启排障 / 已开启。 */
enum class AccessibilityGuideStage { Intro, Waiting, Missing, Enabled }

/** 无障碍分步引导状态。 */
data class AccessibilityGuideState(
    val visible: Boolean = false,
    val stage: AccessibilityGuideStage = AccessibilityGuideStage.Intro,
    /** 本次跳转实际落点，决定步骤 2 的文案；为 null 时用通用文案。 */
    val target: AccessibilitySettingsTarget? = null,
)

data class ShareUiState(
    /** 屏幕捕获(MediaProjection)授权状态。 */
    val permissionState: SharePermissionState = SharePermissionState.Unknown,
    /** 无障碍控制服务是否已在系统设置中开启；与屏幕捕获同时具备才允许接受控制请求。 */
    val accessibilityGranted: Boolean = false,
    /** 入口授权提示是否仍展示；用户点「稍后」或两项权限补齐后关闭。 */
    val permissionPromptVisible: Boolean = false,
    /** 无障碍分步引导（全屏）；与入口提示、控制请求弹窗互斥。 */
    val accessibilityGuide: AccessibilityGuideState = AccessibilityGuideState(),
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
) {
    /** 只有屏幕捕获与无障碍控制同时具备，才允许接受控制端的控制请求。 */
    val allPermissionsGranted: Boolean
        get() = permissionState == SharePermissionState.Granted && accessibilityGranted
}

class ShareViewModel(
    private val repository: ClanRepository,
    initialAccessibilityEnabled: Boolean = false,
) : ViewModel() {

    private val initialScreenCaptureGranted = repository.localPermissions.value.screenCapture
    private val pairingEnabled = BackendCapability.PAIRING in repository.capabilities

    private val _uiState = MutableStateFlow(
        ShareUiState(
            // 进程内已授权过时保持“投屏就绪”，避免重新进入页面又显示未授权。
            permissionState = if (initialScreenCaptureGranted) {
                SharePermissionState.Granted
            } else {
                SharePermissionState.Unknown
            },
            accessibilityGranted = initialAccessibilityEnabled,
            // 进入页面时若两项权限未齐先提示一次；配对不可用时页面本身不可操作，不提示。
            permissionPromptVisible = pairingEnabled &&
                !(initialScreenCaptureGranted && initialAccessibilityEnabled),
            pairingAvailable = pairingEnabled,
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
        // 进入页面即把真实权限（屏幕捕获 + 无障碍）同步给会话层，供 PERMISSION_STATUS 使用。
        repository.updateLocalPermissions(
            screenCapture = initialScreenCaptureGranted,
            accessibility = initialAccessibilityEnabled,
        )
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
            it.copy(
                permissionState = if (granted) SharePermissionState.Granted else SharePermissionState.Denied,
                // 屏幕共享已授予但无障碍未开启时，入口提示切换为第二步引导。
                permissionPromptVisible = if (granted) !it.accessibilityGranted else it.permissionPromptVisible,
            )
        }
        // 无障碍状态保留，避免只更新屏幕捕获时把已开启的无障碍权限覆盖成 false。
        repository.updateLocalPermissions(
            screenCapture = granted,
            accessibility = _uiState.value.accessibilityGranted,
        )
    }

    /** 无障碍控制权限结果；系统设置没有“拒绝”回调，未开启时保持 false。 */
    fun onAccessibilityResult(granted: Boolean) {
        _uiState.update {
            val guide = it.accessibilityGuide
            it.copy(
                accessibilityGranted = granted,
                // 两项权限齐全后不再展示入口提示。
                permissionPromptVisible = if (granted && it.permissionState == SharePermissionState.Granted) {
                    false
                } else {
                    it.permissionPromptVisible
                },
                // 引导可见时：已开启直接切成功态；未开启保持当前阶段，由返回检测的宽限逻辑判定。
                accessibilityGuide = if (guide.visible && granted) {
                    guide.copy(stage = AccessibilityGuideStage.Enabled)
                } else {
                    guide
                },
            )
        }
        repository.updateLocalPermissions(
            screenCapture = _uiState.value.permissionState == SharePermissionState.Granted,
            accessibility = granted,
        )
    }

    /** 打开无障碍分步引导：优先弹出「3 步说明」，而不是直接跳系统设置。 */
    fun openAccessibilityGuide() {
        val state = _uiState.value
        if (!state.pairingAvailable || state.accessibilityGranted) return
        _uiState.update {
            it.copy(
                // 引导与入口提示不叠加。
                permissionPromptVisible = false,
                accessibilityGuide = AccessibilityGuideState(visible = true),
            )
        }
    }

    /** 系统设置已打开，等待用户操作后返回检测。 */
    fun onAccessibilitySettingsOpened(target: AccessibilitySettingsTarget) {
        _uiState.update {
            if (!it.accessibilityGuide.visible) return@update it
            it.copy(
                accessibilityGuide = it.accessibilityGuide.copy(
                    stage = AccessibilityGuideStage.Waiting,
                    target = target,
                ),
            )
        }
    }

    /** 三个设置入口都打不开：留在介绍页，提示用户手动进入系统设置。 */
    fun onAccessibilitySettingsLaunchFailed() {
        _uiState.update {
            it.copy(accessibilityGuide = it.accessibilityGuide.copy(stage = AccessibilityGuideStage.Intro))
        }
        _messages.tryEmit("打不开系统设置，请手动打开「设置 → 无障碍」")
    }

    /** 从系统设置返回（或用户点「检测一下」）后的落定：开启则完成，未开启进入排障。 */
    fun onAccessibilitySetupReturned(granted: Boolean) {
        onAccessibilityResult(granted)
        if (granted) return
        _uiState.update {
            if (!it.accessibilityGuide.visible) return@update it
            it.copy(accessibilityGuide = it.accessibilityGuide.copy(stage = AccessibilityGuideStage.Missing))
        }
    }

    /** 关闭无障碍引导（「暂时不用」或成功提示自动关闭）。 */
    fun dismissAccessibilityGuide() {
        _uiState.update { it.copy(accessibilityGuide = AccessibilityGuideState()) }
    }

    /** 用户选择「稍后」，本次进入页面不再展示授权提示。 */
    fun dismissPermissionPrompt() {
        _uiState.update { it.copy(permissionPromptVisible = false) }
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
            // 配对确认优先于入口授权提示，避免两个弹窗叠加。
            _uiState.update { it.copy(pendingMatch = event, permissionPromptVisible = false) }
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
            // 控制确认优先于入口授权提示，避免两个弹窗叠加。
            _uiState.update { it.copy(pendingControl = event, permissionPromptVisible = false) }
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
        // 两项系统权限缺一不可：没有屏幕共享/无障碍控制时不能接受控制请求。
        if (accept && !_uiState.value.allPermissionsGranted) {
            _messages.tryEmit("请先授予屏幕共享和无障碍控制权限")
            return
        }
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
