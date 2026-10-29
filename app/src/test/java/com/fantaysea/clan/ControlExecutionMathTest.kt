package com.fantaysea.clan

import com.fantaysea.clan.service.normalizedToScreenCoordinate
import org.junit.Assert.assertEquals
import org.junit.Test

class ControlExecutionMathTest {

    @Test
    fun `normalized edges map to the first and last pixel`() {
        assertEquals(0f, normalizedToScreenCoordinate(0.0, sizePx = 1_000), 0.001f)
        assertEquals(999f, normalizedToScreenCoordinate(1.0, sizePx = 1_000), 0.001f)
    }

    @Test
    fun `center maps to the middle of the screen`() {
        assertEquals(499.5f, normalizedToScreenCoordinate(0.5, sizePx = 1_000), 0.001f)
    }

    @Test
    fun `coordinates are clamped and offset is applied`() {
        assertEquals(10f, normalizedToScreenCoordinate(-1.0, sizePx = 1_000, offsetPx = 10), 0.001f)
        assertEquals(1_009f, normalizedToScreenCoordinate(2.0, sizePx = 1_000, offsetPx = 10), 0.001f)
    }

    @Test
    fun `degenerate size stays at the offset`() {
        assertEquals(7f, normalizedToScreenCoordinate(0.5, sizePx = 1, offsetPx = 7), 0.001f)
        assertEquals(7f, normalizedToScreenCoordinate(0.5, sizePx = 0, offsetPx = 7), 0.001f)
    }
}
