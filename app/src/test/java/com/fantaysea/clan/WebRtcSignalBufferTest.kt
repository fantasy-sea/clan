package com.fantaysea.clan

import com.fantaysea.clan.data.model.IceCandidate
import com.fantaysea.clan.data.repository.RemoteEvent
import com.fantaysea.clan.media.WebRtcSignalBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebRtcSignalBufferTest {

    @Test
    fun `signals are buffered per session and drained in arrival order`() {
        val buffer = WebRtcSignalBuffer(maxSessions = 4, ttlMs = 60_000)
        val offer = RemoteEvent.WebRtcOffer("s1", "r1", "v=0-offer")
        val ice = RemoteEvent.IceCandidateEvent(
            sessionId = "s1",
            candidate = IceCandidate("candidate:1", "0", 0),
        )

        buffer.put("s1", offer, now = 1_000)
        buffer.put("s1", ice, now = 1_001)

        assertEquals(listOf<RemoteEvent.Signal>(offer, ice), buffer.take("s1"))
        assertTrue(buffer.take("s1").isEmpty())
    }

    @Test
    fun `sessions do not leak into each other`() {
        val buffer = WebRtcSignalBuffer(maxSessions = 4, ttlMs = 60_000)
        val offer1 = RemoteEvent.WebRtcOffer("s1", null, "s1-sdp")
        val answer2 = RemoteEvent.WebRtcAnswer("s2", null, "s2-sdp")

        buffer.put("s1", offer1, now = 1_000)
        buffer.put("s2", answer2, now = 1_001)

        assertEquals(listOf<RemoteEvent.Signal>(offer1), buffer.take("s1"))
        assertEquals(listOf<RemoteEvent.Signal>(answer2), buffer.take("s2"))
    }

    @Test
    fun `stale sessions are pruned by ttl`() {
        val buffer = WebRtcSignalBuffer(maxSessions = 4, ttlMs = 10_000)
        buffer.put("old", RemoteEvent.WebRtcOffer("old", null, "sdp"), now = 1_000)

        buffer.prune(now = 11_001)

        assertTrue(buffer.take("old").isEmpty())
    }

    @Test
    fun `oldest session is evicted when capacity is exceeded`() {
        val buffer = WebRtcSignalBuffer(maxSessions = 2, ttlMs = 60_000)
        buffer.put("s1", RemoteEvent.WebRtcOffer("s1", null, "1"), now = 1_000)
        buffer.put("s2", RemoteEvent.WebRtcOffer("s2", null, "2"), now = 1_001)
        buffer.put("s3", RemoteEvent.WebRtcOffer("s3", null, "3"), now = 1_002)

        assertTrue(buffer.take("s1").isEmpty())
        assertEquals(1, buffer.take("s2").size)
        assertEquals(1, buffer.take("s3").size)
    }

    @Test
    fun `clear drops buffered signals for a session`() {
        val buffer = WebRtcSignalBuffer(maxSessions = 4, ttlMs = 60_000)
        buffer.put("s1", RemoteEvent.WebRtcOffer("s1", null, "sdp"), now = 1_000)

        buffer.clear("s1")

        assertTrue(buffer.take("s1").isEmpty())
    }
}
