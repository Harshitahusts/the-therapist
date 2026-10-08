package app.haven.companion.core

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WisdomAndVoiceTest {
    @Test
    fun quotesAreUniqueAndAttributed() {
        assertEquals(Quotes.ALL.size, Quotes.ALL.map { it.text }.toSet().size)
        Quotes.ALL.forEach {
            assertTrue(it.author.isNotBlank() && it.source.isNotBlank() && it.themes.isNotEmpty())
            assertTrue("keep quotes short: ${it.text}", it.text.length <= 160)
        }
    }

    @Test
    fun quotesMatchTheMoment() {
        assertEquals("Seneca", Quotes.find("worry about the future").first().author)
        assertTrue(Quotes.find("self-criticism and self-acceptance").any { it.author == "Carl Rogers" })
        assertTrue(Quotes.find("panic, help me breathe").any { it.author == "Thich Nhat Hanh" })
        assertTrue(Quotes.find("zzz").isEmpty())
    }

    @Test
    fun findQuoteToolReturnsVerbatimQuotes() {
        val tools = Tools(DataRepository(InMemoryPersistence()), knowledgeFromRepo())
        val out = tools.execute("find_quote", buildJsonObject { put("theme", "hope in hard times") })
        val q = out["quotes"]!!.jsonArray.first().jsonObject
        val text = q["text"]!!.jsonPrimitive.content
        assertTrue(Quotes.ALL.any { it.text == text && it.author == q["author"]!!.jsonPrimitive.content })
        val none = tools.execute("find_quote", buildJsonObject { put("theme", "qqq") })
        assertTrue("note" in none)
    }

    @Test
    fun promptAsksForSlowSoftSpeechAndVerifiedQuotesOnly() {
        assertTrue(Prompts.VOICE_AGENT.contains("speak slowly and softly"))
        assertTrue(Prompts.VOICE_AGENT.contains("Only use quotations returned by the find_quote tool"))
        val fns = Tools.DECLARATIONS.map { it.jsonObject["name"]!!.jsonPrimitive.content }
        assertTrue("find_quote" in fns)
    }

    @Test
    fun voices() {
        assertEquals("Sulafat", Voices.find(null).name)
        assertEquals("Sulafat", Voices.find("NotAVoice").name)
        assertEquals("Achernar", Voices.find("Achernar").name)
        assertTrue(Voices.CLASSIC_FALLBACK in Voices.CLASSIC)
        assertEquals(Voices.CALM.size, Voices.CALM.map { it.name }.toSet().size)
        val setup = AppJson.parseToJsonElement(LiveCoordinator("m", "i", "Vindemiatrix").setupMessage()).jsonObject["setup"]!!.jsonObject
        val voice = setup["generationConfig"]!!.jsonObject["speechConfig"]!!.jsonObject["voiceConfig"]!!.jsonObject["prebuiltVoiceConfig"]!!
            .jsonObject["voiceName"]!!.jsonPrimitive.content
        assertEquals("Vindemiatrix", voice)
    }

    @Test
    fun ttsModelChoice() {
        fun m(id: String, vararg methods: String) = GeminiModel("models/$id", supportedGenerationMethods = methods.toList())
        assertEquals("gemini-2.5-flash-preview-tts", GeminiApi.chooseTtsModel(listOf(
            m("gemini-2.5-flash", "generateContent"),
            m("gemini-2.5-pro-preview-tts", "generateContent"),
            m("gemini-2.5-flash-preview-tts", "generateContent"),
        )))
        assertEquals(null, GeminiApi.chooseTtsModel(listOf(m("gemini-2.5-flash", "generateContent"))))
    }

    @Test
    fun bookIdeasAreInTheLibrary() {
        val kb = knowledgeFromRepo()
        assertEquals("Key ideas from well-known wellbeing books", kb.retrieve("Viktor Frankl meaning suffering attitude").first().title)
    }
}
