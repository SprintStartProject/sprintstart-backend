package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.UUID

/*
 * Reading what the model passed to a team-mode tool, and what a stored proposal kept of it.
 *
 * Both are untrusted in the same way: a missing, blank or malformed value comes back empty or null,
 * never as an exception, so every tool can answer with a sentence the model can act on.
 */

/** A text argument of a tool call, trimmed; empty when missing or not text. */
internal fun BuddyToolCallDto.textArgument(name: String): String = arguments.text(name)

/** A UUID argument of a tool call; null when missing or not a UUID. */
internal fun BuddyToolCallDto.uuidArgument(name: String): UUID? = arguments.uuid(name)

/** A boolean argument of a tool call; null when missing or neither `true` nor `false`. */
internal fun BuddyToolCallDto.booleanArgument(name: String): Boolean? = arguments.boolean(name)

/** A text field of stored params, trimmed; empty when missing or not text. */
internal fun JsonObject.text(name: String): String = (this[name] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

/** A UUID field of stored params; null when missing or not a UUID. */
internal fun JsonObject.uuid(name: String): UUID? = runCatching { UUID.fromString(text(name)) }.getOrNull()

/** A boolean field of stored params; null when missing or neither `true` nor `false`. Models send `"true"` too. */
internal fun JsonObject.boolean(name: String): Boolean? = (this[name] as? JsonPrimitive)?.booleanOrNull

/**
 * The object entries of an array field; non-objects are dropped rather than failing the read.
 *
 * Read off `call.arguments` for a tool call and off the stored params for a confirm — the two are
 * the same shape, and an array the model sent is untrusted in both places.
 */
internal fun JsonObject.objectArray(name: String): List<JsonObject> =
    (this[name] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()

/** The text entries of a stored array field, trimmed, with blanks dropped. */
internal fun JsonObject.textArray(name: String): List<String> =
    (this[name] as? JsonArray)
        .orEmpty()
        .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
        .filter { it.isNotEmpty() }

/** A JSON schema of string fields, for a team tool's definition. */
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

private const val LABEL_CHARS = 60

/** Short enough for a confirm button. */
internal fun String.forLabel(): String = if (length <= LABEL_CHARS) this else take(LABEL_CHARS - 1).trimEnd() + "…"
