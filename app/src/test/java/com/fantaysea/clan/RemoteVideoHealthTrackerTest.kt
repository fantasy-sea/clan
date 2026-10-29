package com.fantaysea.clan

import com.fantaysea.clan.media.RemoteVideoHealthTracker
import com.fantaysea.clan.media.RemoteVideoState
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteVideoHealthTrackerTest {

    private val tracker = RemoteVideoHealthTracker(stallTimeoutMs = 5_000L)

    @Test
    fun `no active session is idle`() {
        assertEquals(
            RemoteVideoState.Idle,
            tracker.evaluate(nowMs = 10_000L, hasActiveSession = false, mediaConnected = false),
        )
    }

    @Test
    fun `media not connected waits for first frame`() {
        tracker.onFrame(nowMs = 1_000L)

        assertEquals(
            RemoteVideoState.WaitingFirstFrame,
            tracker.evaluate(nowMs = 1_100L, hasActiveSession = true, mediaConnected = false),
        )
    }

    @Test
    fun `first frame starts streaming while within timeout`() {
        tracker.onFrame(nowMs = 1_000L)

        assertEquals(
            RemoteVideoState.Streaming,
            tracker.evaluate(nowMs = 5_999L, hasActiveSession = true, mediaConnected = true),
        )
    }

    @Test
    fun `no frame beyond timeout stalls`() {
        tracker.onFrame(nowMs = 1_000L)

        assertEquals(
            RemoteVideoState.Stalled,
            tracker.evaluate(nowMs = 6_001L, hasActiveSession = true, mediaConnected = true),
        )
    }

    @Test
    fun `frame after stall resumes streaming`() {
        tracker.onFrame(nowMs = 1_000L)
        assertEquals(
            RemoteVideoState.Stalled,
            tracker.evaluate(nowMs = 7_000L, hasActiveSession = true, mediaConnected = true),
        )

        tracker.onFrame(nowMs = 7_100L)

        assertEquals(
            RemoteVideoState.Streaming,
            tracker.evaluate(nowMs = 7_200L, hasActiveSession = true, mediaConnected = true),
        )
    }

    @Test
    fun `reset drops the previous frame and waits again`() {
        tracker.onFrame(nowMs = 1_000L)

        tracker.reset()

        assertEquals(
            RemoteVideoState.WaitingFirstFrame,
            tracker.evaluate(nowMs = 1_100L, hasActiveSession = true, mediaConnected = true),
        )
    }
}
