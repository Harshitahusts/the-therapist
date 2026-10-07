package app.haven.companion.core

/**
 * Risk detection that runs on every user utterance, on the phone, before the
 * companion's next reply. Deterministic rules so behaviour is predictable and
 * testable against eval/safety_cases.jsonl. Nothing here stores what was said.
 */
enum class RiskLevel { LOW, MODERATE, HIGH, IMMEDIATE }

data class SafetyAssessment(val level: RiskLevel, val categories: List<String> = emptyList()) {
    val showResources: Boolean get() = level >= RiskLevel.HIGH
    /** Replace whatever the companion was saying with a reply that has the guidance in mind. */
    val interrupt: Boolean get() = level >= RiskLevel.MODERATE
}

object Safety {
    private fun r(p: String) = Regex(p)

    // Figures of speech that contain alarming words but almost never signal risk. Removed before matching.
    private val HYPERBOLE = listOf(
        r("""\bkill(ing|ed)? it\b"""),
        r("""\bkill(ing)? (some |the )?time\b"""),
        r("""\bkill (the|this) (process|app|task|lights?|music|engine|bug)\b"""),
        r("""\bdying (to|for)\b"""),
        r("""\b(i )?(could|would) kill for\b"""),
        r("""\b(is|are|it's|was|were) killing me\b"""),
        r("""\b(dead|half dead) (tired|on my feet)\b"""),
        r("""\b(died|dying|die) (of )?laugh(ing|ter)?\b"""),
        r("""\b(bored|scared|worried) to death\b"""),
        r("""\bkiller (app|workout|feature|deal|idea)\b"""),
        r("""\bsuicide squad\b"""),
        r("""\bi'?d rather die than\b"""),
        r("""\bi am dead (lol|haha|lmao)\b"""),
        r("""\bthat'?s (so )?(dead|deadly)\b"""),
    )

    // Explicit denials are removed so "I'm not suicidal, just tired" stays LOW.
    private val NEGATED = listOf(
        r(
            """\b(i am not|i'm not|im not|not that i am|i would never|i'd never|i will never|i never want to|i don't want to)""" +
                """ (going to |gonna )?(suicidal|kill myself|hurt myself|harm myself|end my life)\b"""
        ),
        r("""\bno(t)? (thoughts|thinking) (of|about) (suicide|killing myself|hurting myself)\b"""),
    )

    private val EDUCATIONAL = r("""\bsuicide (prevention|awareness|rates?|statistics|research)\b""")

    private val PAST = r(
        """\b(used to|years ago|months ago|back when|when i was (younger|a kid|a teen|a teenager|in school|in college)""" +
            """|in the past|a long time ago|i was suicidal|i had been|once tried|in high school)\b"""
    )

    private val THIRD_PARTY_SUBJECT = r(
        """\b(my|a|our) (friend|best friend|brother|sister|mom|mum|dad|mother|father|son|daughter|partner|""" +
            """boyfriend|girlfriend|husband|wife|colleague|coworker|cousin|roommate|flatmate|classmate)\b""" +
            """|\b(he|she|they) (is|are|was|were|has|have|said|says|keeps?|wants?|tried)\b"""
    )
    private val THIRD_PARTY_SELF_HARM = r(
        """\b(kill|hurt|harm|cut) (himself|herself|themselves|themself)\b""" +
            """|\b(he|she|they)( is| are|'s|'re)? (suicidal|going to end (it|his life|her life|their life))\b""" +
            """|\b(friend|brother|sister|mom|mum|dad|mother|father|son|daughter|partner|boyfriend|girlfriend|husband|wife|""" +
            """colleague|coworker|cousin|roommate)\b.{0,40}\b(suicidal|wants to die|tried to kill)\b"""
    )
    private val FIRST_PERSON_SUICIDAL =
        r("""\b(i am|i feel|i've been|i have been|i'm feeling|feeling) (so |really |very )?suicidal\b""")

    private const val IMMINENCE = """(now|right now|tonight|today|this evening|in a (few|couple of) (minutes|hours)|soon)"""
    private const val SELF_ACT = """(kill myself|end (it|it all|my life)|take my (own )?life|jump|overdose|do it)"""

