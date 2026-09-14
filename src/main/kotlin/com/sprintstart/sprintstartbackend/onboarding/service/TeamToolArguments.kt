package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
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

/** A text field of stored params, trimmed; empty when missing or not text. */
internal fun JsonObject.text(name: String): String = (this[name] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

/** A UUID field of stored params; null when missing or not a UUID. */
internal fun JsonObject.uuid(name: String): UUID? = runCatching { UUID.fromString(text(name)) }.getOrNull()
