package com.sanket_satpute_20.ironmind.domain.engine

import com.sanket_satpute_20.ironmind.domain.model.pattern.EvidenceReference
import com.sanket_satpute_20.ironmind.domain.model.pattern.EvidenceSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class EvidenceSufficiencyEvaluatorTest {

    private lateinit var evaluator: EvidenceSufficiencyEvaluator

    // UTC for baseline testing
    private val defaultZone = ZoneId.of("UTC")
    
    // Timezones for boundary testing
    private val tokyoZone = ZoneId.of("Asia/Tokyo") // UTC+9
    private val newYorkZone = ZoneId.of("America/New_York") // UTC-5 (or -4)

    @Before
    fun setup() {
        evaluator = EvidenceSufficiencyEvaluator()
    }

    private fun buildEvidence(
        id: String,
        type: EvidenceSourceType,
        year: Int,
        month: Int,
        dayOfMonth: Int,
        hour: Int,
        minute: Int,
        zone: ZoneId = defaultZone
    ): ValidatedEvidence {
        val zdt = ZonedDateTime.of(year, month, dayOfMonth, hour, minute, 0, 0, zone)
        return ValidatedEvidence(
            reference = EvidenceReference(id, type),
            timestampMs = zdt.toInstant().toEpochMilli()
        )
    }

    @Test
    fun `Test 1 - Zero evidence returns INSUFFICIENT`() {
        val result = evaluator.evaluate(emptyList(), defaultZone)
        assertTrue(result is EvidenceSufficiencyResult.Insufficient)
        assertEquals(EvidenceSufficiencyReason.INSUFFICIENT_UNIQUE_EVIDENCE, (result as EvidenceSufficiencyResult.Insufficient).reason)
    }

    @Test
    fun `Test 2 - One evidence returns INSUFFICIENT`() {
        val evidence = listOf(
            buildEvidence("1", EvidenceSourceType.EVENT, 2024, 1, 1, 10, 0)
        )
        val result = evaluator.evaluate(evidence, defaultZone)
        assertTrue(result is EvidenceSufficiencyResult.Insufficient)
        assertEquals(EvidenceSufficiencyReason.INSUFFICIENT_UNIQUE_EVIDENCE, (result as EvidenceSufficiencyResult.Insufficient).reason)
    }

    @Test
    fun `Test 3 - Two unique evidence references returns INSUFFICIENT`() {
        val evidence = listOf(
            buildEvidence("1", EvidenceSourceType.EVENT, 2024, 1, 1, 10, 0),
            buildEvidence("2", EvidenceSourceType.OBSERVATION, 2024, 1, 2, 10, 0)
        )
        val result = evaluator.evaluate(evidence, defaultZone)
        assertTrue(result is EvidenceSufficiencyResult.Insufficient)
        assertEquals(EvidenceSufficiencyReason.INSUFFICIENT_UNIQUE_EVIDENCE, (result as EvidenceSufficiencyResult.Insufficient).reason)
    }

    @Test
    fun `Test 4 and 9 - Exactly three unique evidence references across two days returns SUFFICIENT`() {
        val evidence = listOf(
            buildEvidence("1", EvidenceSourceType.EVENT, 2024, 1, 1, 10, 0),
            buildEvidence("2", EvidenceSourceType.EVENT, 2024, 1, 1, 12, 0),
            buildEvidence("3", EvidenceSourceType.EVENT, 2024, 1, 2, 10, 0)
        )
        val result = evaluator.evaluate(evidence, defaultZone)
        assertTrue(result is EvidenceSufficiencyResult.Sufficient)
    }

    @Test
    fun `Test 5 and 10 - More than three evidence references across three days returns SUFFICIENT`() {
        val evidence = listOf(
            buildEvidence("1", EvidenceSourceType.EVENT, 2024, 1, 1, 10, 0),
            buildEvidence("2", EvidenceSourceType.EVENT, 2024, 1, 2, 12, 0),
            buildEvidence("3", EvidenceSourceType.EVENT, 2024, 1, 3, 10, 0),
            buildEvidence("4", EvidenceSourceType.EVENT, 2024, 1, 4, 10, 0)
        )
        val result = evaluator.evaluate(evidence, defaultZone)
        assertTrue(result is EvidenceSufficiencyResult.Sufficient)
    }

    @Test
    fun `Test 6 and 7 - Duplicate EvidenceReferences do not inflate count`() {
        val duplicateEvidence = buildEvidence("1", EvidenceSourceType.EVENT, 2024, 1, 1, 10, 0)
        val evidence = listOf(
            duplicateEvidence,
            duplicateEvidence,
            duplicateEvidence,
            buildEvidence("2", EvidenceSourceType.EVENT, 2024, 1, 2, 10, 0)
        )
        // Only 2 unique items
        val result = evaluator.evaluate(evidence, defaultZone)
        assertTrue(result is EvidenceSufficiencyResult.Insufficient)
        assertEquals(EvidenceSufficiencyReason.INSUFFICIENT_UNIQUE_EVIDENCE, (result as EvidenceSufficiencyResult.Insufficient).reason)
    }

    @Test
    fun `Test 8 - Three evidence items on one calendar day returns INSUFFICIENT`() {
        val evidence = listOf(
            buildEvidence("1", EvidenceSourceType.EVENT, 2024, 1, 1, 8, 0),
            buildEvidence("2", EvidenceSourceType.EVENT, 2024, 1, 1, 12, 0),
            buildEvidence("3", EvidenceSourceType.EVENT, 2024, 1, 1, 18, 0)
        )
        val result = evaluator.evaluate(evidence, defaultZone)
        assertTrue(result is EvidenceSufficiencyResult.Insufficient)
        assertEquals(EvidenceSufficiencyReason.INSUFFICIENT_DISTINCT_CALENDAR_DAYS, (result as EvidenceSufficiencyResult.Insufficient).reason)
    }

    @Test
    fun `Test 11 and 12 - Timezone boundary behavior and timestamps near midnight`() {
        // Evidence 1: Jan 1, 23:55 (UTC)
        val e1 = buildEvidence("1", EvidenceSourceType.EVENT, 2024, 1, 1, 23, 55, defaultZone)
        // Evidence 2: Jan 2, 00:05 (UTC)
        val e2 = buildEvidence("2", EvidenceSourceType.EVENT, 2024, 1, 2, 0, 5, defaultZone)
        // Evidence 3: Jan 2, 12:00 (UTC)
        val e3 = buildEvidence("3", EvidenceSourceType.EVENT, 2024, 1, 2, 12, 0, defaultZone)
        
        val evidence = listOf(e1, e2, e3)
        
        // In UTC, e1 is Jan 1, e2 and e3 are Jan 2 -> 2 distinct days
        val resultUtc = evaluator.evaluate(evidence, defaultZone)
        assertTrue(resultUtc is EvidenceSufficiencyResult.Sufficient)
        
        // In Tokyo (UTC+9), e1 is Jan 2, 08:55. e2 is Jan 2, 09:05. e3 is Jan 2, 21:00.
        // Therefore, in Tokyo, all events are on Jan 2 -> 1 distinct day
        val resultTokyo = evaluator.evaluate(evidence, tokyoZone)
        assertTrue(resultTokyo is EvidenceSufficiencyResult.Insufficient)
        assertEquals(EvidenceSufficiencyReason.INSUFFICIENT_DISTINCT_CALENDAR_DAYS, (resultTokyo as EvidenceSufficiencyResult.Insufficient).reason)
    }

    @Test
    fun `Test 13 - Different timezones producing correct calendar-day evaluation`() {
        // Evidence 1: Jan 1, 02:00 (UTC)
        val e1 = buildEvidence("1", EvidenceSourceType.EVENT, 2024, 1, 1, 2, 0, defaultZone)
        // Evidence 2: Jan 1, 12:00 (UTC)
        val e2 = buildEvidence("2", EvidenceSourceType.EVENT, 2024, 1, 1, 12, 0, defaultZone)
        // Evidence 3: Jan 1, 23:00 (UTC)
        val e3 = buildEvidence("3", EvidenceSourceType.EVENT, 2024, 1, 1, 23, 0, defaultZone)
        
        val evidence = listOf(e1, e2, e3)
        
        // In UTC, all are Jan 1 -> 1 distinct day
        val resultUtc = evaluator.evaluate(evidence, defaultZone)
        assertTrue(resultUtc is EvidenceSufficiencyResult.Insufficient)
        
        // In New York (UTC-5), e1 is Dec 31, 21:00. e2 is Jan 1, 07:00. e3 is Jan 1, 18:00.
        // -> 2 distinct days (Dec 31, Jan 1)
        val resultNy = evaluator.evaluate(evidence, newYorkZone)
        assertTrue(resultNy is EvidenceSufficiencyResult.Sufficient)
    }

    @Test
    fun `Test 14 and 15 - Source diversity not required, mixed sources work`() {
        val evidence = listOf(
            buildEvidence("1", EvidenceSourceType.EVENT, 2024, 1, 1, 10, 0),
            buildEvidence("2", EvidenceSourceType.OBSERVATION, 2024, 1, 1, 12, 0),
            buildEvidence("3", EvidenceSourceType.REFLECTION, 2024, 1, 2, 10, 0)
        )
        val result = evaluator.evaluate(evidence, defaultZone)
        assertTrue(result is EvidenceSufficiencyResult.Sufficient)
    }

    @Test
    fun `Test 16 - Same-source evidence works`() {
        val evidence = listOf(
            buildEvidence("1", EvidenceSourceType.REFLECTION, 2024, 1, 1, 10, 0),
            buildEvidence("2", EvidenceSourceType.REFLECTION, 2024, 1, 1, 12, 0),
            buildEvidence("3", EvidenceSourceType.REFLECTION, 2024, 1, 2, 10, 0)
        )
        val result = evaluator.evaluate(evidence, defaultZone)
        assertTrue(result is EvidenceSufficiencyResult.Sufficient)
    }

    @Test
    fun `Test 17 - Deterministic repeated evaluation`() {
        val evidence = listOf(
            buildEvidence("1", EvidenceSourceType.EVENT, 2024, 1, 1, 10, 0),
            buildEvidence("2", EvidenceSourceType.EVENT, 2024, 1, 1, 12, 0),
            buildEvidence("3", EvidenceSourceType.EVENT, 2024, 1, 2, 10, 0)
        )
        
        val result1 = evaluator.evaluate(evidence, defaultZone)
        val result2 = evaluator.evaluate(evidence, defaultZone)
        val result3 = evaluator.evaluate(evidence, defaultZone)
        
        assertTrue(result1 is EvidenceSufficiencyResult.Sufficient)
        assertTrue(result2 is EvidenceSufficiencyResult.Sufficient)
        assertTrue(result3 is EvidenceSufficiencyResult.Sufficient)
    }

    @Test
    fun `Test 18, 19, 20, 21 - Architectural boundaries are enforced by type signature`() {
        // Because EvidenceSufficiencyEvaluator.evaluate() does not accept repositories, PatternCandidate, AI inference, or anything other than
        // List<ValidatedEvidence> and ZoneId, and returns only EvidenceSufficiencyResult, it is strictly impossible for it to persist patterns,
        // use AI confidence, or use contradiction signals.
        // This test serves as a documentation marker that the architectural bounds are verified by the compiler.
        val evidence = listOf(
            buildEvidence("1", EvidenceSourceType.EVENT, 2024, 1, 1, 10, 0),
            buildEvidence("2", EvidenceSourceType.EVENT, 2024, 1, 1, 12, 0),
            buildEvidence("3", EvidenceSourceType.EVENT, 2024, 1, 2, 10, 0)
        )
        val result = evaluator.evaluate(evidence, defaultZone)
        assertTrue(result is EvidenceSufficiencyResult.Sufficient)
    }
}
