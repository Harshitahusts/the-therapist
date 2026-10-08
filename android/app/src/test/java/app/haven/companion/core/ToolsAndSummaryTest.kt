package app.haven.companion.core

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolsAndSummaryTest {
    private val repo = DataRepository(InMemoryPersistence())
    private val tools = Tools(repo, knowledgeFromRepo())
    private fun args(vararg kv: Pair<String, String>) = buildJsonObject { kv.forEach { (k, v) -> put(k, v) } }
    private fun JsonObject.str(k: String) = this[k]!!.jsonPrimitive.content

    @Test
    fun memoryToolsRoundTrip() {
        assertEquals("true", tools.execute("save_memory", args("category" to "life_event",
            "content" to "User is starting a new product management role.")).str("saved"))
        val found = tools.execute("get_relevant_memories", args("query" to "new product management role"))["memories"]!!.jsonArray
        assertEquals("User is starting a new product management role.", found[0].jsonObject.str("content"))
        assertEquals("true", tools.execute("update_memory", args("about" to "product management role",
            "new_content" to "User started a new product management role in October.")).str("ok"))
        assertEquals("true", tools.execute("delete_memory", args("about" to "new product management role")).str("ok"))
        assertTrue(repo.data.memories.isEmpty())
    }

    @Test
    fun saveRespectsMemoryOffAndSensitiveData() {
        repo.updateSettings { it.copy(memoryEnabled = false) }
        assertEquals("false", tools.execute("save_memory", args("category" to "goal", "content" to "User wants to learn guitar.")).str("saved"))
        repo.updateSettings { it.copy(memoryEnabled = true) }
        assertEquals("false", tools.execute("save_memory", args("category" to "other", "content" to "User's bank password is abc123")).str("saved"))
        assertTrue(repo.data.memories.isEmpty())
    }

    @Test
    fun knowledgeProfileAndUnknownTools() {
        repo.updateProfile { it.copy(preferredName = "Harshit") }
        assertEquals("Harshit", tools.execute("get_user_profile", JsonObject(emptyMap())).str("preferred_name"))
        val passages = tools.execute("retrieve_knowledge", args("query" to "can't sleep, racing mind at night"))["passages"]!!.jsonArray
        assertEquals("Sleep habits", passages[0].jsonObject.str("source"))
        assertTrue("error" in tools.execute("drop_tables", JsonObject(emptyMap())))
    }

    private fun summarizer(response: String, calls: MutableList<String> = mutableListOf()) =
        Summarizer(repo) { _, user -> calls += user; AppJson.parseToJsonElement(response).jsonObject }

    @Test
    fun summaryAndExtractedMemoriesAreSavedAndSensitiveOnesDropped() {
        val calls = mutableListOf<String>()
        val s = summarizer("""{"summary":"User felt anxious about a new job.","important_points":["New job Monday"],
            "emotional_context":"anxious","follow_up":"Ask how the first week went.",
            "memories":[{"category":"life_event","content":"User is starting a new PM role.","confidence":0.9},
                        {"category":"other","content":"User's password is swordfish","confidence":0.9},
                        {"category":"other","content":"User might like jazz.","confidence":0.2}]}""", calls)
        val r = s.summarize("c1", listOf(TranscriptTurn("assistant", "How are you?"), TranscriptTurn("user", "Nervous about Monday.")))
        assertEquals(Summarizer.Result(true, 1), r)
        assertTrue(calls.single().contains("USER: Nervous about Monday."))
        assertEquals("Ask how the first week went.", repo.data.summaries.single().followUp)
        assertEquals(listOf("User is starting a new PM role."), repo.data.memories.map { it.content })
    }

    @Test
    fun nothingIsStoredWhenMemoryIsOffOrTheUserNeverSpoke() {
        val calls = mutableListOf<String>()
        repo.updateSettings { it.copy(memoryEnabled = false) }
        assertEquals(Summarizer.Result(false, 0), summarizer("""{"summary":"x"}""", calls).summarize("c", listOf(TranscriptTurn("user", "hi"))))
        repo.updateSettings { it.copy(memoryEnabled = true) }
        assertEquals(Summarizer.Result(false, 0), summarizer("""{"summary":"x"}""", calls).summarize("c", listOf(TranscriptTurn("assistant", "hi"))))
        assertTrue(calls.isEmpty())
        assertFalse(repo.exportJson().contains("hi"))
    }

    @Test
    fun modelChoice() {
        fun m(id: String, vararg methods: String) = GeminiModel("models/$id", supportedGenerationMethods = methods.toList())
        val models = listOf(
            m("gemini-2.5-flash", "generateContent"),
            m("gemini-2.5-flash-lite", "generateContent"),
            m("gemini-2.5-flash-preview-tts", "generateContent"),
            m("gemini-2.5-flash-native-audio-preview-12-2025", "bidiGenerateContent"),
            m("gemini-3.1-flash-live-preview", "bidiGenerateContent"),
            m("gemini-3.8-live-extended-thinking", "bidiGenerateContent"),
            m("text-embedding-004", "embedContent"),
        )
        assertEquals("gemini-3.1-flash-live-preview", GeminiApi.chooseLiveModel(models))
        // The real model list from a free key: skip transcription, robotics, translation and thinking variants.
        val real = listOf(
            "gemini-3.5-transcribe-live", "gemini-2.5-flash-native-audio-latest", "gemini-2.5-flash-native-audio-preview-09-2025",
            "gemini-2.5-flash-native-audio-preview-12-2025", "gemini-3.1-flash-live-preview", "gemini-3.8-live",
            "gemini-3.8-live-extended-thinking", "gemini-robotics-er-2-streaming-preview", "gemini-3.5-live-translate-preview",
        ).map { m(it, "bidiGenerateContent") }
        val ranked = GeminiApi.rankLiveModels(real)
        assertEquals("gemini-3.8-live", ranked.first())
        assertEquals("gemini-3.1-flash-live-preview", ranked[1])
        assertTrue(ranked.none { "transcribe" in it || "robotics" in it || "translate" in it || "thinking" in it })
        assertEquals("gemini-2.5-flash", GeminiApi.chooseTextModel(models))
        assertEquals(null, GeminiApi.chooseLiveModel(models.filter { "bidi" !in it.supportedGenerationMethods.joinToString() }))
        assertEquals("That Gemini API key isn't valid.", GeminiApi.describeError(400, """{"reason":"API_KEY_INVALID"}"""))
    }
}
