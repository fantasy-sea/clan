package com.fantaysea.clan

import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.data.model.PairingCode
import com.fantaysea.clan.data.repository.BackendCapability
import com.fantaysea.clan.data.repository.ClanRepository
import com.fantaysea.clan.data.repository.DeviceRegistration
import com.fantaysea.clan.data.repository.PairedDevice
import com.fantaysea.clan.data.repository.RemoteEvent
import com.fantaysea.clan.ui.control.ControlHomeViewModel
import com.fantaysea.clan.ui.control.DeviceListState
import com.fantaysea.clan.ui.share.SharePermissionState
import com.fantaysea.clan.ui.share.ShareViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertEquals
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
    fun `share view model ignores projection result when pairing is unavailable`() = runTest(dispatcher) {
        val repository = TrackingRepository()
        val viewModel = ShareViewModel(repository)

        viewModel.onPermissionResult(granted = true)

        assertEquals(SharePermissionState.Unknown, viewModel.uiState.value.permissionState)
        assertEquals(0, repository.pairingCodeCount)
        assertEquals("", viewModel.uiState.value.code)
    }

    private class TrackingRepository : ClanRepository {
        override val capabilities = setOf(BackendCapability.DEVICE_IDENTITY)
        override val events: SharedFlow<RemoteEvent> = MutableSharedFlow()
        override val self: StateFlow<Device?> = MutableStateFlow(null)
        var fetchCount = 0
        var pairingCodeCount = 0

        override suspend fun initializeSelf(registration: DeviceRegistration): Device = error("not used")
        override suspend fun fetchDevices(): List<PairedDevice> { fetchCount++; return emptyList() }
        override suspend fun findDevice(deviceId: String): Device? = null
        override suspend fun unbindDevice(deviceId: String) = Unit
        override suspend fun createPairingCode(): PairingCode { pairingCodeCount++; error("not used") }
        override suspend fun submitPairingCode(code: String): String = error("not used")
        override suspend fun respondMatch(requestId: String, accept: Boolean) = Unit
        override suspend fun sendControlRequest(deviceId: String): String = error("not used")
        override suspend fun respondControl(requestId: String, accept: Boolean) = Unit
        override suspend fun endSession(peerDeviceId: String) = Unit
    }
}
