package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.ArrivalDerivation
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.enums.Rigor
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ArrivalStep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.stereotype.Component

/**
 * The edits a confirmed update applies to the step [key] names.
 *
 * A null field is left alone. A blank [description] or [href] is a field the manager confirmed taking off,
 * which is why presence, not emptiness, decides.
 */
@Serializable
data class UpdateArrivalStepParams(
    val key: String,
    val title: String? = null,
    val description: String? = null,
    val href: String? = null,
    val position: Int? = null,
    @SerialName("settled_by") val settledBy: Rigor? = null,
)

/** Offers to change a step's wording, link, place or how it gets settled — never its key. */
@Component
class UpdateArrivalStepAction(
    private val arrivalStepService: ArrivalStepService,
) : TypedTeamAction<UpdateArrivalStepParams>(UpdateArrivalStepParams.serializer()) {
    override val area = TeamArea.ARRIVAL
    override val risk = BuddyProposalRisk.STANDARD
    override val spec = BuddyToolSpecDto(
        name = "update_arrival_step",
        description = "Offer to change one arrival step on this project: its title, description, link, place " +
            "in the list, or how it gets settled. The key cannot be changed — state is recorded against it, " +
            "so renaming would lose every hire's record of having done the step. To rename, offer a delete " +
            "and a create and say that is what it is. Pass only the fields you are changing, and use " +
            "clear_description or clear_href to take a description or link off entirely. This does NOT " +
            "change anything by itself.",
        parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("key") {
                    put("type", "string")
                    put("description", "The key from list_arrival_steps.")
                }
                putJsonObject("title") {
                    put("type", "string")
                    put("description", "Optional: the new title.")
                }
                putJsonObject("description") {
                    put("type", "string")
                    put("description", "Optional: the new description. To take one off, use clear_description.")
                }
                putJsonObject("clear_description") {
                    put("type", "boolean")
                    put("description", "Optional: true to leave the step with no description. Not with description.")
                }
                putJsonObject("href") {
                    put("type", "string")
                    put("description", "Optional: the new link. To take one off, use clear_href.")
                }
                putJsonObject("clear_href") {
                    put("type", "boolean")
                    put("description", "Optional: true to leave the step with no link. Not with href.")
                }
                putJsonObject("position") {
                    put("type", "string")
                    put(
                        "description",
                        "Optional: a 0-based place this list already runs to. Prefer reorder_arrival_steps.",
                    )
                }
                putJsonObject("settled_by") {
                    put("type", "string")
                    put("description", "Optional: observed, attested or declared.")
                }
            }
            putJsonArray("required") { add("key") }
        },
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val key = normalizeKey(call.textArgument("key"))
        val step = arrivalStepService.stepOn(key, context.projectId)
            ?: return TeamActionDraft.Refused(NO_SUCH_STEP)

        return when (val read = read(call, step, context)) {
            is Read.Refused -> TeamActionDraft.Refused(read.reason)
            is Read.Changes -> propose(step, key, read)
        }
    }

    private fun read(call: BuddyToolCallDto, step: ArrivalStep, context: TeamToolContext): Read {
        val position = when (val parsed = readPosition(call, step, context)) {
            is Parsed.Bad -> return Read.Refused(parsed.reason)
            is Parsed.Value -> parsed.value
        }
        val rigor = when (val parsed = readRigor(call, step.key)) {
            is Parsed.Bad -> return Read.Refused(parsed.reason)
            is Parsed.Value -> parsed.value
        }
        val description = when (val parsed = readClearable(call, "description", step.description)) {
            is Parsed.Bad -> return Read.Refused(parsed.reason)
            is Parsed.Value -> parsed.value
        }
        val href = when (val parsed = readClearable(call, "href", step.href)) {
            is Parsed.Bad -> return Read.Refused(parsed.reason)
            is Parsed.Value -> parsed.value
        }
        val changes = Read.Changes(
            title = call.textArgument("title").ifBlank { null },
            description = description,
            href = href,
            position = position,
            rigor = rigor,
        )
        return if (changes.isEmpty()) {
            Read.Refused("Nothing was given to change on that step.")
        } else {
            changes
        }
    }

    /**
     * A place in the list, refused when another step holds it or it lies past the end. Both leave an
     * ambiguous or gapped order, so they point to reorder_arrival_steps instead.
     */
    private fun readPosition(call: BuddyToolCallDto, step: ArrivalStep, context: TeamToolContext): Parsed<Int?> {
        val text = call.textArgument("position")
        if (text.isBlank()) return Parsed.Value(null)

        val position = text.toIntOrNull()?.takeIf { it >= 0 }
            ?: return Parsed.Bad("That is not a place in the list. Give a whole number from 0.")
        val steps = arrivalStepService.listForAuthoring(context.projectId)
        val occupant = occupantOf(position, step.key, steps)
        if (occupant != null) {
            return Parsed.Bad(
                "“${occupant.title}” is already at place $position. Offer reorder_arrival_steps with the whole " +
                    "list instead, so the order stays unambiguous.",
            )
        }
        if (position >= steps.size) {
            return Parsed.Bad(
                "This list has ${steps.size} steps, so its places run from 0 to ${steps.size - 1}. Offer " +
                    "reorder_arrival_steps with the whole list if the order should change.",
            )
        }
        return Parsed.Value(position)
    }

    /**
     * An optional field that can also be taken off: the new text, "" for taken off, or null for kept.
     *
     * Removing is its own flag, because a model passing an empty string usually means "unchanged".
     */
    private fun readClearable(call: BuddyToolCallDto, field: String, current: String?): Parsed<String?> {
        val given = call.textArgument(field)
        if (call.booleanArgument("clear_$field") != true) {
            return Parsed.Value(given.ifBlank { null })
        }
        if (given.isNotBlank()) {
            return Parsed.Bad(
                "Pass either $field or clear_$field, not both. One replaces what is there, the other takes it off.",
            )
        }
        return if (current == null) {
            Parsed.Bad("That step has no $field to take off.")
        } else {
            Parsed.Value("")
        }
    }

    /** How the step should settle, refused when the system already decides that for this key. */
    private fun readRigor(call: BuddyToolCallDto, key: String): Parsed<Rigor?> {
        val text = call.textArgument("settled_by")
        if (text.isBlank()) return Parsed.Value(null)

        val rigor = rigorOrNull(text)
            ?: return Parsed.Bad("That is not a way of settling a step. $RIGOR_VALUES")
        if (ArrivalDerivation.forStepKey(key) != null && rigor != Rigor.OBSERVED) {
            return Parsed.Bad(
                "That step is one the system checks itself, so it is always settled by observing it. Leave " +
                    "settled_by alone, or delete it and add one with a key the system does not check.",
            )
        }
        return Parsed.Value(rigor)
    }

    private fun lines(step: ArrivalStep, changes: Read.Changes): List<String> = buildList {
        changes.title?.let { add("Title: “${step.title}” becomes “$it”") }
        changes.description?.let { add(fieldLine("Description", step.description, it) { text -> "“$text”" }) }
        changes.href?.let { add(fieldLine("Link", step.href, it) { text -> text }) }
        changes.position?.let { add("Place in the list: ${step.position} becomes $it") }
        changes.rigor?.let { add("Settled by ${settlementWords(step.settledBy)} becomes ${settlementWords(it)}") }
    }

    private fun propose(step: ArrivalStep, key: String, changes: Read.Changes): TeamActionDraft {
        val lines = lines(step, changes)
        return proposed(
            params = UpdateArrivalStepParams(
                key,
                changes.title,
                changes.description,
                changes.href,
                changes.position,
                changes.rigor,
            ),
            label = "Update arrival step: ${step.title.forLabel()}",
            preview = buildString {
                appendLine("Change “${step.title}” [key: $key] on this project's arrival list:")
                appendLine()
                lines.forEach { appendLine("- $it") }
                appendLine()
                append("The key stays $key, so everybody's record of having done this step is kept.")
            }.trim(),
        )
    }

    /** The step is still here and its place is still free: [perform] sets the position unconditionally. */
    override fun recheckParams(params: UpdateArrivalStepParams, context: TeamToolContext): String? {
        val steps = arrivalStepService.listForAuthoring(context.projectId)
        val step = steps.firstOrNull { it.key == params.key } ?: return STEP_GONE_SINCE
        val position = params.position ?: return null

        val occupant = occupantOf(position, step.key, steps)
        if (occupant != null) {
            return "“${occupant.title}” took place $position in the meantime, so nothing was changed. Offer " +
                "the change again against the list as it now stands."
        }
        return if (position >= steps.size) {
            "This project's arrival list is shorter than it was, so place $position is past its end and nothing " +
                "was changed. Offer the change again against the list as it now stands."
        } else {
            null
        }
    }

    override suspend fun performParams(params: UpdateArrivalStepParams, context: TeamToolContext): String {
        arrivalStepService.update(
            key = params.key,
            projectId = context.projectId,
            title = params.title,
            description = params.description,
            href = params.href,
            position = params.position,
            settledBy = params.settledBy,
        )
        return "Updated. That step now reads as proposed on this project's arrival list."
    }

    private sealed interface Parsed<out T> {
        data class Value<T>(
            val value: T,
        ) : Parsed<T>

        data class Bad(
            val reason: String,
        ) : Parsed<Nothing>
    }

    private sealed interface Read {
        data class Refused(
            val reason: String,
        ) : Read

        data class Changes(
            val title: String?,
            val description: String?,
            val href: String?,
            val position: Int?,
            val rigor: Rigor?,
        ) : Read {
            fun isEmpty(): Boolean = listOfNotNull(title, description, href, position, rigor).isEmpty()
        }
    }
}
