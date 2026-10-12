package com.sanket_satpute_20.ironmind.domain.model.intervention

import org.junit.Assert.*
import org.junit.Test

class CorrectionMetadataParserTest {

    @Test
    fun `serialize and parse round trip preserves all CorrectionScope values`() {
        for (scope in CorrectionScope.values()) {
            val serialized = CorrectionMetadataParser.serialize(
                scope = scope,
                type = "BREAK_DOWN",
                recommendation = "Do jumping jacks",
                correctedText = "I have an injury"
            )

            val parsed = CorrectionMetadataParser.parse(serialized)

            assertEquals(scope, parsed.scope)
            assertEquals("BREAK_DOWN", parsed.type)
            assertEquals("Do jumping jacks", parsed.recommendation)
            assertEquals("I have an injury", parsed.correctedText)
        }
    }

    @Test
    fun `serialize and parse preserves delimiter-containing text verbatim`() {
        val complexText = "No, not now; timing = wrong, \"quoted reason\", \nline 2 with\ttab and Unicode: 🎯 📚 🚀"

        val serialized = CorrectionMetadataParser.serialize(
            scope = CorrectionScope.CONSTRAINT,
            type = "RESCHEDULE",
            recommendation = "Do it now, please",
            correctedText = complexText
        )

        val parsed = CorrectionMetadataParser.parse(serialized)

        assertEquals(CorrectionScope.CONSTRAINT, parsed.scope)
        assertEquals("RESCHEDULE", parsed.type)
        assertEquals("Do it now, please", parsed.recommendation)
        assertEquals(complexText, parsed.correctedText)
    }

    @Test
    fun `serialize and parse preserves text with only commas and equals`() {
        val delimiterOnlyText = ",,,===,,,==,,,"

        val serialized = CorrectionMetadataParser.serialize(
            scope = CorrectionScope.INFERENCE_CORRECTION,
            type = "INCENTIVIZE",
            recommendation = "Reward yourself",
            correctedText = delimiterOnlyText
        )

        val parsed = CorrectionMetadataParser.parse(serialized)

        assertEquals(CorrectionScope.INFERENCE_CORRECTION, parsed.scope)
        assertEquals(delimiterOnlyText, parsed.correctedText)
    }

    @Test
    fun `parse legacy metadata without scope defaults to INTERVENTION_CORRECTION`() {
        val legacyMetadata = "type=BREAK_DOWN,recommendation=Do jumping jacks,correctedText=I have a broken leg"

        val parsed = CorrectionMetadataParser.parse(legacyMetadata)

        assertEquals(CorrectionScope.INTERVENTION_CORRECTION, parsed.scope)
        assertEquals("BREAK_DOWN", parsed.type)
        assertEquals("Do jumping jacks", parsed.recommendation)
        assertEquals("I have a broken leg", parsed.correctedText)
    }

    @Test
    fun `parse legacy metadata with trailing keys extracts correctedText properly`() {
        val legacyMetadata = "type=BREAK_DOWN,correctedText=Do it better,other=xyz"

        val parsed = CorrectionMetadataParser.parse(legacyMetadata)

        assertEquals(CorrectionScope.INTERVENTION_CORRECTION, parsed.scope)
        assertEquals("BREAK_DOWN", parsed.type)
        assertNull(parsed.recommendation)
        assertEquals("Do it better", parsed.correctedText)
    }

    @Test
    fun `never retroactively infer broader scope from legacy free text`() {
        val legacyRuleText = "type=BREAK_DOWN,recommendation=Action,correctedText=Never assume postponing means I am avoiding something"
        val parsedRule = CorrectionMetadataParser.parse(legacyRuleText)
        assertEquals(CorrectionScope.INTERVENTION_CORRECTION, parsedRule.scope)
        assertEquals("Never assume postponing means I am avoiding something", parsedRule.correctedText)

        val legacyPrefText = "type=BREAK_DOWN,recommendation=Action,correctedText=I prefer studying at night"
        val parsedPref = CorrectionMetadataParser.parse(legacyPrefText)
        assertEquals(CorrectionScope.INTERVENTION_CORRECTION, parsedPref.scope)
        assertEquals("I prefer studying at night", parsedPref.correctedText)

        val legacyConstraintText = "type=BREAK_DOWN,recommendation=Action,correctedText=I can't study after 8 PM"
        val parsedConstraint = CorrectionMetadataParser.parse(legacyConstraintText)
        assertEquals(CorrectionScope.INTERVENTION_CORRECTION, parsedConstraint.scope)
        assertEquals("I can't study after 8 PM", parsedConstraint.correctedText)
    }

    @Test
    fun `parse unknown scope string safely degrades to INTERVENTION_CORRECTION without throwing`() {
        val unknownScopeMetadata = "scope=FUTURE_UNSEEN_SCOPE,type=BREAK_DOWN,recommendation=Rec,correctedText=Text"

        val parsed = CorrectionMetadataParser.parse(unknownScopeMetadata)

        assertEquals(CorrectionScope.INTERVENTION_CORRECTION, parsed.scope)
        assertEquals("BREAK_DOWN", parsed.type)
        assertEquals("Rec", parsed.recommendation)
        assertEquals("Text", parsed.correctedText)
    }

