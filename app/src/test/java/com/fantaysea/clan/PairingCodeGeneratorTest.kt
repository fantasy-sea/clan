package com.fantaysea.clan

import com.fantaysea.clan.data.PairingCodeGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingCodeGeneratorTest {

    @Test
    fun `generated code is six numeric digits`() {
        val code = PairingCodeGenerator.generate()
        assertEquals(6, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test
    fun `validity window is one minute`() {
        assertEquals(60_000L, PairingCodeGenerator.VALIDITY_MS)
    }

    @Test
    fun `isValid accepts only six digits`() {
        assertTrue(PairingCodeGenerator.isValid("123456"))
        assertFalse(PairingCodeGenerator.isValid("12345"))
        assertFalse(PairingCodeGenerator.isValid("12345a"))
        assertFalse(PairingCodeGenerator.isValid("1234567"))
    }

    @Test
    fun `isExpired boundary behavior`() {
        val now = 1_000_000L
        assertTrue(PairingCodeGenerator.isExpired(now, now))
        assertTrue(PairingCodeGenerator.isExpired(now - 1, now))
        assertFalse(PairingCodeGenerator.isExpired(now + 1, now))
    }

    @Test
    fun `remainingMillis clamps at zero`() {
        val now = 1_000_000L
        assertEquals(500L, PairingCodeGenerator.remainingMillis(now + 500, now))
        assertEquals(0L, PairingCodeGenerator.remainingMillis(now - 500, now))
    }

    @Test
    fun `consecutive generations differ`() {
        val codes = (0 until 100).map { PairingCodeGenerator.generate() }
        assertTrue(codes.distinct().size > 1)
    }
}
