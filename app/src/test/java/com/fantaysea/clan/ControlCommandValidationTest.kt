package com.fantaysea.clan

import com.fantaysea.clan.data.model.ControlCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControlCommandValidationTest {

    @Test
    fun `valid commands pass validation`() {
        assertNull(ControlCommand.Tap(0.5, 0.5).validationError())
        assertNull(ControlCommand.Swipe(0.1, 0.2, 0.3, 0.4, duration = 300).validationError())
        assertNull(ControlCommand.Back.validationError())
        assertNull(ControlCommand.Home.validationError())
        assertNull(ControlCommand.Recents.validationError())
    }

    @Test
    fun `tap rejects non finite and out of range coordinates`() {
        assertEquals(ControlCommand.INVALID_PARAMETER_REASON, ControlCommand.Tap(Double.NaN, 0.5).validationError())
        assertEquals(
            ControlCommand.INVALID_PARAMETER_REASON,
            ControlCommand.Tap(0.5, Double.POSITIVE_INFINITY).validationError(),
        )
        assertEquals(ControlCommand.INVALID_PARAMETER_REASON, ControlCommand.Tap(-0.01, 0.5).validationError())
        assertEquals(ControlCommand.INVALID_PARAMETER_REASON, ControlCommand.Tap(0.5, 1.01).validationError())
    }

    @Test
    fun `swipe rejects out of range coordinates and illegal duration`() {
        assertEquals(
            ControlCommand.INVALID_PARAMETER_REASON,
            ControlCommand.Swipe(0.1, 0.2, 0.3, 1.2, duration = 300).validationError(),
        )
        assertEquals(
            ControlCommand.INVALID_PARAMETER_REASON,
            ControlCommand.Swipe(0.1, 0.2, 0.3, 0.4, duration = 49).validationError(),
        )
        assertEquals(
            ControlCommand.INVALID_PARAMETER_REASON,
            ControlCommand.Swipe(0.1, 0.2, 0.3, 0.4, duration = 5_001).validationError(),
        )
    }
}
