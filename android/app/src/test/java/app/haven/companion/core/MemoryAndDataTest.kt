package app.haven.companion.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryAndDataTest {
    private var now = 1_000L
    private val store = InMemoryPersistence()
    private val repo = DataRepository(store) { now++ }

    @Test
    fun saveListUpdateDelete() {
        val (m, created) = repo.saveMemory("User is building personal AI projects.", "project")
        assertTrue(created)
        assertEquals("project", m.category)
        assertEquals(listOf(m.id), repo.data.memories.map { it.id })
        assertEquals("User is building an Android voice app.", repo.updateMemory(m.id, "User is building an Android voice app.")!!.content)
        assertTrue(repo.deleteMemory(m.id))
        assertFalse(repo.deleteMemory(m.id))
        assertTrue(repo.data.memories.isEmpty())
    }

    @Test
    fun unknownCategoryBecomesOther() {
        assertEquals("other", repo.saveMemory("User likes walking by the sea.", "hobbies").first.category)
    }

    @Test
    fun nearDuplicateUpdatesInsteadOfInserting() {
        repo.saveMemory("User prefers concise practical advice.", "preference")
        val (_, created) = repo.saveMemory("User prefers concise, practical advice", "preference")
        assertFalse(created)
        assertEquals(1, repo.data.memories.size)
    }

    @Test
    fun sensitiveContentIsNeverStored() {
        listOf(
            "User's password is hunter2",
            "User's card number is 4111 1111 1111 1111",
            "User's API key is sk-abcdefghijklmnopqrstuvwxyz",
            "Gemini key AIzaSyA1234567890abcdefghijklmnop",
        ).forEach { text ->
            try {
                repo.saveMemory(text, "other"); error("stored: $text")
            } catch (e: MemoryRejected) {
                assertEquals("sensitive", e.reason)
            }
        }
        assertTrue(repo.data.memories.isEmpty())
    }

    @Test
    fun persistsAcrossRestartsAndSurvivesCorruption() {
        repo.saveMemory("User is training for a 10k run.", "goal")
        repo.updateProfile { it.copy(preferredName = "Harshit") }
        val reopened = DataRepository(store)
        assertEquals("Harshit", reopened.data.profile.preferredName)
        assertEquals(1, reopened.data.memories.size)
        assertEquals(CompanionData(), DataRepository(InMemoryPersistence("{not json")).data)
    }

    @Test
    fun forgetEverythingAndWipe() {
        repo.saveMemory("User is learning to cook.", "goal")
        repo.saveSummary(ConversationSummary("c1", "Talked about cooking.", createdAt = 1))
        repo.forgetEverything()
        assertTrue(repo.data.memories.isEmpty() && repo.data.summaries.isEmpty())
        repo.updateProfile { it.copy(preferredName = "H") }
        repo.wipe()
        assertNull(store.stored)
        assertEquals(CompanionData(), repo.data)
    }

    @Test
    fun conversationsAndSafetyEventsStoreNoText() {
        val c = repo.startConversation()
        repo.recordSafetyEvent(Safety.classify("I want to end my life"))
        val ended = repo.endConversation(c.id)!!
        assertTrue(ended.endedAt != null)
        assertNull("ending twice is a no-op", repo.endConversation(c.id))
        val export = repo.exportJson()
        assertTrue(export.contains("HIGH"))
        assertFalse(export.contains("end my life"))
    }

    @Test
    fun relevantMemoriesRankByOverlapThenRecency() {
        repo.saveMemory("User is starting a new product management role.", "life_event")
        repo.saveMemory("User has a cat called Miso.", "other")
        repo.saveMemory("User wants to run a half marathon.", "goal")
        assertEquals("User is starting a new product management role.",
            MemoryEngine.relevant(repo.data.memories, "worried about the new role").first().content)
        // Nothing relevant: most recent first.
        assertEquals("User wants to run a half marathon.", MemoryEngine.relevant(repo.data.memories, "pizza").first().content)
    }

    @Test
    fun bestMatchDoesNotGuess() {
        repo.saveMemory("User wants to run a half marathon.", "goal")
        assertNull(MemoryEngine.bestMatch(repo.data.memories, "favourite pizza topping"))
        assertEquals("User wants to run a half marathon.", MemoryEngine.bestMatch(repo.data.memories, "the half marathon")!!.content)
    }
}
