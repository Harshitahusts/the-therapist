package app.haven.companion.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * End of conversation: write a compact summary and extract a few durable
 * memories. The transcript is used once here and never stored.
 */
class Summarizer(private val repo: DataRepository, private val generate: (system: String, user: String) -> JsonObject) {

    data class Result(val summarySaved: Boolean, val memoriesSaved: Int)

    fun summarize(conversationId: String, transcript: List<TranscriptTurn>, maxChars: Int = 60_000): Result {
        if (!repo.memoryEnabled) return Result(false, 0)
        val lines = transcript.mapNotNull { t ->
            val text = t.text.split(Regex("""\s+""")).filter { it.isNotEmpty() }.joinToString(" ")
            if (text.isEmpty()) null else "${if (t.role == "user") "USER" else "COMPANION"}: $text"
        }
        if (lines.none { it.startsWith("USER: ") }) return Result(false, 0)
        val body = lines.joinToString("\n").takeLast(maxChars)
        val existing = repo.data.memories.take(40).joinToString("\n") { "- ${it.content}" }.ifEmpty { "(none)" }
        val data = generate(Prompts.SUMMARY, "EXISTING MEMORIES:\n$existing\n\nCONVERSATION:\n$body")

        fun str(key: String) = (data[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

        var summarySaved = false
        val summary = str("summary")
        if (summary != null && !MemoryEngine.looksSensitive(summary)) {
            val points = (data["important_points"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.take(300) }
                .filterNot { MemoryEngine.looksSensitive(it) }
                .take(5)
            repo.saveSummary(
                ConversationSummary(
                    conversationId = conversationId,
                    summary = summary.take(2000),
                    importantPoints = points,
                    emotionalContext = str("emotional_context")?.take(200),
                    followUp = str("follow_up")?.take(500),
                    createdAt = System.currentTimeMillis(),
                )
            )
            summarySaved = true
        }

        var saved = 0
        for (item in (data["memories"] as? JsonArray).orEmpty().take(5)) {
            val obj = item as? JsonObject ?: continue
            val content = (obj["content"] as? JsonPrimitive)?.contentOrNull ?: continue
            val confidence = (obj["confidence"] as? JsonPrimitive)?.doubleOrNull ?: 0.7
            if (confidence < 0.5) continue
            try {
                repo.saveMemory(content, (obj["category"] as? JsonPrimitive)?.contentOrNull, confidence, "summary_extraction")
                saved++
            } catch (_: MemoryRejected) {
            }
        }
        return Result(summarySaved, saved)
    }
}
