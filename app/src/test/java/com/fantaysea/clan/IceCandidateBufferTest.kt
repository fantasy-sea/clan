package com.fantaysea.clan

import com.fantaysea.clan.data.model.IceCandidate
import com.fantaysea.clan.media.IceCandidateBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IceCandidateBufferTest {

    @Test
    fun `candidates queue until remote description is ready`() {
        val buffer = IceCandidateBuffer()
        val first = IceCandidate("candidate:1", "0", 0)
        val second = IceCandidate("candidate:2", "0", 1)

        assertTrue(buffer.add(first))
        assertTrue(buffer.add(second))
        assertEquals(2, buffer.bufferedCount())

        assertEquals(listOf(first, second), buffer.markRemoteDescriptionReady())
        assertEquals(0, buffer.bufferedCount())
    }

    @Test
    fun `candidates passed after remote description are not buffered`() {
        val buffer = IceCandidateBuffer()
        buffer.markRemoteDescriptionReady()

        assertFalse(buffer.add(IceCandidate("candidate:1", "0", 0)))
        assertEquals(0, buffer.bufferedCount())
    }

    @Test
    fun `clear resets queue and readiness`() {
        val buffer = IceCandidateBuffer()
        buffer.add(IceCandidate("candidate:1", "0", 0))
        buffer.clear()

        assertTrue(buffer.add(IceCandidate("candidate:2", "0", 0)))
        assertEquals(1, buffer.bufferedCount())
    }
}
