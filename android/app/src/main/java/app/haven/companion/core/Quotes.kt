package app.haven.companion.core

/**
 * A small, hand-checked library of short quotations about wellbeing, credited
 * to their authors and works. The companion may share one now and then, and is
 * told never to quote anything that is not in here.
 *
 * Classical texts use public-domain translations; quotations from modern books
 * are short, attributed excerpts.
 */
object Quotes {
    data class Quote(val text: String, val author: String, val source: String, val themes: List<String>)

    val ALL = listOf(
        Quote("Men are disturbed not by things, but by the views which they take of things.",
            "Epictetus", "Enchiridion (tr. Elizabeth Carter)", listOf("thoughts", "anxiety", "overthinking", "perspective")),
        Quote("We suffer more often in imagination than in reality.",
            "Seneca", "Letters to Lucilius, Letter 13 (tr. R. M. Gummere)", listOf("anxiety", "worry", "future", "overthinking")),
        Quote("Such as are thy habitual thoughts, such also will be the character of thy mind; for the soul is dyed by the thoughts.",
            "Marcus Aurelius", "Meditations, Book 5 (tr. George Long)", listOf("thoughts", "self-talk", "habits")),
        Quote("Everything can be taken from a man but one thing: the last of the human freedoms, to choose one's attitude in any given set of circumstances.",
            "Viktor Frankl", "Man's Search for Meaning", listOf("hardship", "meaning", "resilience", "choice")),
        Quote("When we are no longer able to change a situation, we are challenged to change ourselves.",
            "Viktor Frankl", "Man's Search for Meaning", listOf("change", "acceptance", "hardship", "resilience")),
        Quote("The curious paradox is that when I accept myself just as I am, then I can change.",
            "Carl Rogers", "On Becoming a Person", listOf("self-acceptance", "self-criticism", "change", "growth")),
        Quote("You can't stop the waves, but you can learn to surf.",
            "Jon Kabat-Zinn", "Wherever You Go, There You Are", listOf("stress", "mindfulness", "acceptance", "anxiety")),
        Quote("Feelings come and go like clouds in a windy sky. Conscious breathing is my anchor.",
            "Thich Nhat Hanh", "Stepping into Freedom", listOf("emotions", "breathing", "calm", "panic", "mindfulness")),
        Quote("You are the sky. Everything else, it's just the weather.",
            "Pema Chödrön", "teachings", listOf("emotions", "overthinking", "mindfulness", "calm")),
        Quote("Owning our story and loving ourselves through that process is the bravest thing we'll ever do.",
            "Brené Brown", "The Gifts of Imperfection", listOf("self-acceptance", "shame", "courage", "past")),
        Quote("Anything that's human is mentionable, and anything that is mentionable can be more manageable.",
            "Fred Rogers", "Mister Rogers' Neighborhood", listOf("talking", "feelings", "shame", "openness")),
        Quote("Almost everything will work again if you unplug it for a few minutes, including you.",
            "Anne Lamott", "TED talk, 12 Truths I Learned from Life and Writing", listOf("rest", "burnout", "stress", "self-care")),
        Quote("You may not control all the events that happen to you, but you can decide not to be reduced by them.",
            "Maya Angelou", "Letter to My Daughter", listOf("resilience", "hardship", "courage", "past")),
        Quote("In the midst of winter, I found there was, within me, an invincible summer.",
            "Albert Camus", "Return to Tipasa", listOf("hope", "hardship", "resilience", "sadness")),
        Quote("Let everything happen to you: beauty and terror. Just keep going. No feeling is final.",
            "Rainer Maria Rilke", "Book of Hours (tr. Anita Barrows and Joanna Macy)", listOf("emotions", "sadness", "hope", "hardship")),
        Quote("Although the world is full of suffering, it is full also of the overcoming of it.",
            "Helen Keller", "Optimism (1903)", listOf("hope", "hardship", "resilience")),
        Quote("A journey of a thousand miles begins with a single step.",
            "Lao Tzu", "Tao Te Ching, chapter 64", listOf("motivation", "goals", "procrastination", "small steps")),
        Quote("Tell me, what is it you plan to do with your one wild and precious life?",
            "Mary Oliver", "The Summer Day", listOf("purpose", "future", "meaning", "motivation")),
    )

    private fun words(s: String) = Regex("[a-z]+").findAll(s.lowercase()).map { it.value.trimEnd('s') }.filter { it.length > 2 }.toSet()

    /** Best-matching quotes for a theme or feeling, e.g. "worry about the future". */
    fun find(theme: String, limit: Int = 2): List<Quote> {
        val q = words(theme)
        if (q.isEmpty()) return emptyList()
        return ALL.map { quote ->
            val tags = quote.themes.flatMap { words(it) }.toSet()
            quote to (q.intersect(tags).size * 3 + q.intersect(words(quote.text)).size)
        }.filter { it.second > 0 }.sortedByDescending { it.second }.take(limit).map { it.first }
    }
}
