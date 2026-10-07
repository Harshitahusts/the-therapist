package app.haven.companion.core

/** Pure helpers for deciding what may be remembered and finding relevant memories. */
object MemoryEngine {
    const val MAX_MEMORY_CHARS = 500

    private val SENSITIVE = listOf(
        Regex("""\b(password|passcode|passwd|pin code|pin number|my pin|otp|one[- ]time code)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(api[ _-]?key|secret key|access token|bearer token|private key)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bsk-[A-Za-z0-9_-]{10,}|\bgh[pousr]_[A-Za-z0-9]{20,}|\bAKIA[0-9A-Z]{16}\b|\bAIza[0-9A-Za-z_-]{20,}"""),
        Regex("""\b(?:\d[ -]?){13,19}\b"""), // card-like / long account numbers
        Regex("""\b(cvv|cvc|card number|iban|routing number|account number|sort code)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(ssn|social security number|aadhaar|passport number|pan card number)\b""", RegexOption.IGNORE_CASE),
    )

    fun looksSensitive(text: String): Boolean = SENSITIVE.any { it.containsMatchIn(text) }

    fun normalizeCategory(category: String?): String {
        val c = (category ?: "other").trim().lowercase().replace(' ', '_')
        return if (c in MEMORY_CATEGORIES) c else "other"
    }

    fun clean(content: String): String = content.split(Regex("""\s+""")).filter { it.isNotEmpty() }
        .joinToString(" ").take(MAX_MEMORY_CHARS)

    private val STOPWORDS = setOf(
        "a", "an", "the", "and", "or", "but", "of", "to", "in", "on", "at", "for", "with", "about", "is", "are", "was",
        "were", "be", "been", "it", "its", "this", "that", "their", "they", "them", "he", "she", "his", "her", "i", "me",
        "my", "you", "your", "user", "user's", "users", "has", "have", "had", "do", "does", "did", "as", "by", "from",
        "into", "so", "very", "really", "just", "what", "said", "again", "who", "s",
    )

    fun tokens(text: String): Set<String> = Regex("""[a-z0-9']+""").findAll(text.lowercase())
        .map { it.value.trim('\'') }
        .map { stem(it) }
        .filter { it.length > 1 && it !in STOPWORDS }
        .toSet()

    /** Very light stemming so "worries"/"worried"/"worry" and "jobs"/"job" meet. */
    private fun stem(w: String): String = when {
        w.length > 5 && w.endsWith("ies") -> w.dropLast(3) + "y"
        w.length > 5 && w.endsWith("ied") -> w.dropLast(3) + "y"
        w.length > 5 && w.endsWith("ing") -> w.dropLast(3)
        w.length > 4 && w.endsWith("ed") -> w.dropLast(2)
        w.length > 3 && w.endsWith("s") && !w.endsWith("ss") -> w.dropLast(1)
        else -> w
    }

    /** Jaccard similarity of content words; 1.0 for the same fact phrased with different punctuation. */
    fun similarity(a: String, b: String): Double {
        val ta = tokens(a)
        val tb = tokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        return ta.intersect(tb).size.toDouble() / ta.union(tb).size
    }

    /** Fraction of the query's content words found in the text. */
    fun overlap(query: String, text: String): Double {
        val q = tokens(query)
        if (q.isEmpty()) return 0.0
        return q.intersect(tokens(text)).size.toDouble() / q.size
    }

    fun relevant(memories: List<Memory>, query: String?, limit: Int = 6, minScore: Double = 0.2): List<Memory> {
        if (!query.isNullOrBlank()) {
            val hits = memories.map { it to overlap(query, it.content) }
                .filter { it.second >= minScore }
                .sortedWith(compareByDescending<Pair<Memory, Double>> { it.second }.thenByDescending { it.first.updatedAt })
                .take(limit)
                .map { it.first }
            if (hits.isNotEmpty()) return hits
        }
        return memories.sortedByDescending { it.updatedAt }.take(limit)
    }

    /** Best match for "forget/update the thing about X", or null if nothing is clearly about X. */
    fun bestMatch(memories: List<Memory>, about: String, threshold: Double = 0.5): Memory? =
        memories.map { it to overlap(about, it.content) }
            .filter { it.second >= threshold }
            .maxByOrNull { it.second }?.first
}
