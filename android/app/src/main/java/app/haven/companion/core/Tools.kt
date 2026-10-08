package app.haven.companion.core

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.Instant
import java.time.ZoneOffset

/** The companion's function tools: declarations for Gemini, and their on-device implementation. */
class Tools(private val repo: DataRepository, private val knowledge: KnowledgeBase) {

    fun execute(name: String, args: JsonObject): JsonObject {
        fun arg(key: String, max: Int = 500) = ((args[key] as? JsonPrimitive)?.contentOrNull ?: "").trim().take(max)
        fun date(ms: Long) = Instant.ofEpochMilli(ms).atOffset(ZoneOffset.UTC).toLocalDate().toString()
        val memoryOn = repo.memoryEnabled

        return when (name) {
            "get_user_profile" -> repo.data.profile.let { p ->
                buildJsonObject {
                    put("name", p.name); put("preferred_name", p.preferredName)
                    put("timezone", p.timezone); put("language", p.language)
                    putJsonObject("preferences") { p.preferences.forEach { (k, v) -> put(k, v) } }
                }
            }
            "get_relevant_memories" -> if (!memoryOn) off("memories") else buildJsonObject {
                putJsonArray("memories") {
                    MemoryEngine.relevant(repo.data.memories, arg("query")).forEach { m ->
                        add(buildJsonObject { put("category", m.category); put("content", m.content); put("remembered_on", date(m.createdAt)) })
                    }
                }
            }
            "get_recent_conversation_summaries" -> if (!memoryOn) off("summaries") else buildJsonObject {
                putJsonArray("summaries") {
                    repo.recentSummaries(5).forEach { s ->
                        add(buildJsonObject {
                            put("date", date(s.createdAt)); put("summary", s.summary)
                            put("emotional_context", s.emotionalContext); put("follow_up", s.followUp)
                        })
                    }
                }
            }
            "save_memory" -> when {
                !memoryOn -> buildJsonObject { put("saved", false); put("reason", "Memory is turned off. The user can turn it on in Settings.") }
                else -> try {
                    val (_, created) = repo.saveMemory(arg("content"), arg("category", 32), source = "voice_tool")
                    buildJsonObject { put("saved", true); put("updated_existing", !created) }
                } catch (e: MemoryRejected) {
                    buildJsonObject {
                        put("saved", false)
                        put("reason", if (e.reason == "sensitive") "That looks like sensitive information, which is never stored." else "Nothing to save.")
                    }
                }
            }
            "update_memory", "delete_memory" -> {
                val about = arg("about")
                val target = if (about.isEmpty()) null else MemoryEngine.bestMatch(repo.data.memories, about)
                when {
                    about.isEmpty() -> buildJsonObject { put("ok", false); put("reason", "Say what the memory is about.") }
                    target == null -> buildJsonObject { put("ok", false); put("reason", "No matching memory found.") }
                    name == "delete_memory" -> {
                        repo.deleteMemory(target.id)
                        buildJsonObject { put("ok", true); put("forgotten", target.content) }
                    }
                    !memoryOn -> buildJsonObject { put("ok", false); put("reason", "Memory is turned off.") }
                    else -> try {
                        repo.updateMemory(target.id, arg("new_content"))
                        buildJsonObject { put("ok", true) }
                    } catch (_: MemoryRejected) {
                        buildJsonObject { put("ok", false); put("reason", "That can't be stored.") }
                    }
                }
            }
            "find_quote" -> buildJsonObject {
                val found = Quotes.find(arg("theme"))
                putJsonArray("quotes") {
                    found.forEach { q -> add(buildJsonObject { put("text", q.text); put("author", q.author); put("source", q.source) }) }
                }
                if (found.isEmpty()) put("note", "No fitting quote. Carry on without one.")
            }
            "retrieve_knowledge" -> buildJsonObject {
                putJsonArray("passages") {
                    knowledge.retrieve(arg("query")).forEach { p ->
                        add(buildJsonObject { put("source", p.title); put("section", p.section); put("content", p.content) })
                    }
                }
            }
            else -> buildJsonObject { put("error", "Unknown tool") }
        }
    }

    private fun off(what: String) = buildJsonObject {
        putJsonArray(what) {}
        put("note", "Memory is turned off by the user.")
    }

    companion object {
        private fun obj(vararg props: Pair<String, JsonObject>, required: List<String> = emptyList()) = buildJsonObject {
            put("type", "OBJECT")
            putJsonObject("properties") { props.forEach { (k, v) -> put(k, v) } }
            if (required.isNotEmpty()) putJsonArray("required") { required.forEach { add(it) } }
        }

        private fun str(description: String, enum: List<String>? = null) = buildJsonObject {
            put("type", "STRING")
            put("description", description)
            if (enum != null) putJsonArray("enum") { enum.forEach { add(it) } }
        }

        private fun fn(name: String, description: String, params: JsonObject? = null) = buildJsonObject {
            put("name", name)
            put("description", description)
            if (params != null) put("parameters", params)
        }

        /** Gemini `functionDeclarations`. */
        val DECLARATIONS = buildJsonArray {
            add(fn("get_relevant_memories", "Look up things the user has shared in earlier conversations that relate to a topic.",
                obj("query" to str("Topic to look up, e.g. 'new job'"), required = listOf("query"))))
            add(fn("save_memory",
                "Remember a durable, useful fact about the user for future conversations (goal, project, recurring worry, " +
                    "preference, life event, plan). Short third-person sentence. Never secrets or financial details.",
                obj(
                    "category" to str("Kind of memory", MEMORY_CATEGORIES),
                    "content" to str("e.g. 'User is preparing for a new product management role.'"),
                    required = listOf("category", "content"),
                )))
            add(fn("update_memory", "Correct or update something remembered about the user.",
                obj("about" to str("What the existing memory is about"), "new_content" to str("The corrected fact"),
                    required = listOf("about", "new_content"))))
            add(fn("delete_memory", "Forget something the user asked you to forget.",
                obj("about" to str("What should be forgotten"), required = listOf("about"))))
            add(fn("get_recent_conversation_summaries", "Brief notes from the user's last few conversations."))
            add(fn("get_user_profile", "The user's name, preferred name, timezone, language and communication preferences."))
            add(fn("find_quote",
                "Find a short, verified quotation (with author and source) that fits a feeling or theme, e.g. 'worry about " +
                    "the future', 'self-criticism', 'hope'. Only ever quote what this returns.",
                obj("theme" to str("The feeling or theme"), required = listOf("theme"))))
            add(fn("retrieve_knowledge",
                "Search the curated psychoeducation library (CBT, ACT, behavioural activation, mindfulness, grounding, " +
                    "sleep, motivation, loneliness, work stress, when to seek help, and key ideas from well-known wellbeing books " +
                    "such as Man's Search for Meaning, Feeling Good, Full Catastrophe Living, Self-Compassion, Flourish and " +
                    "The Gifts of Imperfection) for grounded material on a topic.",
                obj("query" to str("What to look up"), required = listOf("query"))))
        }
    }
}
