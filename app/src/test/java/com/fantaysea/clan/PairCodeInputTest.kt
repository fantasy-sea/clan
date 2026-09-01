package com.fantaysea.clan

import com.fantaysea.clan.ui.control.PairCodeInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PairCodeInputTest {

    @Test
    fun `sanitize keeps only digits`() {
        assertEquals("123456", PairCodeInput.sanitize("12a3b456"))
    }

    @Test
    fun `sanitize caps at six digits`() {
        assertEquals("123456", PairCodeInput.sanitize("123456789"))
        assertEquals(6, PairCodeInput.sanitize("123456789").length)
    }

    @Test
    fun `sanitize passes through valid six digits`() {
        assertEquals("000123", PairCodeInput.sanitize("000123"))
    }

    @Test
    fun `isComplete true only for exactly six digits`() {
        assertFalse(PairCodeInput.isComplete(""))
        assertFalse(PairCodeInput.isComplete("12345"))
        assertFalse(PairCodeInput.isComplete("12345a"))
        assertTrue(PairCodeInput.isComplete("123456"))
        assertTrue(PairCodeInput.isComplete("000000"))
    }

    @Test
    fun `length constant is six`() {
        assertEquals(6, PairCodeInput.LENGTH)
    }
}
