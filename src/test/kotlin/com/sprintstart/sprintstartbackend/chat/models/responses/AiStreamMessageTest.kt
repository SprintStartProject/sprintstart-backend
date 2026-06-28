package com.sprintstart.sprintstartbackend.chat.models.responses

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Verifies that [AiStreamMessage] (de)serializes against the AI service SSE contract.
 *
 * The backend is a transparent passthrough: it deserializes events coming from the AI
 * service and re-serializes them for the client, so both directions must preserve the
 * exact wire field names (snake_case where applicable) and omit irrelevant fields.
 */
class AiStreamMessageTest {
    // The application shares a single Json with ignoreUnknownKeys; defaults are omitted.
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `deserializes a tool_use event`() {
        val raw = """{"type": "tool_use", "name": "retrieve", "kind": "tool"}"""

        val message = json.decodeFromString<AiStreamMessage>(raw)

        assertEquals("tool_use", message.type)
        assertEquals("retrieve", message.name)
        assertEquals("tool", message.kind)
        assertNull(message.content)
    }

    @Test
    fun `deserializes a citation event with snake_case fields`() {
        val raw =
            """{"type": "citation", "chunk_id": "chunk-1", "filename": "retro.md", "section_path": "Retro > Blockers"}"""

        val message = json.decodeFromString<AiStreamMessage>(raw)

        assertEquals("citation", message.type)
        assertEquals("chunk-1", message.chunkId)
        assertEquals("retro.md", message.filename)
        assertEquals("Retro > Blockers", message.sectionPath)
    }

    @Test
    fun `deserializes an error event using the message field`() {
        val raw = """{"type": "error", "message": "LLM backend unreachable"}"""

        val message = json.decodeFromString<AiStreamMessage>(raw)

        assertEquals("error", message.type)
        assertEquals("LLM backend unreachable", message.message)
    }

    @Test
    fun `serializes a tool_use event back to the AI service wire shape`() {
        val message = AiStreamMessage(type = "tool_use", name = "retrieve", kind = "tool")

        assertEquals(
            """{"type":"tool_use","name":"retrieve","kind":"tool"}""",
            json.encodeToString(message),
        )
    }

    @Test
    fun `serializes a citation event with snake_case wire names and omits null fields`() {
        val message = AiStreamMessage(
            type = "citation",
            chunkId = "chunk-1",
            filename = "retro.md",
            sectionPath = "Retro > Blockers",
        )

        assertEquals(
            """{"type":"citation","chunk_id":"chunk-1","filename":"retro.md","section_path":"Retro > Blockers"}""",
            json.encodeToString(message),
        )
    }

    @Test
    fun `serializes a token event without the unrelated fields`() {
        val message = AiStreamMessage("token", "The main")

        assertEquals("""{"type":"token","content":"The main"}""", json.encodeToString(message))
    }
}
