package app.haven.companion.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeTest {
    private val kb = knowledgeFromRepo()

    @Test
    fun everySourceHasLicenceAndIsListedInLicensesMd() {
        val licenses = repoFile("LICENSES.md").readText()
        assertEquals(13, kb.documents.size)
        kb.documents.forEach { d ->
            assertTrue(d.meta["license"]!!.isNotBlank())
            assertTrue(d.meta["redistribution_allowed"] in setOf("true", "false"))
            assertTrue("${d.slug}.md missing from LICENSES.md", licenses.contains("`${d.slug}.md`"))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingLicenceIsRejected() {
        KnowledgeBase.parse("x.md", "---\ntitle: X\nauthor: Y\n---\nBody")
    }

    @Test
    fun chunkingRespectsHeadingsAndSize() {
        val body = "# A\n\n" + List(10) { "word ".repeat(60) }.joinToString("\n\n") + "\n\n## B\n\nshort"
        val chunks = KnowledgeBase.chunkMarkdown(body, maxChars = 700)
        assertTrue(chunks.all { it.second.length <= 700 })
        assertEquals("B" to "short", chunks.last())
        assertEquals(setOf("A", "B"), chunks.map { it.first }.toSet())
        assertTrue(kb.chunkCount > kb.documents.size)
    }

    @Test
    fun retrievalFindsTheRightSource() {
        assertEquals("Grounding and breathing for anxiety and panic",
            kb.retrieve("slow breathing and grounding during a panic attack").first().title)
        assertEquals("Sleep habits", kb.retrieve("I can't sleep, lying awake at night with a racing mind").first().title)
        assertEquals("Behavioural activation", kb.retrieve("no motivation, stopped doing things, low mood withdrawal").first().title)
        assertEquals("Loneliness and connection", kb.retrieve("lonely after moving to a new city").first().title)
        assertTrue(kb.retrieve("the and of").isEmpty())
    }
}
