package com.fantaysea.clan

import com.fantaysea.clan.data.androidIdToFingerprint
import com.fantaysea.clan.data.oaidToFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceFingerprintTest {

    @Test
    fun `missing or broken android id has no fingerprint`() {
        assertNull(androidIdToFingerprint(null))
        assertNull(androidIdToFingerprint(""))
        assertNull(androidIdToFingerprint("   "))
        assertNull(androidIdToFingerprint("9774d56d682e549c"))
    }

    @Test
    fun `fingerprint is stable lowercase sha256 within 64 chars`() {
        val fingerprint = requireNotNull(androidIdToFingerprint("  0123456789abcdef\n"))
        assertEquals(64, fingerprint.length)
        assertTrue(fingerprint.all { it in '0'..'9' || it in 'a'..'f' })
        assertEquals(fingerprint, androidIdToFingerprint("0123456789abcdef"))
    }

    @Test
    fun `different android ids produce different fingerprints`() {
        assertNotEquals(
            androidIdToFingerprint("0123456789abcdef"),
            androidIdToFingerprint("fedcba9876543210"),
        )
    }

    @Test
    fun `missing or placeholder oaid has no fingerprint`() {
        assertNull(oaidToFingerprint(null))
        assertNull(oaidToFingerprint(""))
        assertNull(oaidToFingerprint("   "))
        assertNull(oaidToFingerprint("00000000-0000-0000-0000-000000000000"))
        assertNull(oaidToFingerprint("UNKNOWN"))
        assertNull(oaidToFingerprint("null"))
    }

    @Test
    fun `oaid fingerprint normalizes case and whitespace`() {
        val fingerprint = requireNotNull(
            oaidToFingerprint("  550E8400-E29B-41D4-A716-446655440000\n"),
        )
        assertEquals(64, fingerprint.length)
        assertTrue(fingerprint.all { it in '0'..'9' || it in 'a'..'f' })
        assertEquals(fingerprint, oaidToFingerprint("550e8400-e29b-41d4-a716-446655440000"))
    }

    @Test
    fun `different oaids produce different fingerprints`() {
        assertNotEquals(
            oaidToFingerprint("550e8400-e29b-41d4-a716-446655440000"),
            oaidToFingerprint("550e8400-e29b-41d4-a716-446655440001"),
        )
    }
}