    private val RULES: List<Triple<RiskLevel, String, Regex>> = listOf(
        // --- IMMEDIATE ---
        Triple(RiskLevel.IMMEDIATE, "self_harm_imminent",
            r("""\b(going to|about to|gonna|will|planning to|ready to)\s+$SELF_ACT\b.{0,40}\b$IMMINENCE\b""")),
        Triple(RiskLevel.IMMEDIATE, "self_harm_imminent",
            r("""\b$IMMINENCE\b.{0,30}\b(i am|i'm|i will|i'll)\s+(going to |gonna )?$SELF_ACT\b""")),
        Triple(RiskLevel.IMMEDIATE, "overdose",
            r("""\b(took|taken|swallowed|just took|have taken|i've taken)\s+(a bunch of|all (of )?my|too many|loads of|""" +
                """a lot of|an overdose|a whole (bottle|pack|packet) of|the whole (bottle|pack|packet) of)\s*\w*\s*""" +
                """(pills|tablets|medication|meds|paracetamol|sleeping pills)?\b""")),
        Triple(RiskLevel.IMMEDIATE, "self_harm_means",
            r("""\b(i have|i've got|i am holding|i'm holding|holding|i bought)\s+(a|the|some)\s+(gun|knife|rope|blade|razor|pills)\b""" +
                """.{0,60}\b$SELF_ACT\b""")),
        Triple(RiskLevel.IMMEDIATE, "self_harm_imminent",
            r("""\b(standing|sitting|i am|i'm) on (the|a) (edge|ledge|bridge|roof|railway|tracks)\b""")),
        Triple(RiskLevel.IMMEDIATE, "medical_emergency", r("""\b(won't|wont|can't|cannot) stop (the )?bleeding\b""")),
        Triple(RiskLevel.IMMEDIATE, "danger_from_others",
            r("""\b(he|she|they|someone|somebody)('s| is| are)? (here|outside|at the door|breaking in|trying to (get|break) in)\b""" +
                """.{0,60}\b(hurt|kill|gun|knife|weapon|scared)\b""")),
        // --- HIGH ---
        Triple(RiskLevel.HIGH, "suicidal_ideation",
            r("""\b(want|wanna|wish|plan|planning|thinking about|thought about|think about|considering|going)( to)? """ +
                """(kill(ing)? myself|end(ing)? my (own )?life|tak(e|ing) my (own )?life|commit(ting)? suicide)\b""")),
        Triple(RiskLevel.HIGH, "suicidal_ideation", r("""\bkill myself\b|\bkms\b""")),
        Triple(RiskLevel.HIGH, "suicidal_ideation", r("""\bsuicid(e|al)\b""")),
        Triple(RiskLevel.HIGH, "suicidal_ideation",
            r("""\b(want to|wish i could|wish to|would like to|ready to) (just )?die\b|\bi want to be dead\b|\bi wish i (was|were) dead\b""")),
        Triple(RiskLevel.HIGH, "suicidal_ideation", r("""\bend it all\b|\bend my life\b""")),
        Triple(RiskLevel.HIGH, "suicidal_ideation", r("""\b(don't|do not|dont) want to (live|be alive|exist)( anymore| any more)?\b""")),
        Triple(RiskLevel.HIGH, "suicidal_ideation", r("""\bbetter off (dead|without me)\b""")),
        Triple(RiskLevel.HIGH, "suicidal_ideation", r("""\bno (reason|point) (to|in) (live|living|being alive|go on|going on)\b""")),
        Triple(RiskLevel.HIGH, "self_harm",
            r("""\b(hurt|harm|cut|cutting|burn|burning|hurting|harming) myself\b|\bself[- ]?harm(ing)?\b""")),
        Triple(RiskLevel.HIGH, "harm_to_others",
            r("""\b(going to|want to|gonna|planning to|plan to|will)\s+(kill|hurt|stab|shoot|attack)\s+""" +
                """(him|her|them|someone|somebody|people|everyone|my \w+)\b.{0,40}\b(gun|knife|weapon|plan|tonight|tomorrow|for real|seriously)\b""")),
        Triple(RiskLevel.HIGH, "harm_to_others", r("""\bshoot up (the|my|a)\b""")),
        Triple(RiskLevel.HIGH, "abuse",
            r("""\b(he|she|they|my (husband|wife|partner|boyfriend|girlfriend|dad|father|mom|mum|mother|parents?|""" +
                """stepdad|stepmom|brother|sister|boss|uncle))\s+(hits|hit|beats|beat|chokes|choked|strangled|hurts|hurt|""" +
                """abuses|abused|threatens|threatened to (kill|hurt))\s+me\b""")),
        Triple(RiskLevel.HIGH, "abuse", r("""\b(being|been|was|got) (abused|assaulted|raped|sexually assaulted)\b""")),
        Triple(RiskLevel.HIGH, "danger", r("""\b(i am|i'm|i feel) (not safe|unsafe|in danger|scared for my life)\b""")),
        // --- MODERATE ---
        Triple(RiskLevel.MODERATE, "hopelessness",
            r("""\b(can't|cannot|cant) (go on|do this anymore|take (it|this) anymore|keep going)\b""")),
        Triple(RiskLevel.MODERATE, "hopelessness",
            r("""\bhopeless\b|\bno hope\b|\bwhat'?s the point (of|in) (anything|it all|living|life|trying)\b""")),
        Triple(RiskLevel.MODERATE, "passive_ideation",
            r("""\bwish i (could |would |can )?(just )?(disappear|wasn'?t here|weren'?t here|was never born|could sleep forever|didn'?t wake up|""" +
                """wouldn'?t wake up)\b|\b(don't|dont) want to wake up\b|\bdisappear forever\b""")),
        Triple(RiskLevel.MODERATE, "passive_ideation",
            r("""\b(nobody|no one|no-one) would (even |really |ever )?(miss|care|notice)\b|\beveryone would be better off\b""" +
                """|\bif i (was|were) gone\b""")),
        Triple(RiskLevel.MODERATE, "hopelessness", r("""\b(i am|i'm|i feel like) (such )?a burden\b|\bi feel trapped\b""")),
        Triple(RiskLevel.MODERATE, "harm_to_others",
            r("""\b(want to|wanna|going to|gonna)\s+(hurt|attack|stab|shoot)\s+(him|her|them|someone|somebody|people)\b""")),
        Triple(RiskLevel.MODERATE, "panic", r("""\bpanic attack\b|\b(i )?(can't|cannot) breathe\b""")),
    )

