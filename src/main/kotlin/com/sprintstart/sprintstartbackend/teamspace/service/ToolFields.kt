package com.sprintstart.sprintstartbackend.teamspace.service

import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * One argument of a content tool.
 *
 * The content tools take more than text: a place in a list, an amount of minutes, one of a fixed
 * set of values. [stringFields] can only say "string", which would leave a model guessing that
 * `type` must be `VIDEO`, `DOCUMENT` or `TASK`.
 */
internal data class ToolField(
    val name: String,
    val description: String,
    val type: String = "string",
    val values: List<String> = emptyList(),
)

/** A JSON schema of typed [fields], for a content tool's definition. */
internal fun toolFields(vararg fields: ToolField, required: List<String>): JsonObject =
    buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            fields.forEach { field ->
                putJsonObject(field.name) {
                    put("type", field.type)
                    put("description", field.description)
                    if (field.values.isNotEmpty()) putJsonArray("enum") { field.values.forEach { add(it) } }
                }
            }
        }
        putJsonArray("required") { required.forEach { add(it) } }
    }

/**
 * A 1-based place the model gave, as the 0-based position the services take.
 *
 * Null when it is not a whole number or lies outside the [slots] places there are. People and
 * models both count "the first phase" as 1; the services count from 0, and the conversion lives here
 * so that neither of them has to think about it.
 */
internal fun placeToPosition(place: String, slots: Int): Int? =
    place.toIntOrNull()?.minus(1)?.takeIf { it in 0 until slots }

/** A 0-based position said the way a person counts: "place 1 of 3". */
internal fun placeOf(position: Int, total: Int): String = "place ${position + 1} of $total"

/**
 * The new value of a text argument, or null when the call leaves it alone or only repeats what is
 * already there.
 *
 * An argument the model left out is not the same as one it sent empty: the second is a request to
 * clear the field, and is honoured unless [allowBlank] is false, as it is for a title.
 */
internal fun BuddyToolCallDto.changedText(name: String, now: String, allowBlank: Boolean = true): String? =
    textArgument(name).takeIf { arguments.containsKey(name) && (allowBlank || it.isNotEmpty()) && it != now }

/**
 * What the manager is shown of the edits one update call asks for, one line per field that changes.
 *
 * Only what actually changes is recorded, and the stored params keep the same fields: a confirm applies
 * them over the element as it is *then*, so a change made to any other field since the preview is not undone.
 */
internal class ChangeLines {
    private val lines = mutableListOf<String>()

    val isEmpty: Boolean get() = lines.isEmpty()

    /** Records the line describing [value], unless it is null: no change. */
    fun <T : Any> add(value: T?, line: (T) -> String) {
        if (value != null) lines += line(value)
    }

    fun preview(): String = lines.joinToString("\n")
}

/** A JSON schema of string fields, for a tool's definition. */
internal fun stringFields(vararg fields: Pair<String, String>, required: List<String>): JsonObject =
    buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            fields.forEach { (name, description) ->
                putJsonObject(name) {
                    put("type", "string")
                    put("description", description)
                }
            }
        }
        putJsonArray("required") { required.forEach { add(it) } }
    }
