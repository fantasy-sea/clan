package com.fantaysea.clan

import androidx.lifecycle.viewModelScope
import com.fantaysea.clan.data.model.ActiveSession
import com.fantaysea.clan.data.model.ControlCommand
import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.data.model.IceCandidate
import com.fantaysea.clan.data.model.LocalPermissions
import com.fantaysea.clan.data.model.PairingCode
import com.fantaysea.clan.data.model.SessionInfo
import com.fantaysea.clan.data.model.SessionStatus
import com.fantaysea.clan.data.repository.BackendCapability
import com.fantaysea.clan.data.repository.ClanRepository
import com.fantaysea.clan.data.repository.DeviceRegistration
import com.fantaysea.clan.data.repository.PairedDevice
import com.fantaysea.clan.data.repository.RemoteEvent
import com.fantaysea.clan.service.AccessibilitySettingsTarget
import com.fantaysea.clan.ui.control.ControlHomeViewModel
import com.fantaysea.clan.ui.control.DeviceListState
import com.fantaysea.clan.ui.control.PairPhase
import com.fantaysea.clan.ui.share.AccessibilityGuideStage
import com.fantaysea.clan.ui.share.SharePermissionState
import com.fantaysea.clan.ui.share.ShareViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackendCapabilityUiTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `control view model shows unavailable without fetching devices`() = runTest(dispatcher) {
        val repository = TrackingRepository()

        val viewModel = ControlHomeViewModel(repository)

        assertTrue(viewModel.uiState.value.pageState is DeviceListState.Unavailable)
        assertEquals(0, repository.fetchCount)
    }

    @Test
    fun `control view model loads devices when binding capability is present`() = runTest(dispatcher) {
        val repository = TrackingRepository(
            capabilities = setOf(BackendCapability.DEVICE_IDENTITY, BackendCapability.DEVICE_BINDINGS),
        )

        val viewModel = ControlHomeViewModel(repository)
        advanceUntilIdle()

        assertEquals(1, repository.fetchCount)
        assertTrue(viewModel.uiState.value.pageState is DeviceListState.Empty)
    }

    @Test
    fun `control view model applies device presence without refetching the list`() = runTest(dispatcher) {
        val peer = PairedDevice(
            device = Device("dev_peer", "妈妈的手机", "ANDROID", "Pixel 8", "16", 1_000L, null),
            online = true,
        )
        val repository = TrackingRepository(
            capabilities = setOf(BackendCapability.DEVICE_IDENTITY, BackendCapability.DEVICE_BINDINGS),
            devices = listOf(peer),
        )
        val viewModel = ControlHomeViewModel(repository)
        advanceUntilIdle()
        assertEquals(1, repository.fetchCount)

        // 未知设备(例如已解绑)的上下线事件不应影响现有列表。
        repository.emit(RemoteEvent.DevicePresence("dev_unknown", online = false))
        runCurrent()
        assertTrue((viewModel.uiState.value.pageState as DeviceListState.Success).devices.single().online)

        repository.emit(RemoteEvent.DevicePresence("dev_peer", online = false, lastSeenAt = 2_000L))
        runCurrent()

        val updated = (viewModel.uiState.value.pageState as DeviceListState.Success).devices.single()
        assertFalse(updated.online)
        assertEquals(2_000L, updated.device.lastOnlineAt)
        assertEquals(1, repository.fetchCount)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `share view model ignores projection result when pairing is unavailable`() = runTest(dispatcher) {
        val repository = TrackingRepository()
        val viewModel = ShareViewModel(repository)

        viewModel.onPermissionResult(granted = true)

        assertEquals(SharePermissionState.Unknown, viewModel.uiState.value.permissionState)
        assertEquals(0, repository.pairingCodeCount)
        assertEquals("", viewModel.uiState.value.code)
    }

    @Test
    fun `share view model generates a code without projection permission`() = runTest(dispatcher) {
        val code = PairingCode(
            id = 1,
            code = "123456",
            deviceId = "dev_self",
            expiresAt = System.currentTimeMillis() + 60_000,
            used = false,
            createdAt = System.currentTimeMillis(),
        )
        val repository = TrackingRepository(
            capabilities = setOf(BackendCapability.DEVICE_IDENTITY, BackendCapability.PAIRING),
            pairingCode = code,
        )

        val viewModel = ShareViewModel(repository)
        runCurrent()

        assertEquals("123456", viewModel.uiState.value.code)
        assertEquals(SharePermissionState.Unknown, viewModel.uiState.value.permissionState)
        assertEquals(1, repository.pairingCodeCount)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `share view model refuses control until both permissions are granted`() = runTest(dispatcher) {
        val repository = TrackingRepository(
            capabilities = setOf(
                BackendCapability.DEVICE_IDENTITY,
                BackendCapability.PAIRING,
                BackendCapability.CONTROL_SESSIONS,
            ),
        )
        val viewModel = ShareViewModel(repository)
        runCurrent()
        val controller = Device("dev_controller", "爸爸的手机", "ANDROID", "Pixel 8", "16", null, null)
        repository.emit(RemoteEvent.ControlRequest("session_1", controller))
        runCurrent()
        assertEquals("session_1", viewModel.uiState.value.pendingControl?.sessionId)

        // 只有屏幕共享：仍不接受控制请求，也不调用服务端。
        viewModel.onPermissionResult(granted = true)
        viewModel.respondControl(accept = true)
        runCurrent()
        assertTrue(repository.respondControlCalls.isEmpty())
        assertEquals("session_1", viewModel.uiState.value.pendingControl?.sessionId)

        // 无障碍也授予后即可接受。
        viewModel.onAccessibilityResult(granted = true)
        viewModel.respondControl(accept = true)
        runCurrent()
        assertEquals(listOf("session_1" to true), repository.respondControlCalls)
        assertNull(viewModel.uiState.value.pendingControl)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `share view model reports accessibility together with capture`() = runTest(dispatcher) {
        val repository = TrackingRepository(
            capabilities = setOf(BackendCapability.DEVICE_IDENTITY, BackendCapability.PAIRING),
        )
        val viewModel = ShareViewModel(repository)

        viewModel.onAccessibilityResult(granted = true)
        viewModel.onPermissionResult(granted = true)
        assertEquals(LocalPermissions(screenCapture = true, accessibility = true), repository.localPermissions.value)
        assertTrue(viewModel.uiState.value.allPermissionsGranted)

        // 屏幕共享结果变化不应覆盖已开启的无障碍权限。
        viewModel.onPermissionResult(granted = false)
        assertEquals(LocalPermissions(screenCapture = false, accessibility = true), repository.localPermissions.value)
        assertFalse(viewModel.uiState.value.allPermissionsGranted)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `share view model prompts for permissions and guides accessibility after capture`() = runTest(dispatcher) {
        val repository = TrackingRepository(
            capabilities = setOf(BackendCapability.DEVICE_IDENTITY, BackendCapability.PAIRING),
        )
        val viewModel = ShareViewModel(repository)
        assertTrue(viewModel.uiState.value.permissionPromptVisible)

        // 「稍后」只关闭本次提示；授予屏幕共享后再次提示，引导开启无障碍。
        viewModel.dismissPermissionPrompt()
        assertFalse(viewModel.uiState.value.permissionPromptVisible)
        viewModel.onPermissionResult(granted = true)
        assertTrue(viewModel.uiState.value.permissionPromptVisible)

        viewModel.onAccessibilityResult(granted = true)
        assertFalse(viewModel.uiState.value.permissionPromptVisible)
        assertTrue(viewModel.uiState.value.allPermissionsGranted)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `share view model skips prompt when both permissions are already granted`() = runTest(dispatcher) {
        val repository = TrackingRepository(
            capabilities = setOf(BackendCapability.DEVICE_IDENTITY, BackendCapability.PAIRING),
        )
        repository.updateLocalPermissions(screenCapture = true, accessibility = false)
        val viewModel = ShareViewModel(repository, initialAccessibilityEnabled = true)

        assertFalse(viewModel.uiState.value.permissionPromptVisible)
        assertTrue(viewModel.uiState.value.allPermissionsGranted)
        assertEquals(LocalPermissions(screenCapture = true, accessibility = true), repository.localPermissions.value)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `opening accessibility guide replaces the entry prompt and records the settings target`() = runTest(dispatcher) {
        val repository = TrackingRepository(
            capabilities = setOf(BackendCapability.DEVICE_IDENTITY, BackendCapability.PAIRING),
        )
        val viewModel = ShareViewModel(repository)
        runCurrent()
        assertTrue(viewModel.uiState.value.permissionPromptVisible)

        viewModel.openAccessibilityGuide()

        val intro = viewModel.uiState.value.accessibilityGuide
        assertTrue(intro.visible)
        assertEquals(AccessibilityGuideStage.Intro, intro.stage)
        assertNull(intro.target)
        assertFalse(viewModel.uiState.value.permissionPromptVisible)

        viewModel.onAccessibilitySettingsOpened(AccessibilitySettingsTarget.ServiceDetail)

        val waiting = viewModel.uiState.value.accessibilityGuide
        assertEquals(AccessibilityGuideStage.Waiting, waiting.stage)
        assertEquals(AccessibilitySettingsTarget.ServiceDetail, waiting.target)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `returning without enabling keeps waiting until the grace period ends`() = runTest(dispatcher) {
        val repository = TrackingRepository(
            capabilities = setOf(BackendCapability.DEVICE_IDENTITY, BackendCapability.PAIRING),
        )
        val viewModel = ShareViewModel(repository)
        runCurrent()
        viewModel.openAccessibilityGuide()
        viewModel.onAccessibilitySettingsOpened(AccessibilitySettingsTarget.AccessibilityList)

        // 刚回到前台时状态可能还没同步：初步检测失败不应立刻判定为未开启。
        viewModel.onAccessibilityResult(granted = false)
        assertEquals(AccessibilityGuideStage.Waiting, viewModel.uiState.value.accessibilityGuide.stage)

        viewModel.onAccessibilitySetupReturned(granted = false)

        assertEquals(AccessibilityGuideStage.Missing, viewModel.uiState.value.accessibilityGuide.stage)
        assertFalse(viewModel.uiState.value.accessibilityGranted)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `enabling accessibility finishes the guide and unlocks control`() = runTest(dispatcher) {
        val repository = TrackingRepository(
            capabilities = setOf(
                BackendCapability.DEVICE_IDENTITY,
                BackendCapability.PAIRING,
                BackendCapability.CONTROL_SESSIONS,
            ),
        )
        val viewModel = ShareViewModel(repository)
        runCurrent()
        viewModel.onPermissionResult(granted = true)
        viewModel.openAccessibilityGuide()
        viewModel.onAccessibilitySettingsOpened(AccessibilitySettingsTarget.ServiceDetail)

        viewModel.onAccessibilitySetupReturned(granted = true)

        val guide = viewModel.uiState.value.accessibilityGuide
        assertTrue(guide.visible)
        assertEquals(AccessibilityGuideStage.Enabled, guide.stage)
        assertTrue(viewModel.uiState.value.allPermissionsGranted)
        assertFalse(viewModel.uiState.value.permissionPromptVisible)
        assertEquals(LocalPermissions(screenCapture = true, accessibility = true), repository.localPermissions.value)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `dismissing the accessibility guide hides it`() = runTest(dispatcher) {
        val repository = TrackingRepository(
            capabilities = setOf(BackendCapability.DEVICE_IDENTITY, BackendCapability.PAIRING),
        )
        val viewModel = ShareViewModel(repository)
        runCurrent()
        viewModel.openAccessibilityGuide()

        viewModel.dismissAccessibilityGuide()

        assertFalse(viewModel.uiState.value.accessibilityGuide.visible)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `control view model completes pairing when succeeded event arrives`() = runTest(dispatcher) {
        val repository = TrackingRepository(
            capabilities = setOf(
                BackendCapability.DEVICE_IDENTITY,
                BackendCapability.DEVICE_BINDINGS,
                BackendCapability.PAIRING,
            ),
        )
        val viewModel = ControlHomeViewModel(repository)
        runCurrent()

        viewModel.openPairDialog()
        viewModel.submitPairCode("123456")
        runCurrent()
        assertEquals(PairPhase.Waiting, viewModel.uiState.value.pairPhase)

        repository.emit(
            RemoteEvent.PairingSucceeded(
                requestId = "pairreq_1",
                pairedDevice = Device("dev_peer", "妈妈的手机", "ANDROID", "Pixel 8", "16", null, null),
            ),
        )
        runCurrent()

        assertEquals(PairPhase.Success, viewModel.uiState.value.pairPhase)
        assertNull(viewModel.uiState.value.pendingPairRequestId)
        assertTrue(repository.fetchCount >= 1)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `control view model cancels a waiting request when dialog closes`() = runTest(dispatcher) {
        val repository = TrackingRepository(
            capabilities = setOf(
                BackendCapability.DEVICE_IDENTITY,
                BackendCapability.DEVICE_BINDINGS,
                BackendCapability.PAIRING,
            ),
        )
        val viewModel = ControlHomeViewModel(repository)
        runCurrent()
        viewModel.openPairDialog()
        viewModel.submitPairCode("123456")
        runCurrent()

        viewModel.closePairDialog()
        runCurrent()

        assertEquals(1, repository.cancelCount)
        assertEquals("pairreq_1", repository.canceledRequestIds.single())
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `share view model clears pending match on expired result`() = runTest(dispatcher) {
        val repository = TrackingRepository()
        val viewModel = ShareViewModel(repository)
        runCurrent()
        val requester = Device("dev_requester", "爸爸的手机", "ANDROID", "Pixel 8", "16", null, null)

        repository.emit(RemoteEvent.MatchRequest("pairreq_1", "123456", requester))
        runCurrent()
        assertEquals("pairreq_1", viewModel.uiState.value.pendingMatch?.requestId)

        repository.emit(RemoteEvent.MatchResult("pairreq_1", accepted = false, pairedDevice = null, expired = true))
        runCurrent()

        assertNull(viewModel.uiState.value.pendingMatch)
    }

    @Test
    fun `control view model batch deletes selected devices`() = runTest(dispatcher) {
        val first = PairedDevice(
            device = Device("dev_mom", "妈妈的手机", "ANDROID", "Pixel 8", "16", null, null),
            online = true,
        )
        val second = PairedDevice(
            device = Device("dev_dad", "爸爸的手机", "ANDROID", "Pixel 8", "16", null, null),
            online = false,
        )
        val repository = TrackingRepository(
            capabilities = setOf(BackendCapability.DEVICE_IDENTITY, BackendCapability.DEVICE_BINDINGS),
            devices = listOf(first, second),
        )
        val viewModel = ControlHomeViewModel(repository)
        advanceUntilIdle()

        // 长按进入多选, 默认选中长按项; 全选后批量删除。
        viewModel.enterSelection(first)
        assertTrue(viewModel.uiState.value.selectionMode)
        assertEquals(setOf("dev_mom"), viewModel.uiState.value.selectedDeviceIds)

        viewModel.selectAllDevices()
        assertEquals(setOf("dev_mom", "dev_dad"), viewModel.uiState.value.selectedDeviceIds)

        viewModel.askBatchDeleteDevices()
        assertTrue(viewModel.uiState.value.batchDeleteConfirm)

        viewModel.deleteSelectedDevices()
        advanceUntilIdle()

        assertEquals(listOf("dev_mom", "dev_dad"), repository.unboundDeviceIds)
        assertFalse(viewModel.uiState.value.batchDeleteConfirm)
        assertFalse(viewModel.uiState.value.selectionMode)
        assertTrue(viewModel.uiState.value.selectedDeviceIds.isEmpty())
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `control view model renames device through repository`() = runTest(dispatcher) {
        val peer = PairedDevice(
            device = Device("dev_mom", "妈妈的手机", "ANDROID", "Pixel 8", "16", null, null),
            online = true,
        )
        val repository = TrackingRepository(
            capabilities = setOf(BackendCapability.DEVICE_IDENTITY, BackendCapability.DEVICE_BINDINGS),
            devices = listOf(peer),
        )
        val viewModel = ControlHomeViewModel(repository)
        advanceUntilIdle()

        viewModel.askEditDevice(peer)
        assertEquals("妈妈的手机", viewModel.uiState.value.editDeviceName)

        viewModel.onEditDeviceNameChanged("  妈妈的 Mate  ")
        viewModel.confirmEditDevice()
        advanceUntilIdle()

        // 名称去除首尾空白后写回服务端, 成功后关闭弹窗。
        assertEquals(listOf("dev_mom" to "妈妈的 Mate"), repository.renamedDevices)
        assertNull(viewModel.uiState.value.editTarget)
        assertFalse(viewModel.uiState.value.editSaving)
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `control view model rejects blank device name`() = runTest(dispatcher) {
        val peer = PairedDevice(
            device = Device("dev_mom", "妈妈的手机", "ANDROID", "Pixel 8", "16", null, null),
            online = true,
        )
        val repository = TrackingRepository(
            capabilities = setOf(BackendCapability.DEVICE_IDENTITY, BackendCapability.DEVICE_BINDINGS),
            devices = listOf(peer),
        )
        val viewModel = ControlHomeViewModel(repository)
        advanceUntilIdle()

        viewModel.askEditDevice(peer)
        viewModel.onEditDeviceNameChanged("   ")
        viewModel.confirmEditDevice()
        advanceUntilIdle()

        assertEquals("设备名称不能为空", viewModel.uiState.value.editError)
        assertTrue(repository.renamedDevices.isEmpty())
        viewModel.viewModelScope.cancel()
    }

    private class TrackingRepository(
        override val capabilities: Set<BackendCapability> = setOf(BackendCapability.DEVICE_IDENTITY),
        private val pairingCode: PairingCode? = null,
        private val devices: List<PairedDevice> = emptyList(),
    ) : ClanRepository {
        private val eventFlow = MutableSharedFlow<RemoteEvent>(extraBufferCapacity = 8)
        override val events: SharedFlow<RemoteEvent> = eventFlow
        override val self: StateFlow<Device?> = MutableStateFlow(null)
        private val activeSessionFlow = MutableStateFlow<ActiveSession?>(null)
        override val activeSession: StateFlow<ActiveSession?> = activeSessionFlow
        private val localPermissionsFlow = MutableStateFlow(LocalPermissions())
        override val localPermissions: StateFlow<LocalPermissions> = localPermissionsFlow
        var fetchCount = 0
        var pairingCodeCount = 0
        var cancelCount = 0
        val canceledRequestIds = mutableListOf<String>()
        val unboundDeviceIds = mutableListOf<String>()
        val renamedDevices = mutableListOf<Pair<String, String>>()
        val respondControlCalls = mutableListOf<Pair<String, Boolean>>()

        suspend fun emit(event: RemoteEvent) = eventFlow.emit(event)

        override suspend fun initializeSelf(registration: DeviceRegistration): Device = error("not used")
        override suspend fun fetchDevices(): List<PairedDevice> { fetchCount++; return devices }
        override suspend fun findDevice(deviceId: String): Device? = null
        override suspend fun unbindDevice(deviceId: String) { unboundDeviceIds += deviceId }
        override suspend fun updateDeviceName(deviceId: String, deviceName: String) {
            renamedDevices += deviceId to deviceName
        }
        override suspend fun createPairingCode(): PairingCode {
            pairingCodeCount++
            return pairingCode ?: error("pairing code not configured")
        }
        override suspend fun submitPairingCode(code: String): String = "pairreq_1"
        override suspend fun respondMatch(requestId: String, accept: Boolean) = Unit
        override suspend fun cancelPairingRequest(requestId: String) {
            cancelCount++
            canceledRequestIds += requestId
        }
        override suspend fun sendControlRequest(device: Device): SessionInfo = error("not used")
        override suspend fun respondControl(sessionId: String, accept: Boolean): SessionInfo {
            respondControlCalls += sessionId to accept
            return SessionInfo(
                sessionId = sessionId,
                controllerDeviceId = "dev_controller",
                controlledDeviceId = "dev_self",
                status = SessionStatus.CONNECTING,
                expiresAt = null,
                createdAt = null,
            )
        }
        override suspend fun endSession(sessionId: String) = Unit
        override suspend fun sendControl(sessionId: String, command: ControlCommand): String = "ctl_test"
        override suspend fun sendControlResult(
            sessionId: String,
            requestId: String,
            ok: Boolean,
            reason: String?,
        ) = Unit

        override suspend fun sendPermissionStatus(
            sessionId: String,
            screenCapture: Boolean,
            accessibility: Boolean,
        ) = Unit

        override suspend fun sendSessionConnected(sessionId: String) = Unit
        override suspend fun sendWebRtcOffer(sessionId: String, requestId: String, sdp: String) = Unit
        override suspend fun sendWebRtcAnswer(sessionId: String, requestId: String, sdp: String) = Unit
        override suspend fun sendIceCandidate(sessionId: String, requestId: String, candidate: IceCandidate) = Unit

        override fun updateLocalPermissions(screenCapture: Boolean, accessibility: Boolean) {
            localPermissionsFlow.value = LocalPermissions(screenCapture, accessibility)
        }
    }
}
