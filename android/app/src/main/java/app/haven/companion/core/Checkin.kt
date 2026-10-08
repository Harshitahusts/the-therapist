package app.haven.companion.core

import kotlinx.serialization.Serializable

/**
 * The wellbeing check-in: a few multiple-choice questions, each followed by its
 * own small uplifting note, adding up to a Well-Being Count (WBC) from 0 to 100.
 * It is a reflection, not a diagnosis.
 */
object Checkin {

    /** Answer options, in display order. */
    val OPTIONS = listOf("Often", "Sometimes", "Rarely", "Never")

    data class Question(
        val id: String,
        val topic: String,
        val text: String,
        /** True when "Often" is the healthiest answer; false when "Never" is. */
        val oftenIsGood: Boolean,
        val noteTitle: String,
        val note: String,
    )

    /** Ordered so that any prefix (3, 5 or 10) covers a balanced mix of topics. */
    val QUESTIONS = listOf(
        Question(
            "joy", "Emotions",
            "How often do everyday moments make you feel happy?", true,
            "Joy has small doors",
            "A warm cup, a song you love, light on a wall. Spotting just one of these today is like finding a coin in a coat pocket. 🌼",
        ),
        Question(
            "sleep", "Healthy habits",
            "How often do you wake up feeling rested?", true,
            "Your brain's night shift",
            "While you sleep, your brain quietly tidies up the day, like a cleaning crew polishing the floors after closing. An early night is a free spa visit for your mind. 🌙",
        ),
        Question(
            "support", "Relationships",
            "How often do you feel supported by the people around you?", true,
            "Somebody's glad you exist",
            "A short, kind chat can lift your mood for hours, and it works both ways. There's someone who would genuinely light up if you said hi today. 💌",
        ),
        Question(
            "overthinking", "Thoughts",
            "How often does overthinking make it hard to unwind?", false,
            "You are the sky",
            "Thoughts drift by like clouds, and the sky is never harmed by the weather. Try naming one thought out loud, then let it float along. ☁️",
        ),
        Question(
            "selftalk", "Thoughts",
            "How often do self-critical thoughts weigh on you?", false,
            "Your best friend, on call",
            "Picture how you'd talk to your best friend on a rough day: gentle, a little funny, completely on their side. You're allowed to be that friend to yourself. 🤝",
        ),
        Question(
            "calm", "Emotions",
            "When feelings get intense, how often can you find your way back to calm?", true,
            "A remote control in your chest",
            "Breathing out for longer than you breathe in tells your body, \"we're safe now.\" One slow, long exhale counts. Want to try one right now? 🌬️",
        ),
        Question(
            "selfcare", "Healthy habits",
            "How often do you make time for something that's just for you?", true,
            "Always in stock",
            "Self-care doesn't need a shopping trip. Five minutes of doing nothing on purpose counts, and it never sells out. 🍵",
        ),
        Question(
            "movement", "Healthy habits",
            "How often do you move your body in a way that feels good?", true,
            "Cloud-watching counts",
            "A ten-minute walk can brighten your mood, and it's the only workout where looking up at the sky is part of the technique. 🚶",
        ),
        Question(
            "past", "Your past",
            "How often do past events still affect how you feel today?", false,
            "A chapter, not the book",
            "What happened is part of your story, not all of it. Every time you treat yourself gently, you write a kinder page, and today's page counts. 📖",
        ),
        Question(
            "future", "Your future",
            "How often do worries about the future feel heavy?", false,
            "Futures are made of todays",
            "You just did something quietly brave: you checked in with yourself. That's exactly how good futures get built, one small moment at a time. 🌅",
        ),
    )

    val QUESTION_COUNTS = listOf(3, 5, 10)

    fun questionsFor(count: Int): List<Question> = QUESTIONS.take(count.coerceIn(1, QUESTIONS.size))

    /** 0..3, higher is healthier, for an option index into [OPTIONS]. */
    fun points(q: Question, optionIndex: Int): Int {
        require(optionIndex in OPTIONS.indices) { "bad option $optionIndex" }
        return if (q.oftenIsGood) 3 - optionIndex else optionIndex
    }

    /** Well-Being Count: 0..100 from the answered questions (question id -> option index). */
    fun score(answers: Map<String, Int>): Int {
        val scored = answers.mapNotNull { (id, opt) -> QUESTIONS.find { it.id == id }?.let { points(it, opt) } }
        if (scored.isEmpty()) return 0
        return Math.round(scored.sum() * 100.0 / (3 * scored.size)).toInt()
    }

    data class Band(val name: String, val emoji: String, val message: String)

    fun band(score: Int): Band = when {
        score >= 80 -> Band("Blooming", "🌸", "You're in a bright season. Keep watering what's working.")
        score >= 60 -> Band("Growing", "🌿", "Plenty is going well, with a few spots that would love a little more sunlight.")
        score >= 40 -> Band("Sprouting", "🌱", "Some things feel heavy right now. Small, kind steps really do add up.")
        else -> Band(
            "Needs gentle care", "🍃",
            "Things seem hard at the moment. You don't have to carry it alone: talking it through, with Haven or someone you trust, can help.",
        )
    }

    fun record(answers: Map<String, Int>, now: Long = System.currentTimeMillis()): WellbeingCheck = WellbeingCheck(
        createdAt = now,
        score = score(answers),
        answers = QUESTIONS.filter { it.id in answers }.map { CheckAnswer(it.id, it.text, OPTIONS[answers.getValue(it.id)]) },
    )
}

@Serializable
data class CheckAnswer(val questionId: String, val question: String, val answer: String)

@Serializable
data class WellbeingCheck(val createdAt: Long, val score: Int, val answers: List<CheckAnswer>)
