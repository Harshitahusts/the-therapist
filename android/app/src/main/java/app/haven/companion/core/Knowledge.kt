package app.haven.companion.core

import kotlin.math.ln

/**
 * Retrieval over the bundled psychoeducation library (knowledge/sources, CC BY 4.0,
 * shipped inside the APK). BM25 keyword ranking: offline, free, and good enough
 * for a few dozen short documents.
 */
class KnowledgeBase(sources: List<Pair<String, String>>) {

    data class SourceDoc(val slug: String, val meta: Map<String, String>, val body: String)
    data class Chunk(val doc: SourceDoc, val section: String?, val content: String, val terms: List<String>)
    data class Passage(val title: String, val section: String?, val content: String, val license: String, val score: Double)

    val documents: List<SourceDoc> = sources.filter { !it.first.equals("README.md", true) }.map { (name, text) -> parse(name, text) }
    private val chunks: List<Chunk> = documents.flatMap { doc ->
        chunkMarkdown(doc.body).map { (section, text) ->
            Chunk(doc, section, text, terms("${doc.meta["title"]} ${section.orEmpty()} $text"))
        }
    }
    private val avgLen = chunks.map { it.terms.size }.average().takeIf { !it.isNaN() } ?: 1.0
    private val df: Map<String, Int> = chunks.flatMap { it.terms.toSet() }.groupingBy { it }.eachCount()

    val chunkCount: Int get() = chunks.size

    fun retrieve(query: String, k: Int = 3): List<Passage> {
        val q = terms(query).toSet()
        if (q.isEmpty()) return emptyList()
        val n = chunks.size.toDouble()
        return chunks.map { c ->
            val tf = c.terms.groupingBy { it }.eachCount()
            var score = 0.0
            for (t in q) {
                val f = tf[t] ?: continue
                val idf = ln(1 + (n - (df[t] ?: 0) + 0.5) / ((df[t] ?: 0) + 0.5))
                score += idf * (f * (K1 + 1)) / (f + K1 * (1 - B + B * c.terms.size / avgLen))
            }
            c to score
        }.filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(k)
            .map { (c, s) -> Passage(c.doc.meta.getValue("title"), c.section, c.content, c.doc.meta.getValue("license"), s) }
    }

    companion object {
        private const val K1 = 1.4
        private const val B = 0.75
        val REQUIRED_META = listOf("title", "author", "license")

        fun terms(text: String): List<String> = Regex("""[a-z0-9]+""").findAll(text.lowercase())
            .map { it.value }
            .filter { it.length > 2 && it !in STOP }
            .map { if (it.length > 4 && it.endsWith("s") && !it.endsWith("ss")) it.dropLast(1) else it }
            .toList()

        private val STOP = setOf(
            "the", "and", "for", "are", "but", "not", "you", "your", "with", "that", "this", "can", "what", "how", "when",
            "they", "them", "about", "from", "have", "has", "was", "were", "will", "would", "into", "than", "then", "there",
            "their", "our", "out", "its", "it's", "also", "just", "like", "some", "more", "most", "very", "being", "been",
            "i'm", "feel", "feeling",
        )

        fun parse(fileName: String, text: String): SourceDoc {
            val meta = mutableMapOf<String, String>()
            var body = text
            if (text.startsWith("---")) {
                val parts = text.split("---", limit = 3)
                for (line in parts[1].trim().lines()) {
                    val i = line.indexOf(':')
                    if (i > 0) meta[line.substring(0, i).trim()] = line.substring(i + 1).trim().trim('"')
                }
                body = parts[2]
            }
            val missing = REQUIRED_META.filter { meta[it].isNullOrBlank() }
            require(missing.isEmpty()) { "$fileName: missing front-matter fields $missing" }
            return SourceDoc(fileName.removeSuffix(".md"), meta, body.trim())
        }

        /** Split on headings, then pack paragraphs into chunks of at most maxChars. */
        fun chunkMarkdown(body: String, maxChars: Int = 1200): List<Pair<String?, String>> {
            val sections = mutableListOf<Pair<String?, MutableList<String>>>(null to mutableListOf())
            for (raw in body.split(Regex("""\n\s*\n"""))) {
                val block = raw.trim()
                if (block.isEmpty()) continue
                val lines = block.lines()
                val heading = Regex("""^#{1,6}\s+(.*)$""").find(lines[0])
                if (heading != null) {
                    sections += heading.groupValues[1].trim() to mutableListOf()
                    val rest = lines.drop(1).joinToString("\n").trim()
                    if (rest.isNotEmpty()) sections.last().second += rest
                } else {
                    sections.last().second += block
                }
            }
            val out = mutableListOf<Pair<String?, String>>()
            for ((title, paras) in sections) {
                var buf = ""
                for (p in paras) {
                    val pieces = if (p.length > maxChars) p.split(Regex("""(?<=[.!?])\s+""")) else listOf(p)
                    for (piece in pieces) {
                        if (buf.isNotEmpty() && buf.length + piece.length + 2 > maxChars) {
                            out += title to buf
                            buf = ""
                        }
                        buf = if (buf.isEmpty()) piece else "$buf\n\n$piece"
                    }
                }
                if (buf.isNotEmpty()) out += title to buf
            }
            return out
        }
    }
}