    @Test
    fun `parse malformed scope string safely degrades to INTERVENTION_CORRECTION without throwing`() {
        val malformedScopeMetadata = "scope=123!@#$,type=BREAK_DOWN,correctedText=Text"

        val parsed = CorrectionMetadataParser.parse(malformedScopeMetadata)

        assertEquals(CorrectionScope.INTERVENTION_CORRECTION, parsed.scope)
        assertEquals("BREAK_DOWN", parsed.type)
        assertEquals("Text", parsed.correctedText)
    }

    @Test
    fun `parse malformed metadata string returns safe defaults without throwing`() {
        val malformed1 = "invalidMetadataStringWithoutKeyValues"
        val parsed1 = CorrectionMetadataParser.parse(malformed1)
        assertEquals(CorrectionScope.INTERVENTION_CORRECTION, parsed1.scope)
        assertNull(parsed1.type)
        assertNull(parsed1.recommendation)
        assertNull(parsed1.correctedText)

        val malformed2 = "====,,,,===="
        val parsed2 = CorrectionMetadataParser.parse(malformed2)
        assertEquals(CorrectionScope.INTERVENTION_CORRECTION, parsed2.scope)

        val malformed3 = "type=BREAK_DOWN=EXTRA_EQUALS"
        val parsed3 = CorrectionMetadataParser.parse(malformed3)
        assertEquals(CorrectionScope.INTERVENTION_CORRECTION, parsed3.scope)
    }

    @Test
    fun `parse null or blank metadata returns safe defaults`() {
        val parsedNull = CorrectionMetadataParser.parse(null)
        assertEquals(CorrectionScope.INTERVENTION_CORRECTION, parsedNull.scope)
        assertNull(parsedNull.type)
        assertNull(parsedNull.recommendation)
        assertNull(parsedNull.correctedText)

        val parsedEmpty = CorrectionMetadataParser.parse("")
        assertEquals(CorrectionScope.INTERVENTION_CORRECTION, parsedEmpty.scope)
        assertNull(parsedEmpty.type)

        val parsedBlank = CorrectionMetadataParser.parse("   ")
        assertEquals(CorrectionScope.INTERVENTION_CORRECTION, parsedBlank.scope)
        assertNull(parsedBlank.type)
    }

    @Test
    fun `recommendation containing commas does not corrupt recommendation or type or text`() {
        val metadata = "scope=PREFERENCE,type=BREAK_DOWN,recommendation=Take a break, walk for 5 minutes, get water,correctedText=Sounds good, will do"

        val parsed = CorrectionMetadataParser.parse(metadata)

        assertEquals(CorrectionScope.PREFERENCE, parsed.scope)
        assertEquals("BREAK_DOWN", parsed.type)
        assertEquals("Take a break, walk for 5 minutes, get water", parsed.recommendation)
        assertEquals("Sounds good, will do", parsed.correctedText)
    }

    @Test
    fun `recommendation containing literal correctedText= is parsed correctly without truncation`() {
        val metadata = "scope=PREFERENCE,type=BREAK_DOWN,recommendation=Review correctedText= setting in config,correctedText=Done"

        val parsed = CorrectionMetadataParser.parse(metadata)

        assertEquals(CorrectionScope.PREFERENCE, parsed.scope)
        assertEquals("BREAK_DOWN", parsed.type)
        assertEquals("Review correctedText= setting in config", parsed.recommendation)
        assertEquals("Done", parsed.correctedText)
    }

    @Test
    fun `correction text containing literal correctedText= remains intact`() {
        val metadata = "scope=CONSTRAINT,type=BREAK_DOWN,recommendation=Take a break,correctedText=Why is correctedText= shown here?"

        val parsed = CorrectionMetadataParser.parse(metadata)

        assertEquals(CorrectionScope.CONSTRAINT, parsed.scope)
        assertEquals("BREAK_DOWN", parsed.type)
        assertEquals("Take a break", parsed.recommendation)
        assertEquals("Why is correctedText= shown here?", parsed.correctedText)
    }

    @Test
    fun `both recommendation and correction text containing correctedText= and delimiters remain intact`() {
        val complexRec = "Do not use correctedText=, please"
        val complexText = "I have correctedText= here, with = sign and ,correctedText= inside \n 😊"
        val serialized = CorrectionMetadataParser.serialize(
            scope = CorrectionScope.EXPLICIT_GENERAL_RULE,
            type = "BREAK_DOWN",
            recommendation = complexRec,
            correctedText = complexText
        )

        val parsed = CorrectionMetadataParser.parse(serialized)

        assertEquals(CorrectionScope.EXPLICIT_GENERAL_RULE, parsed.scope)
        assertEquals("BREAK_DOWN", parsed.type)
        assertEquals(complexRec, parsed.recommendation)
        assertEquals(complexText, parsed.correctedText)
    }

    @Test
    fun `legacy metadata with recommendation containing correctedText= parses correctly`() {
        val metadata = "type=BREAK_DOWN,recommendation=Check correctedText= field,correctedText=Legacy text"

        val parsed = CorrectionMetadataParser.parse(metadata)

        assertEquals(CorrectionScope.INTERVENTION_CORRECTION, parsed.scope)
        assertEquals("BREAK_DOWN", parsed.type)
        assertEquals("Check correctedText= field", parsed.recommendation)
        assertEquals("Legacy text", parsed.correctedText)
    }
}
