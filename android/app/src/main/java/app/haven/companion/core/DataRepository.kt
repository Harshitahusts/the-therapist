package app.haven.companion.core

import kotlinx.serialization.encodeToString
import java.util.UUID

/** Where the encrypted document lives. Android: EncryptedSharedPreferences. Tests: memory. */
interface Persistence {
    fun load(): String?
    fun save(json: String)
    fun clear()
}

class MemoryRejected(val reason: String) : IllegalArgumentException(reason)

/**
 * All of Haven's data, as one small document stored encrypted on the phone.
 * Thread-safe; every change is written through immediately.
 */
class DataRepository(private val persistence: Persistence, private val clock: () -> Long = System::currentTimeMillis) {

    @Volatile var data: CompanionData = load()
        private set

    private fun load(): CompanionData = persistence.load()?.let {
        runCatching { AppJson.decodeFromString<CompanionData>(it) }.getOrNull()
    } ?: CompanionData()

    @Synchronized
    private fun update(block: (CompanionData) -> CompanionData): CompanionData {
        data = block(data)
        persistence.save(AppJson.encodeToString(data))
        return data
    }

    // --- profile & settings ---
    fun updateProfile(block: (Profile) -> Profile) = update { it.copy(profile = block(it.profile)) }
    fun updateSettings(block: (CompanionSettings) -> CompanionSettings) = update { it.copy(settings = block(it.settings)) }

    // --- memories ---
    val memoryEnabled: Boolean get() = data.settings.memoryEnabled

    /** Returns (memory, created). Near-duplicates update the existing memory instead. */
    fun saveMemory(content: String, category: String?, confidence: Double = 0.8, source: String = "user"): Pair<Memory, Boolean> {
        val text = MemoryEngine.clean(content)
        if (text.isEmpty()) throw MemoryRejected("empty")
        if (MemoryEngine.looksSensitive(text)) throw MemoryRejected("sensitive")
        var result: Pair<Memory, Boolean>? = null
        update { d ->
            val now = clock()
            val dup = d.memories.maxByOrNull { MemoryEngine.similarity(it.content, text) }
                ?.takeIf { MemoryEngine.similarity(it.content, text) >= DEDUP_SIMILARITY }
            if (dup != null) {
                val updated = dup.copy(
                    content = text,
                    category = MemoryEngine.normalizeCategory(category ?: dup.category),
                    confidence = maxOf(dup.confidence, confidence),
                    updatedAt = now,
                )
                result = updated to false
                d.copy(memories = d.memories.map { if (it.id == dup.id) updated else it })
            } else {
                val m = Memory(
                    id = UUID.randomUUID().toString(),
                    category = MemoryEngine.normalizeCategory(category),
                    content = text,
                    confidence = confidence.coerceIn(0.0, 1.0),
                    source = source,
                    createdAt = now,
                    updatedAt = now,
                )
                result = m to true
                d.copy(memories = d.memories + m)
            }
        }
        return result!!
    }

    fun updateMemory(id: String, content: String): Memory? {
        val text = MemoryEngine.clean(content)
        if (text.isEmpty()) throw MemoryRejected("empty")
        if (MemoryEngine.looksSensitive(text)) throw MemoryRejected("sensitive")
        var out: Memory? = null
        update { d ->
            d.copy(memories = d.memories.map {
                if (it.id == id) it.copy(content = text, updatedAt = clock()).also { m -> out = m } else it
            })
        }
        return out
    }

    fun deleteMemory(id: String): Boolean {
        val exists = data.memories.any { it.id == id }
        if (exists) update { d -> d.copy(memories = d.memories.filterNot { it.id == id }) }
        return exists
    }

    /** "Forget everything about me": memories and conversation summaries. */
    fun forgetEverything() = update { it.copy(memories = emptyList(), summaries = emptyList()) }

    // --- conversations ---
    fun startConversation(): ConversationRecord {
        val c = ConversationRecord(id = UUID.randomUUID().toString(), startedAt = clock())
        update { it.copy(conversations = it.conversations + c) }
        return c
    }

    fun endConversation(id: String): ConversationRecord? {
        var out: ConversationRecord? = null
        update { d ->
            d.copy(conversations = d.conversations.map {
                if (it.id == id && it.endedAt == null) {
                    val now = clock()
                    it.copy(endedAt = now, durationSeconds = (now - it.startedAt) / 1000).also { c -> out = c }
                } else it
            })
        }
        return out
    }

    fun saveSummary(summary: ConversationSummary) = update { d ->
        d.copy(summaries = d.summaries.filterNot { it.conversationId == summary.conversationId } + summary)
    }

    fun recentSummaries(limit: Int = 3): List<ConversationSummary> = data.summaries.sortedByDescending { it.createdAt }.take(limit)

    fun recordSafetyEvent(a: SafetyAssessment) = update {
        it.copy(safetyEvents = it.safetyEvents + SafetyEventRecord(a.level.name, a.categories, clock()))
    }

    // --- privacy ---
    fun exportJson(): String = AppJson.encodeToString(data)

    /** Deletes everything. */
    @Synchronized
    fun wipe() {
        persistence.clear()
        data = CompanionData()
    }

    companion object {
        const val DEDUP_SIMILARITY = 0.8
    }
}
