package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID

internal class ToolCallArgumentsTest {
    private fun call(arguments: JsonObject) = BuddyToolCallDto(id = "c1", name = "tool", arguments = arguments)

    @Test
    fun `a text argument comes back trimmed`() {
        assertEquals(
            "deploy on Thursday",
            call(
                buildJsonObject {
                    put("text", "  deploy on Thursday \n")
                },
            ).textArgument("text"),
        )
    }

    @Test
    fun `a missing or non-text argument reads as empty text`() {
        val arguments = buildJsonObject {
            put("number", 3)
            putJsonArray("list") { add("a") }
        }

        assertEquals("", call(arguments).textArgument("missing"))
        assertEquals("3", call(arguments).textArgument("number"))
        assertEquals("", call(arguments).textArgument("list"))
    }

    @Test
    fun `a uuid argument parses, padded or not`() {
        val id = UUID.randomUUID()
        val arguments = buildJsonObject {
            put("plain", id.toString())
            put("padded", " $id ")
        }

        assertEquals(id, call(arguments).uuidArgument("plain"))
        assertEquals(id, call(arguments).uuidArgument("padded"))
    }

    @Test
    fun `a missing or malformed uuid argument is null`() {
        val arguments = buildJsonObject { put("card_id", "the second card") }

        assertNull(call(arguments).uuidArgument("card_id"))
        assertNull(call(arguments).uuidArgument("missing"))
    }

    @Test
    fun `a text list argument trims its entries and drops blank and non-text ones`() {
        val arguments = buildJsonObject {
            putJsonArray("items") {
                add(" write the runbook ")
                add("   ")
                add(buildJsonObject { put("text", "nested") })
                add("rotate the keys")
            }
            put("single", "not a list")
        }

        assertEquals(listOf("write the runbook", "rotate the keys"), call(arguments).textListArgument("items"))
        assertEquals(emptyList<String>(), call(arguments).textListArgument("single"))
        assertEquals(emptyList<String>(), call(arguments).textListArgument("missing"))
    }

    @Test
    fun `a boolean argument is null unless it is true or false`() {
        val arguments = buildJsonObject {
            put("yes", true)
            put("word", "maybe")
        }

        assertEquals(true, call(arguments).booleanArgument("yes"))
        assertNull(call(arguments).booleanArgument("word"))
        assertNull(call(arguments).booleanArgument("missing"))
    }
}
