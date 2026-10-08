package app.haven.companion.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val AppJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

@Serializable
data class Profile(
    val name: String? = null,
    val preferredName: String? = null,
    val timezone: String = "UTC",
    val language: String = "en",
    val preferences: Map<String, String> = emptyMap(),
)

@Serializable
data class CompanionSettings(
    val memoryEnabled: Boolean = true,
    val crisisRegion: String = "INTL",
)

@Serializable
data class Memory(
    val id: String,
    val category: String,
    val content: String,
    val confidence: Double,
    /** "voice_tool" | "summary_extraction" | "user" */
    val source: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class ConversationSummary(
    val conversationId: String,
    val summary: String,
    val importantPoints: List<String> = emptyList(),
    val emotionalContext: String? = null,
    val followUp: String? = null,
    val createdAt: Long,
)

@Serializable
data class ConversationRecord(
    val id: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val durationSeconds: Long? = null,
)

/** Records that a risk level was detected. Never what the user said. */
@Serializable
data class SafetyEventRecord(
    val level: String,
    val categories: List<String>,
    val createdAt: Long,
)

/** Everything Haven stores, kept encrypted on the phone. */
@Serializable
data class CompanionData(
    val version: Int = 1,
    val profile: Profile = Profile(),
    val settings: CompanionSettings = CompanionSettings(),
    val memories: List<Memory> = emptyList(),
    val summaries: List<ConversationSummary> = emptyList(),
    val conversations: List<ConversationRecord> = emptyList(),
    val safetyEvents: List<SafetyEventRecord> = emptyList(),
    /** Wellbeing check-ins, oldest first. */
    val checks: List<WellbeingCheck> = emptyList(),
)

@Serializable
data class TranscriptTurn(val role: String, val text: String)

@Serializable
data class CrisisLine(val name: String, val phone: String? = null, val text: String? = null)

@Serializable
data class CrisisResources(
    val region: String = "INTL",
    val emergency: String? = null,
    val lines: List<CrisisLine> = emptyList(),
    val directory: String = "https://findahelpline.com",
)

val MEMORY_CATEGORIES = listOf(
    "goal", "project", "recurring_worry", "preference", "life_event", "plan", "relationship", "wellbeing", "other",
)

/** Regions with verified crisis lines. */
val SUPPORTED_CRISIS_REGIONS = linkedMapOf(
    "INTL" to "Other / international",
    "IN" to "India",
    "US" to "United States",
    "GB" to "United Kingdom",
    "CA" to "Canada",
    "IE" to "Ireland",
    "AU" to "Australia",
    "NZ" to "New Zealand",
)
