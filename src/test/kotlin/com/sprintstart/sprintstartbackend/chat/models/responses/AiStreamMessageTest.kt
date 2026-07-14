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
            """{"type": "citation", "artifact_id": "artifact-1", "start_line": 12, "start_page": null}"""

        val message = json.decodeFromString<AiStreamMessage>(raw)

        assertEquals("citation", message.type)
        assertEquals("artifact-1", message.artifactId)
        assertEquals(12, message.startLine)
        assertNull(message.startPage)
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
            artifactId = "artifact-1",
            startLine = 12,
        )

        assertEquals(
            """{"type":"citation","artifact_id":"artifact-1","start_line":12}""",
            json.encodeToString(message),
        )
    }

    @Test
    fun `serializes a backend-enriched citation event with filename and source_url`() {
        val message = AiStreamMessage(
            type = "citation",
            artifactId = "artifact-1",
            filename = "retro.md",
            sourceUrl = "https://github.com/example/retro.md",
            startLine = 12,
        )

        assertEquals(
            """{"type":"citation","artifact_id":"artifact-1","filename":"retro.md",""" +
                """"source_url":"https://github.com/example/retro.md","start_line":12}""",
            json.encodeToString(message),
        )
    }

    @Test
    fun `serializes a token event without the unrelated fields`() {
        val message = AiStreamMessage("token", "The main")

        assertEquals("""{"type":"token","content":"The main"}""", json.encodeToString(message))
    }
}
