package com.fantaysea.clan

import com.fantaysea.clan.ui.session.VideoRect
import com.fantaysea.clan.ui.session.fitVideoRect
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoLayoutTest {

    @Test
    fun `landscape video in portrait container is letterboxed vertically`() {
        val rect = fitVideoRect(
            videoWidth = 1920,
            videoHeight = 1080,
            containerWidth = 1080f,
            containerHeight = 1920f,
        )

        assertEquals(1080f, rect.width, 0.01f)
        assertEquals(607.5f, rect.height, 0.01f)
        assertEquals(0f, rect.left, 0.01f)
        assertEquals((1920f - 607.5f) / 2f, rect.top, 0.01f)
    }

    @Test
    fun `portrait video in landscape container is pillarboxed horizontally`() {
        val rect = fitVideoRect(
            videoWidth = 1080,
            videoHeight = 1920,
            containerWidth = 1920f,
            containerHeight = 1080f,
        )

        assertEquals(607.5f, rect.width, 0.01f)
        assertEquals(1080f, rect.height, 0.01f)
        assertEquals((1920f - 607.5f) / 2f, rect.left, 0.01f)
        assertEquals(0f, rect.top, 0.01f)
    }

    @Test
    fun `matching aspect ratio fills the whole container`() {
        val rect = fitVideoRect(
            videoWidth = 1920,
            videoHeight = 1080,
            containerWidth = 1920f,
            containerHeight = 1080f,
        )

        assertEquals(VideoRect(0f, 0f, 1920f, 1080f), rect)
    }

    @Test
    fun `unknown or empty size keeps the container untouched`() {
        val rect = fitVideoRect(
            videoWidth = 0,
            videoHeight = 0,
            containerWidth = 1080f,
            containerHeight = 1920f,
        )

        assertEquals(VideoRect(0f, 0f, 1080f, 1920f), rect)
    }
}