    fun normalize(text: String): String {
        var t = text.lowercase().replace('’', '\'').replace('‘', '\'')
        t = t.replace(Regex("""\bi'm\b"""), "i am")
        t = t.replace(Regex("""\bim\b"""), "i am")
        t = t.replace(Regex("""\bgonna\b"""), "going to")
        t = t.replace(Regex("""\bwanna\b"""), "want to")
        return t.replace(Regex("""\s+"""), " ").trim()
    }

    private fun strip(patterns: List<Regex>, text: String) = patterns.fold(text) { acc, p -> p.replace(acc, " ") }

    fun classify(text: String): SafetyAssessment {
        var t = strip(NEGATED, strip(HYPERBOLE, normalize(text)))
        var level = RiskLevel.LOW
        val categories = mutableListOf<String>()

        if (EDUCATIONAL.containsMatchIn(t) && !FIRST_PERSON_SUICIDAL.containsMatchIn(t)) {
            t = EDUCATIONAL.replace(t, " ")
            categories += "topic_mention"
        }

        val thirdParty = THIRD_PARTY_SELF_HARM.containsMatchIn(t) && !FIRST_PERSON_SUICIDAL.containsMatchIn(t)
        if (thirdParty) {
            categories += "third_party_concern"
            level = RiskLevel.MODERATE
            t = THIRD_PARTY_SELF_HARM.replace(t, " ")
            // "suicidal" about someone else should not then match the first-person rule.
            if (THIRD_PARTY_SUBJECT.containsMatchIn(t)) t = Regex("""\bsuicid(e|al)\b""").replace(t, " ")
        }

        for ((ruleLevel, category, pattern) in RULES) {
            if (pattern.containsMatchIn(t)) {
                if (category !in categories) categories += category
                if (ruleLevel > level) level = ruleLevel
            }
        }

        // Talking about the past is still worth a gentle check-in, but not a crisis
        // response. Imminent danger is never downgraded.
        if (level == RiskLevel.HIGH && PAST.containsMatchIn(t) && "abuse" !in categories) {
            level = RiskLevel.MODERATE
            categories += "past_experience"
        }
        return SafetyAssessment(level, categories)
    }

