package app.haven.companion.core

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Base64

class LiveSessionTest {
    private fun json(s: String) = AppJson.parseToJsonElement(s).jsonObject
    private fun sent(effects: List<LiveEffect>) = effects.filterIsInstance<LiveEffect.Send>().map { json(it.json) }
    private fun audio(bytes: ByteArray) =
        """{"serverContent":{"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"${Base64.getEncoder().encodeToString(bytes)}"}}]}}}"""
    private fun inputText(t: String) = """{"serverContent":{"inputTranscription":{"text":"$t"}}}"""
    private fun outputText(t: String) = """{"serverContent":{"outputTranscription":{"text":"$t"}}}"""
    private val turnComplete = """{"serverContent":{"turnComplete":true}}"""

    private fun connected(): LiveCoordinator = LiveCoordinator("gemini-live-test", "INSTRUCTIONS").also {
        it.onServerMessage("""{"setupComplete":{}}""")
    }

    @Test
    fun setupMessageShape() {
        val setup = json(LiveCoordinator("gemini-live-test", "Be kind").setupMessage())["setup"]!!.jsonObject
        assertEquals("models/gemini-live-test", setup["model"]!!.jsonPrimitive.content)
        assertEquals("AUDIO", setup["generationConfig"]!!.jsonObject["responseModalities"]!!.jsonArray[0].jsonPrimitive.content)
        assertEquals("Be kind", setup["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        val fns = setup["tools"]!!.jsonArray[0].jsonObject["functionDeclarations"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }
        assertTrue(fns.containsAll(listOf("get_relevant_memories", "save_memory", "update_memory", "delete_memory",
            "retrieve_knowledge", "get_user_profile", "get_recent_conversation_summaries")))
        listOf("inputAudioTranscription", "outputAudioTranscription", "sessionResumption", "contextWindowCompression")
            .forEach { assertTrue(it, it in setup) }
    }

    @Test
    fun audioMessageIsBase64Pcm16k() {
        val m = json(LiveCoordinator("m", "i").audioMessage(byteArrayOf(1, 2, 3)))["realtimeInput"]!!.jsonObject["audio"]!!.jsonObject
        assertEquals("audio/pcm;rate=16000", m["mimeType"]!!.jsonPrimitive.content)
        assertArrayEquals(byteArrayOf(1, 2, 3), Base64.getDecoder().decode(m["data"]!!.jsonPrimitive.content))
    }

    @Test
    fun greetsOnceOnSetupButNotAfterResuming() {
        val c = LiveCoordinator("m", "i")
        val first = c.onServerMessage("""{"setupComplete":{}}""")
        assertEquals(Prompts.OPENING_CUE, sent(first).single()["realtimeInput"]!!.jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals(VoicePhase.THINKING, c.phase)
        c.onReconnecting()
        assertTrue(sent(c.onServerMessage("""{"setupComplete":{}}""")).isEmpty())
    }

    @Test
    fun playsAudioAndFollowsPhases() {
        val c = connected()
        val pcm = byteArrayOf(9, 8, 7, 6)
        val out = c.onServerMessage(audio(pcm))
        assertArrayEquals(pcm, out.filterIsInstance<LiveEffect.PlayAudio>().single().pcm)
        assertEquals(VoicePhase.SPEAKING, c.phase)
        c.onServerMessage(turnComplete)
        assertEquals("still speaking until the speaker drains", VoicePhase.SPEAKING, c.phase)
        c.onPlaybackIdle()
        assertEquals(VoicePhase.LISTENING, c.phase)
    }

    @Test
    fun interruptionStopsPlayback() {
        val c = connected()
        c.onServerMessage(audio(byteArrayOf(1, 1)))
        val out = c.onServerMessage("""{"serverContent":{"interrupted":true}}""")
        assertTrue(LiveEffect.StopPlayback in out)
        assertEquals(VoicePhase.LISTENING, c.phase)
    }

    @Test
    fun transcriptIsOrderedAndUserTurnsAreSafetyChecked() {
        val c = connected()
        c.onServerMessage(outputText("Hey Harshit. "))
        c.onServerMessage(outputText("How are you feeling?"))
        c.onServerMessage(turnComplete)
        c.onServerMessage(inputText("Pretty "))
        c.onServerMessage(inputText("demotivated."))
        val out = c.onServerMessage(audio(byteArrayOf(1)))
        assertEquals(listOf(LiveEffect.CheckSafety("Pretty demotivated.")), out.filterIsInstance<LiveEffect.CheckSafety>())
        c.onServerMessage(outputText("That sounds heavy."))
        c.onServerMessage(turnComplete)
        assertEquals(
            listOf(
                TranscriptTurn("assistant", "Hey Harshit. How are you feeling?"),
                TranscriptTurn("user", "Pretty demotivated."),
                TranscriptTurn("assistant", "That sounds heavy."),
            ),
            c.transcript(),
        )
    }

    @Test
    fun toolCallRoundTrip() {
        val c = connected()
        val out = c.onServerMessage(
            """{"toolCall":{"functionCalls":[{"id":"call-1","name":"save_memory","args":{"category":"goal","content":"x"}}]}}"""
        )
        val run = out.filterIsInstance<LiveEffect.RunTool>().single()
        assertEquals("save_memory", run.name)
        assertEquals("x", run.args["content"]!!.jsonPrimitive.content)
        assertEquals(VoicePhase.THINKING, c.phase)
        val resp = sent(c.onToolResult("call-1", "save_memory", buildJsonObject { put("saved", true) })).single()
        val fr = resp["toolResponse"]!!.jsonObject["functionResponses"]!!.jsonArray[0].jsonObject
        assertEquals("call-1", fr["id"]!!.jsonPrimitive.content)
        assertEquals("save_memory", fr["name"]!!.jsonPrimitive.content)
        assertEquals("true", fr["response"]!!.jsonObject["saved"]!!.jsonPrimitive.content)
    }

    @Test
    fun cancelledToolResultsAreDropped() {
        val c = connected()
        c.onServerMessage("""{"toolCall":{"functionCalls":[{"id":"a","name":"get_user_profile","args":{}}]}}""")
        c.onServerMessage("""{"toolCallCancellation":{"ids":["a"]}}""")
        assertTrue(c.onToolResult("a", "get_user_profile", JsonObject(emptyMap())).isEmpty())
    }

    @Test
    fun riskyTurnCutsTheReplyAndResteersWithGuidance() {
        val c = connected()
        c.onServerMessage(inputText("I want to kill myself"))
        c.onServerMessage(audio(byteArrayOf(1, 2)))
        val a = Safety.classify("I want to kill myself")
        val out = c.onSafety(a, Safety.guidanceFor(a, "IN"))
        assertTrue(LiveEffect.StopPlayback in out)
        val text = sent(out).single()["realtimeInput"]!!.jsonObject["text"]!!.jsonPrimitive.content
        assertTrue(text.startsWith("SAFETY PRIORITY") && text.contains("14416"))
        // The rest of the unsafe reply is discarded…
        assertTrue(c.onServerMessage(audio(byteArrayOf(3))).none { it is LiveEffect.PlayAudio })
        // …and the guided reply plays after the server closes that turn.
        c.onServerMessage("""{"serverContent":{"interrupted":true}}""")
        assertTrue(c.onServerMessage(audio(byteArrayOf(4))).any { it is LiveEffect.PlayAudio })
    }

    @Test
    fun lowRiskDoesNothing() {
        val c = connected()
        assertTrue(c.onSafety(Safety.classify("long day at work"), null).isEmpty())
    }

    @Test
    fun goAwayReconnectsWithResumptionHandle() {
        val c = connected()
        c.onServerMessage("""{"sessionResumptionUpdate":{"newHandle":"h-1","resumable":true}}""")
        c.onServerMessage("""{"sessionResumptionUpdate":{"newHandle":"h-2","resumable":false}}""")
        assertEquals("h-1", c.resumeHandle)
        assertTrue(LiveEffect.Reconnect in c.onServerMessage("""{"goAway":{"timeLeft":"10s"}}"""))
        val setup = json(c.setupMessage())["setup"]!!.jsonObject
        assertEquals("h-1", setup["sessionResumption"]!!.jsonObject["handle"]!!.jsonPrimitive.content)
    }

    @Test
    fun malformedMessagesAreIgnored() {
        val c = connected()
        assertTrue(c.onServerMessage("not json").isEmpty())
        assertTrue(c.onServerMessage("""{"usageMetadata":{}}""").isEmpty())
    }

    @Test
    fun instructionsIncludeContextAndRespectMemorySetting() {
        val now = ZonedDateTime.of(2026, 10, 7, 19, 30, 0, 0, ZoneId.of("Asia/Kolkata"))
        val data = CompanionData(
            profile = Profile(preferredName = "Harshit", timezone = "Asia/Kolkata"),
            memories = listOf(Memory("1", "life_event", "User is starting a new PM role.", 0.9, "user", 1, 1)),
            summaries = listOf(ConversationSummary("c", "Felt anxious about work.", followUp = "Ask about week one.", createdAt = 1)),
        )
        val on = LiveCoordinator.buildInstructions(data, now)
        assertTrue(on.contains("Harshit") && on.contains("evening") && on.contains("User is starting a new PM role."))
        assertTrue(on.contains("Ask about week one.") && on.contains("not a therapist") && on.contains("Memory is ON"))
        val off = LiveCoordinator.buildInstructions(data.copy(settings = CompanionSettings(memoryEnabled = false)), now)
        assertTrue(off.contains("Memory is OFF") && !off.contains("new PM role"))
    }

    private fun cueText(effects: List<LiveEffect>) = sent(effects).map { it["realtimeInput"]!!.jsonObject["text"]!!.jsonPrimitive.content }

    /** Greets, speaks a question, finishes; leaves the companion listening at time [now]. */
    private fun listeningAfterQuestion(now: LongArray): LiveCoordinator {
        val c = LiveCoordinator("m", "i", clock = { now[0] })
        c.onServerMessage("""{"setupComplete":{}}""")
        c.onServerMessage(audio(byteArrayOf(1, 2)))
        c.onServerMessage(outputText("What has today felt like?"))
        c.onServerMessage(turnComplete)
        c.onPlaybackIdle()
        assertEquals(VoicePhase.LISTENING, c.phase)
        return c
    }

    @Test
    fun quietUserGetsAGentleEasierQuestionThenCompanyThenSilence() {
        val now = longArrayOf(0)
        val c = listeningAfterQuestion(now)
        assertTrue(c.onTick().isEmpty()) // starts the quiet timer
        now[0] = LiveCoordinator.FIRST_QUIET_NUDGE_MS - 1
        assertTrue("not before 20 s", c.onTick().isEmpty())
        now[0] = LiveCoordinator.FIRST_QUIET_NUDGE_MS + 1
        assertEquals(listOf(Prompts.QUIET_CUE), cueText(c.onTick()))
        assertEquals(VoicePhase.THINKING, c.phase)

        // The companion answers the nudge, then the user stays quiet again.
        c.onServerMessage(audio(byteArrayOf(3)))
        c.onServerMessage(turnComplete)
        c.onPlaybackIdle()
        c.onTick()
        now[0] += LiveCoordinator.SECOND_QUIET_NUDGE_MS + 1
        assertEquals(listOf(Prompts.STILL_QUIET_CUE), cueText(c.onTick()))

        c.onServerMessage(turnComplete)
        c.onPlaybackIdle()
        c.onTick()
        now[0] += 10 * LiveCoordinator.SECOND_QUIET_NUDGE_MS
        assertTrue("then it simply waits", c.onTick().isEmpty())
    }

    @Test
    fun speakingResetsTheQuietTimer() {
        val now = longArrayOf(0)
        val c = listeningAfterQuestion(now)
        c.onTick()
        now[0] = LiveCoordinator.FIRST_QUIET_NUDGE_MS - 1_000
        c.noteUserActivity() // heard on the microphone
        now[0] = LiveCoordinator.FIRST_QUIET_NUDGE_MS + 1_000
        assertTrue(c.onTick().isEmpty())
        c.onServerMessage(inputText("I guess"))
        now[0] += 3 * LiveCoordinator.FIRST_QUIET_NUDGE_MS
        assertTrue("never nudges while the user is mid-sentence", c.onTick().isEmpty())
    }

    @Test
    fun droppedSessionContinuesWithoutGreetingAgain() {
        val earlier = listOf(TranscriptTurn("user", "Work has been heavy."), TranscriptTurn("assistant", "That sounds like a lot."))
        val recap = LiveCoordinator.recap(earlier)
        assertTrue(recap.contains("User: Work has been heavy.") && recap.contains("You: That sounds like a lot."))
        val c = LiveCoordinator("m", "i\n\n$recap", continuing = true, priorTurns = earlier)
        assertEquals(listOf(Prompts.RESUME_CUE), cueText(c.onServerMessage("""{"setupComplete":{}}""")))
        assertTrue(c.everConnected)
        c.onServerMessage(inputText("Yes, still."))
        c.onServerMessage(turnComplete)
        assertEquals(earlier + TranscriptTurn("user", "Yes, still."), c.transcript())
        assertEquals("", LiveCoordinator.recap(emptyList()))
    }

    @Test
    fun promptTeachesBreakingQuestionsDown() {
        val p = Prompts.VOICE_AGENT
        listOf("When something is hard to answer", "small, simple pieces", "ONE small piece at a time", "Long conversations")
            .forEach { assertTrue(it, p.contains(it)) }
    }
}
