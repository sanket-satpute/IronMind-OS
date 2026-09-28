package com.sanket_satpute_20.ironmind.domain.model.pattern

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EvidenceReferenceTest {

    @Test
    fun `serialize formats correctly`() {
        val ref = EvidenceReference("test-id", EvidenceSourceType.EVENT)
        assertEquals("EVENT|test-id", ref.serialize())
    }

    @Test
    fun `deserialize parses valid format`() {
        val result = EvidenceReference.deserialize("OBSERVATION|obs-123")
        assertEquals(EvidenceSourceType.OBSERVATION, result?.sourceType)
        assertEquals("obs-123", result?.sourceId)
    }

    @Test
    fun `deserialize handles invalid type gracefully`() {
        val result = EvidenceReference.deserialize("UNKNOWN_TYPE|obs-123")
        assertNull(result)
    }

    @Test
    fun `deserialize handles legacy format gracefully`() {
        val result = EvidenceReference.deserialize("just-an-id")
        assertEquals(EvidenceSourceType.LEGACY_AMBIGUOUS, result?.sourceType)
        assertEquals("just-an-id", result?.sourceId)
    }

    @Test
    fun `deserialize rejects blank source ID`() {
        assertNull(EvidenceReference.deserialize(""))
    }

    @Test
    fun `deserialize rejects whitespace source ID`() {
        assertNull(EvidenceReference.deserialize("   "))
    }

    @Test
    fun `deserialize rejects TYPE with empty ID`() {
        assertNull(EvidenceReference.deserialize("OBSERVATION|"))
    }

    @Test
    fun `deserialize rejects TYPE with whitespace ID`() {
        assertNull(EvidenceReference.deserialize("EVENT|   "))
    }
}
