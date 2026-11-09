package com.sprintstart.sprintstartbackend.assistant.external

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.util.UUID

/*
 * Reading what the model passed to a buddy tool.
 *
 * It is untrusted: a missing, blank or malformed value comes back empty or null, never as an exception,
 * so every tool can answer with a sentence the model can act on. What a confirmed proposal stores is not
 * read here but through the typed params of team mode's `TypedTeamAction`.
 */

/** A text field, trimmed; empty when missing or not text. */
internal fun JsonObject.text(name: String): String = (this[name] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

/** A UUID field; null when missing or not a UUID. */
internal fun JsonObject.uuid(name: String): UUID? = runCatching { UUID.fromString(text(name)) }.getOrNull()

/** A boolean field; null when missing or neither `true` nor `false`. */
internal fun JsonObject.boolean(name: String): Boolean? = (this[name] as? JsonPrimitive)?.booleanOrNull

/** The object entries of an array field; non-objects are dropped rather than failing the read. */
internal fun JsonObject.objectArray(name: String): List<JsonObject> =
    (this[name] as? JsonArray).orEmpty().filterIsInstance<JsonObject>()

/** The text entries of an array field, trimmed, with blanks dropped. */
internal fun JsonObject.textArray(name: String): List<String> =
    (this[name] as? JsonArray)
        .orEmpty()
        .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
        .filter { it.isNotEmpty() }
