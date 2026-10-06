package app.haven.companion.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class Profile(
    val name: String? = null,
    val preferred_name: String? = null,
    val timezone: String = "UTC",
    val language: String = "en",
    val preferences: Map<String, String> = emptyMap(),
)

@Serializable
data class UserSettings(
    val memory_enabled: Boolean = true,
    val crisis_region: String? = null,
    val onboarding_completed: Boolean = false,
)

@Serializable
data class Me(
    val id: String,
    val email: String? = null,
    val profile: Profile,
    val settings: UserSettings,
)

@Serializable
data class OnboardingRequest(
    val preferred_name: String,
    val memory_enabled: Boolean,
    val timezone: String?,
    val language: String?,
    val crisis_region: String?,
)

@Serializable
data class SettingsUpdate(val memory_enabled: Boolean? = null, val crisis_region: String? = null)

@Serializable
data class MemoryItem(
    val id: String,
    val category: String,
    val content: String,
    val confidence: Double,
    val source: String,
    val created_at: String,
    val updated_at: String,
)

@Serializable
data class RealtimeSession(
    val conversation_id: String,
    val client_secret: String,
    val expires_at: Long? = null,
    val model: String,
    val calls_url: String,
)

@Serializable
data class CrisisLine(val name: String, val phone: String? = null, val text: String? = null)

@Serializable
data class CrisisResources(
    val region: String = "INTL",
    val emergency: String? = null,
    val lines: List<CrisisLine> = emptyList(),
    val directory: String = "https://findahelpline.com",
)

@Serializable
data class TurnCheck(
    val level: String,
    val categories: List<String> = emptyList(),
    val guidance: String? = null,
    val interrupt: Boolean = false,
    val resources: CrisisResources? = null,
)

@Serializable
data class TranscriptTurn(val role: String, val text: String)

@Serializable
data class EndRequest(val transcript: List<TranscriptTurn>)

@Serializable
data class TurnRequest(val text: String)

@Serializable
data class ToolCallRequest(val arguments: JsonObject)

/** Regions the backend has verified crisis lines for. */
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
