package com.sprintstart.sprintstartbackend.path.assistant

import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.HireActionOutcome
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.UUID

private const val NOT_PROPOSED = "NOT PROPOSED — no button was shown to the hire, so do not tell " +
    "them to confirm anything. "

/**
 * A reason and no button, for the path actions to give the mentor.
 *
 * Prefixed, and that prefix is load-bearing. A refusal phrased as ordinary guidance came back
 * from testing as the mentor telling the hire to click a button that was never rendered: from the
 * model's side a tool result is a tool result, and "say what doing it involves and offer it again"
 * reads a lot like "offered". The prefix says the one thing it has to know -- that nothing is on
 * screen -- before the advice it should act on.
 */
internal fun notProposed(reason: String) = HireActionOutcome(NOT_PROPOSED + reason, null)

/** Reads a list of strings the model passed, tolerating a single string where a list was asked for. */
internal fun BuddyToolCallDto.lenientTextListArgument(name: String): List<String> =
    when (val value = arguments[name]) {
        is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
        is JsonPrimitive -> listOfNotNull(value.contentOrNull?.trim())
        else -> emptyList()
    }.filter { it.isNotEmpty() }

/**
 * Reads a list of UUIDs the model passed, tolerating a single string where a list was asked for.
 * Anything that is not a UUID is dropped here and caught by the placement check as "not in this
 * phase" only if it parsed -- an unparseable id is simply not a placement.
 */
internal fun BuddyToolCallDto.lenientUuidSetArgument(name: String): Set<UUID> {
    val raw = when (val value = arguments[name]) {
        is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        is JsonPrimitive -> listOfNotNull(value.contentOrNull)
        else -> emptyList()
    }
    return raw.mapNotNull { runCatching { UUID.fromString(it.trim()) }.getOrNull() }.toSet()
}
