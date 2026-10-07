package app.haven.companion.core

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyTest {
    private val cases = repoFile("eval/safety_cases.jsonl").readLines().filter { it.isNotBlank() }
        .map { AppJson.parseToJsonElement(it).jsonObject }

    @Test
    fun evalDataset() {
        val failures = cases.mapNotNull { c ->
            val text = c["text"]!!.jsonPrimitive.content
            val expected = c["expected"]!!.jsonPrimitive.content
            val got = Safety.classify(text).level.name
            if (got != expected) "${c["id"]!!.jsonPrimitive.content}: expected $expected, got $got — \"$text\"" else null
        }
        assertTrue("Safety eval failures:\n" + failures.joinToString("\n"), failures.isEmpty())
        assertTrue(cases.size >= 57)
    }

    @Test
    fun datasetCoversRequiredCategories() {
        val cats = cases.map { it["category"]!!.jsonPrimitive.content }.toSet()
        listOf("normal", "stress", "work_anxiety", "loneliness", "anger", "sadness", "motivation", "relationship", "sleep",
            "panic", "self_harm", "false_positive", "joke").forEach { assertTrue(it, it in cats) }
    }

    @Test
    fun guidanceNeverEncouragesDependencyOrPromises() {
        for (level in RiskLevel.entries) {
            val g = Safety.guidanceFor(SafetyAssessment(level), "GB").orEmpty().lowercase()
            assertFalse(g.contains("only need me"))
            assertFalse(g.contains("promise me"))
            if (level >= RiskLevel.HIGH) assertTrue(g.contains("116 123") && g.contains("999"))
        }
    }

    @Test
    fun regionalResources() {
        assertEquals("112", Safety.resourcesFor("in").emergency)
        assertEquals("14416", Safety.resourcesFor("IN").lines.single().phone)
        assertEquals("INTL", Safety.resourcesFor("ZZ").region)
        assertTrue(Safety.guidanceFor(SafetyAssessment(RiskLevel.IMMEDIATE), null)!!.contains("local emergency number"))
    }

    @Test
    fun imminentRiskIsNotDowngradedByPastTense() {
        assertEquals(RiskLevel.IMMEDIATE, Safety.classify("I used to cope but I'm going to kill myself tonight").level)
    }

    @Test
    fun lowRiskHasNoGuidance() {
        assertEquals(null, Safety.guidanceFor(Safety.classify("Work was stressful today"), "IN"))
    }
}
