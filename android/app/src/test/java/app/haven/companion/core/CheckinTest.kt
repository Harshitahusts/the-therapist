package app.haven.companion.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckinTest {
    @Test
    fun tenUniqueQuestionsEachWithItsOwnNote() {
        assertEquals(10, Checkin.QUESTIONS.size)
        assertEquals(10, Checkin.QUESTIONS.map { it.id }.toSet().size)
        assertEquals(10, Checkin.QUESTIONS.map { it.note }.toSet().size)
        assertEquals(10, Checkin.QUESTIONS.map { it.noteTitle }.toSet().size)
        assertEquals(listOf("Often", "Sometimes", "Rarely", "Never"), Checkin.OPTIONS)
    }

    @Test
    fun shortCheckinsStillCoverDifferentTopics() {
        assertEquals(3, Checkin.questionsFor(3).map { it.topic }.toSet().size)
        assertTrue(Checkin.questionsFor(5).map { it.topic }.toSet().size >= 4)
        assertEquals(10, Checkin.questionsFor(99).size)
    }

    @Test
    fun scoringRespectsDirection() {
        val joy = Checkin.QUESTIONS.first { it.id == "joy" }          // often is good
        val worry = Checkin.QUESTIONS.first { it.id == "future" }     // never is good
        assertEquals(3, Checkin.points(joy, 0))
        assertEquals(0, Checkin.points(joy, 3))
        assertEquals(0, Checkin.points(worry, 0))
        assertEquals(3, Checkin.points(worry, 3))
    }

    @Test
    fun wellBeingCount() {
        assertEquals(100, Checkin.score(mapOf("joy" to 0, "future" to 3, "sleep" to 0)))
        assertEquals(0, Checkin.score(mapOf("joy" to 3, "future" to 0)))
        assertEquals(50, Checkin.score(mapOf("joy" to 0, "future" to 0)))
        assertEquals(67, Checkin.score(mapOf("joy" to 1)))
        assertEquals(0, Checkin.score(emptyMap()))
        assertEquals(0, Checkin.score(mapOf("unknown" to 1)))
    }

    @Test
    fun bands() {
        assertEquals("Blooming", Checkin.band(80).name)
        assertEquals("Growing", Checkin.band(79).name)
        assertEquals("Sprouting", Checkin.band(40).name)
        assertEquals("Needs gentle care", Checkin.band(39).name)
    }

    @Test
    fun recordedCheckReachesTheCompanionAndIsForgettable() {
        val repo = DataRepository(InMemoryPersistence())
        val check = Checkin.record(mapOf("sleep" to 2, "joy" to 0), now = 1_000)
        assertEquals(listOf("joy", "sleep"), check.answers.map { it.questionId })
        assertEquals("Rarely", check.answers.last().answer)
        repo.saveCheck(check)
        val now = java.time.ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(2_000), java.time.ZoneOffset.UTC)
        val prompt = LiveCoordinator.buildInstructions(repo.data, now)
        assertTrue(prompt.contains("Well-Being Count ${check.score}/100"))
        assertTrue(prompt.contains("How often do you wake up feeling rested? Rarely"))
        repo.forgetEverything()
        assertTrue(repo.data.checks.isEmpty())
    }

    @Test
    fun oldCheckinsAreNotMentioned() {
        val data = CompanionData(checks = listOf(Checkin.record(mapOf("joy" to 0), now = 0)))
        val tenDaysLater = java.time.ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(10L * 86_400_000), java.time.ZoneOffset.UTC)
        assertTrue(!LiveCoordinator.buildInstructions(data, tenDaysLater).contains("Well-Being Count"))
    }
}
