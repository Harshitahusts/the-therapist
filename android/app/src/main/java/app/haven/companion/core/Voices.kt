package app.haven.companion.core

/** Gemini's prebuilt voices that suit a calm companion, softest first. */
object Voices {
    data class Voice(val name: String, val style: String, val description: String)

    val CALM = listOf(
        Voice("Sulafat", "Warm", "Warm and reassuring, like a friend by the fire"),
        Voice("Vindemiatrix", "Gentle", "Gentle and unhurried"),
        Voice("Achernar", "Soft", "Soft and quiet"),
        Voice("Enceladus", "Breathy", "Breathy and relaxed"),
        Voice("Algieba", "Smooth", "Smooth and steady"),
        Voice("Despina", "Smooth", "Smooth and clear"),
        Voice("Aoede", "Breezy", "Light and breezy"),
        Voice("Umbriel", "Easy-going", "Easy-going and calm"),
        Voice("Callirrhoe", "Easy-going", "Relaxed and friendly"),
        Voice("Achird", "Friendly", "Friendly and kind"),
        Voice("Gacrux", "Mature", "Mature and grounded"),
        Voice("Schedar", "Even", "Even and composed"),
    )

    const val DEFAULT = "Sulafat"

    /** The voices every Live model supports; newer native-audio models support all of [CALM]. */
    val CLASSIC = setOf("Puck", "Charon", "Kore", "Fenrir", "Aoede", "Leda", "Orus", "Zephyr")
    const val CLASSIC_FALLBACK = "Aoede"

    fun find(name: String?): Voice = CALM.firstOrNull { it.name == name } ?: CALM.first { it.name == DEFAULT }
}