    // ---- Crisis resources: few and verified. The app always also says "contact your local emergency number". ----

    val CRISIS_RESOURCES: Map<String, CrisisResources> = mapOf(
        "US" to CrisisResources("US", "911", listOf(CrisisLine("988 Suicide & Crisis Lifeline", "988", "988"))),
        "CA" to CrisisResources("CA", "911", listOf(CrisisLine("9-8-8 Suicide Crisis Helpline", "988", "988"))),
        "GB" to CrisisResources("GB", "999", listOf(CrisisLine("Samaritans", "116 123"), CrisisLine("Shout", text = "85258"))),
        "IE" to CrisisResources("IE", "112", listOf(CrisisLine("Samaritans", "116 123"), CrisisLine("Text About It", text = "50808"))),
        "IN" to CrisisResources("IN", "112", listOf(CrisisLine("Tele-MANAS", "14416"))),
        "AU" to CrisisResources("AU", "000", listOf(CrisisLine("Lifeline", "13 11 14"))),
        "NZ" to CrisisResources("NZ", "111", listOf(CrisisLine("Need to talk? 1737", "1737", "1737"))),
        "INTL" to CrisisResources("INTL", null, emptyList()),
    )

    fun resourcesFor(region: String?): CrisisResources =
        CRISIS_RESOURCES[(region ?: "INTL").uppercase()] ?: CRISIS_RESOURCES.getValue("INTL")

    private fun resourceSentence(res: CrisisResources): String {
        val parts = mutableListOf(res.emergency?.let { "the local emergency number ($it)" } ?: "the local emergency number")
        for (line in res.lines) {
            val how = listOfNotNull(line.phone?.let { "call $it" }, line.text?.let { "text $it" })
            parts += "${line.name} (${how.joinToString(" or ")})"
        }
        return parts.joinToString(", ")
    }

    /** Guidance injected into the live conversation for this turn, or null when no action is needed. */
    fun guidanceFor(a: SafetyAssessment, region: String?): String? {
        val res = resourcesFor(region)
        return when (a.level) {
            RiskLevel.LOW -> null
            RiskLevel.MODERATE -> when {
                "third_party_concern" in a.categories ->
                    "SAFETY NOTE: The user may be worried about someone else's safety. Acknowledge how hard that is. " +
                        "If that person might be in immediate danger, say clearly that the best step is to contact " +
                        "emergency services or a crisis line for them: ${resourceSentence(res)}. " +
                        "Do not take on the role of crisis counsellor."
                "panic" in a.categories ->
                    "SAFETY NOTE: The user may be having intense anxiety or panic. Slow down. Use short, calm sentences. " +
                        "Offer one simple grounding step at a time (for example slow breathing with a longer out-breath). " +
                        "If they mention chest pain, fainting, or symptoms that feel physically dangerous, tell them to " +
                        "contact emergency services."
                else ->
                    "SAFETY NOTE: The user said something that may reflect hopelessness or passive thoughts of not wanting " +
                        "to be here. Slow down. Reflect what you heard warmly. Then ask one gentle, direct question, such as " +
                        "whether they are having any thoughts of hurting themselves or ending their life. Do not lecture."
            }
            RiskLevel.HIGH ->
                "SAFETY PRIORITY: The user may be at risk of harm. Respond with calm warmth and without judgement. " +
                    "Ask directly whether they are safe right now. Clearly encourage them to reach real human support now: " +
                    "a trusted person nearby, or ${resourceSentence(res)}. " +
                    "Mention that a support card with these numbers is on their screen. Do not ask them to promise anything, " +
                    "do not suggest they rely only on you, and do not try to act as their therapist. Keep it short."
            RiskLevel.IMMEDIATE ->
                "SAFETY EMERGENCY: The user may be in immediate danger. In one or two short sentences, tell them to contact " +
                    "emergency services right now: ${resourceSentence(res)}, or to get a person nearby to help them now. " +
                    "Tell them the call button is on their screen. Do not continue the normal conversation, do not ask them to " +
                    "promise anything, and do not suggest staying with you instead of getting help."
        }
    }
}
