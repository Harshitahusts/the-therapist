package app.haven.companion.voice

import app.haven.companion.data.TranscriptTurn
import app.haven.companion.data.TurnCheck
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeCoordinatorTest {

    private fun ev(type: String, extra: String = "") = """{"type":"$type"$extra}"""

    private fun sentTypes(effects: List<Effect>) =
        effects.filterIsInstance<Effect.Send>().map { Json.parseToJsonElement(it.json).jsonObject["type"]!!.jsonPrimitive.content }

    @Test
    fun greetingIsRequestedWhenConnected() {
        val c = RealtimeCoordinator()
        val out = c.onConnected()
        assertEquals(listOf("response.create"), sentTypes(out))
        assertEquals(VoicePhase.THINKING, c.phase)
    }

    @Test
    fun phasesFollowTheConversation() {
        val c = RealtimeCoordinator()
        c.onConnected()
        c.onEvent(ev("response.created"))
        c.onEvent(ev("output_audio_buffer.started"))
        assertEquals(VoicePhase.SPEAKING, c.phase)
        c.onEvent(ev("response.done"))
        assertEquals("audio still playing after generation finishes", VoicePhase.SPEAKING, c.phase)
        c.onEvent(ev("output_audio_buffer.stopped"))
        assertEquals(VoicePhase.LISTENING, c.phase)
        c.onEvent(ev("input_audio_buffer.speech_started"))
        assertEquals(VoicePhase.LISTENING, c.phase)
        c.onEvent(ev("input_audio_buffer.speech_stopped"))
        assertEquals(VoicePhase.THINKING, c.phase)
    }

    @Test
    fun userInterruptionClearsSpeakingState() {
        val c = RealtimeCoordinator()
        c.onEvent(ev("response.created"))
        c.onEvent(ev("output_audio_buffer.started"))
        c.onEvent(ev("input_audio_buffer.speech_started"))
        c.onEvent(ev("output_audio_buffer.cleared"))
        c.onEvent(ev("response.done"))
        assertEquals(VoicePhase.LISTENING, c.phase)
    }

    @Test
    fun toolCallIsRelayedAndResponseResumesOnlyAfterResponseDone() {
        val c = RealtimeCoordinator()
        c.onEvent(ev("response.created"))
        val call = c.onEvent(
            ev("response.function_call_arguments.done", ""","call_id":"call_1","name":"save_memory","arguments":"{\"content\":\"x\",\"category\":\"goal\"}"""")
        )
        val run = call.filterIsInstance<Effect.RunTool>().single()
        assertEquals("save_memory", run.name)
        assertEquals("x", run.arguments["content"]!!.jsonPrimitive.content)

        // Tool finishes while the original response is still open: output sent, no response.create yet.
        val result = c.onToolResult("call_1", """{"saved":true}""")
        assertEquals(listOf("conversation.item.create"), sentTypes(result))

        val done = c.onEvent(ev("response.done"))
        assertEquals(listOf("response.create"), sentTypes(done))
        assertEquals(VoicePhase.THINKING, c.phase)
    }

    @Test
    fun responseWaitsForAllParallelTools() {
        val c = RealtimeCoordinator()
        c.onEvent(ev("response.created"))
        c.onEvent(ev("response.function_call_arguments.done", ""","call_id":"a","name":"get_user_profile","arguments":"{}""""))
        c.onEvent(ev("response.function_call_arguments.done", ""","call_id":"b","name":"get_relevant_memories","arguments":"{}""""))
        c.onEvent(ev("response.done"))
        assertEquals(listOf("conversation.item.create"), sentTypes(c.onToolResult("a", "{}")))
        assertEquals(listOf("conversation.item.create", "response.create"), sentTypes(c.onToolResult("b", "{}")))
    }

    @Test
    fun functionOutputCarriesCallIdAndOutput() {
        val json = Json.parseToJsonElement(RealtimeCoordinator.functionOutput("call_9", """{"ok":true}""")).jsonObject
        val item = json["item"]!!.jsonObject
        assertEquals("function_call_output", item["type"]!!.jsonPrimitive.content)
        assertEquals("call_9", item["call_id"]!!.jsonPrimitive.content)
        assertEquals("""{"ok":true}""", item["output"]!!.jsonPrimitive.content)
    }

    @Test
    fun userTranscriptTriggersSafetyCheck() {
        val c = RealtimeCoordinator()
        val out = c.onEvent(ev("conversation.item.input_audio_transcription.completed", ""","item_id":"u1","transcript":" I feel awful """"))
        assertEquals(listOf(Effect.CheckSafety("I feel awful")), out.filterIsInstance<Effect.CheckSafety>())
    }

    @Test
    fun highRiskInterruptsCurrentReplyAndReasksWithGuidance() {
        val c = RealtimeCoordinator()
        c.onEvent(ev("response.created"))
        c.onEvent(ev("output_audio_buffer.started"))
        val out = c.onSafetyResult(TurnCheck(level = "HIGH", guidance = "SAFETY PRIORITY: ...", interrupt = true))
        assertEquals(listOf("response.cancel", "output_audio_buffer.clear", "conversation.item.create"), sentTypes(out))
        val system = Json.parseToJsonElement((out[2] as Effect.Send).json).jsonObject["item"]!!.jsonObject
        assertEquals("system", system["role"]!!.jsonPrimitive.content)

        // After the cancelled response finishes, a new one is requested with the guidance in place.
        assertEquals(listOf("response.create"), sentTypes(c.onEvent(ev("response.done"))))
    }

    @Test
    fun highRiskWhenIdleRespondsImmediately() {
        val c = RealtimeCoordinator()
        val out = c.onSafetyResult(TurnCheck(level = "IMMEDIATE", guidance = "SAFETY EMERGENCY", interrupt = true))
        assertEquals(listOf("conversation.item.create", "response.create"), sentTypes(out))
    }

    @Test
    fun moderateRiskOnlyAddsGuidance() {
        val c = RealtimeCoordinator()
        c.onEvent(ev("response.created"))
        val out = c.onSafetyResult(TurnCheck(level = "MODERATE", guidance = "SAFETY NOTE", interrupt = false))
        assertEquals(listOf("conversation.item.create"), sentTypes(out))
        assertTrue(sentTypes(c.onEvent(ev("response.done"))).isEmpty())
    }

    @Test
    fun lowRiskDoesNothing() {
        assertTrue(RealtimeCoordinator().onSafetyResult(TurnCheck(level = "LOW")).isEmpty())
    }

    @Test
    fun transcriptKeepsConversationOrderEvenWhenTranscriptionArrivesLate() {
        val c = RealtimeCoordinator()
        c.onEvent(ev("response.output_item.added", ""","item":{"id":"a0","type":"message","role":"assistant"}"""))
        c.onEvent(ev("response.output_audio_transcript.done", ""","item_id":"a0","transcript":"Hey Harshit. How are you feeling?""""))
        c.onEvent(ev("input_audio_buffer.committed", ""","item_id":"u1""""))
        c.onEvent(ev("response.output_item.added", ""","item":{"id":"a1","type":"message","role":"assistant"}"""))
        c.onEvent(ev("response.output_audio_transcript.done", ""","item_id":"a1","transcript":"That sounds heavy.""""))
        // User transcription completes after the assistant already answered.
        c.onEvent(ev("conversation.item.input_audio_transcription.completed", ""","item_id":"u1","transcript":"Pretty demotivated.""""))
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
    fun malformedEventsAreIgnored() {
        val c = RealtimeCoordinator()
        assertTrue(c.onEvent("not json").isEmpty())
        assertTrue(c.onEvent("""{"type":"something.new"}""").isEmpty())
        assertFalse(c.transcript().any())
    }
}
