package com.fantaysea.clan

import com.fantaysea.clan.data.model.Device
import com.fantaysea.clan.media.RemoteVideoSize
import com.fantaysea.clan.media.RemoteVideoState
import com.fantaysea.clan.media.WebRtcState
import com.fantaysea.clan.ui.session.ControlSessionUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlSessionGateTest {

    private val peer = Device(
        id = "dev_peer",
        deviceName = "妈妈的手机",
        platform = "ANDROID",
        model = "Pixel 8",
        androidVersion = "16",
        lastOnlineAt = null,
        createdAt = null,
    )
    private val videoSize = RemoteVideoSize(width = 1080, height = 2400)

    private fun readyState() = ControlSessionUiState(
        peer = peer,
        connected = true,
        mediaState = WebRtcState.Connected,
        remoteVideoState = RemoteVideoState.Streaming,
        remoteVideoSize = videoSize,
    )

    @Test
    fun `control is allowed only when session and video are both ready`() {
        assertTrue(readyState().canControl)
    }

    @Test
    fun `control is locked when the server session is not connected`() {
        assertFalse(readyState().copy(connected = false).canControl)
    }

    @Test
    fun `control is locked while media is negotiating reconnecting or failed`() {
        assertFalse(readyState().copy(mediaState = WebRtcState.Negotiating).canControl)
        assertFalse(readyState().copy(mediaState = WebRtcState.Reconnecting).canControl)
        assertFalse(readyState().copy(mediaState = WebRtcState.Failed("ice failed")).canControl)
    }

    @Test
    fun `control is locked before the first frame and while the stream is stalled`() {
        assertFalse(
            readyState()
                .copy(remoteVideoState = RemoteVideoState.WaitingFirstFrame, remoteVideoSize = null)
                .canControl,
        )
        assertFalse(readyState().copy(remoteVideoState = RemoteVideoState.Stalled).canControl)
        assertFalse(readyState().copy(remoteVideoState = RemoteVideoState.Idle).canControl)
    }

    @Test
    fun `control is locked without a decoded video size`() {
        assertFalse(readyState().copy(remoteVideoSize = null).canControl)
    }
}
