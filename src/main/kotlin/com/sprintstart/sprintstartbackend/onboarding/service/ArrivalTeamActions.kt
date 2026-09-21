package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.ArrivalDerivation
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.enums.Rigor
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ArrivalStep
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.stereotype.Component
import java.util.UUID

/*
 * The actions of team mode's arrival area.
 *
 * Every arrival service method takes a nullable `projectId`, and `null` is the organisation-wide
 * default list. Team mode passes `context.projectId` and never `null`, so nothing here can read or
 * write that list — a key a model invents names a step in this project's scope or names nothing.
 *
 * Keys are the identity here, not ids, and the service will not rename one: state is recorded
 * against the key, so changing it would orphan every hire's record of having done the step while
 * leaving the row looking healthy. Renaming is therefore delete plus create, and the tools say so
 * rather than quietly doing half of it.
 */

/** The key regex the service enforces, applied here so a malformed key is a sentence, not a 400. */
private val KEY = Regex("^[a-z\\d][a-z\\d_-]{0,63}$")

/** The step [key] names on [projectId], or null. */
private fun ArrivalStepService.stepOn(key: String, projectId: UUID): ArrivalStep? =
    listForAuthoring(projectId).firstOrNull { it.key == key }

/** A model-supplied key, lower-cased and trimmed the way the service will store it. */
private fun normalizeKey(raw: String): String = raw.trim().lowercase()

/**
 * What a step with this key will actually be settled by, whatever was asked for.
 *
 * A key the system knows how to check is forced to [Rigor.OBSERVED] on create. The preview has to
 * show that rather than the request, or the manager confirms one thing and gets another.
 */
private fun effectiveRigor(key: String, requested: Rigor): Rigor =
    if (ArrivalDerivation.forStepKey(key) != null) Rigor.OBSERVED else requested

private fun rigorOrNull(raw: String): Rigor? =
    Rigor.entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }

private fun settlementWords(rigor: Rigor): String =
    when (rigor) {
        Rigor.OBSERVED -> "the system observing it"
        Rigor.ATTESTED -> "somebody else attesting it"
        Rigor.DECLARED -> "the hire declaring it"
    }

private const val NO_SUCH_STEP =
    "This project has no arrival step with that key. Call list_arrival_steps for the keys it does have."

private const val GONE_SINCE = "That arrival step was changed or removed since, so nothing was changed."

private const val RIGOR_VALUES = "Must be one of observed, attested or declared."

/**
 * Offers to add one or more steps to the project's arrival list.
 *
 * A batch is one proposal and one confirm, because a manager writing an arrival list is agreeing to
 * a list rather than to each line of it. The service applies the batch in one transaction, so a key
 * that turns out to be taken takes the whole batch down rather than leaving a partial list.
 */
@Component
class CreateArrivalStepsAction(
    private val arrivalStepService: ArrivalStepService,
) : TeamActionHandler {
    override val area = TeamArea.ARRIVAL
    override val risk = BuddyProposalRisk.STANDARD
    override val spec = BuddyToolSpecDto(
        name = "create_arrival_steps",
        description = "Offer to add one or more steps to this project's arrival list — what should be in " +
            "place before somebody starts working. Call list_arrival_steps first so you do not repeat one, " +
            "and list_derivable_steps so you use a key the system can check instead of inventing a similar " +
            "one. Several steps are one offer the manager confirms once. This does NOT add anything by " +
            "itself. An arrival step never blocks anybody and is never shown as a fraction.",
        parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("steps") {
                    put("type", "array")
                    put("description", "The steps to add, in the order they should appear.")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("key") {
                                put("type", "string")
                                put(
                                    "description",
                                    "A short stable identifier: lower-case letters, digits, '-' and '_'. " +
                                        "Use the key from list_derivable_steps when one fits.",
                                )
                            }
                            putJsonObject("title") {
                                put("type", "string")
                                put("description", "What the hire has to do, in their words.")
                            }
                            putJsonObject("description") {
                                put("type", "string")
                                put("description", "Optional: why it matters, or how to do it.")
                            }
                            putJsonObject("href") {
                                put("type", "string")
                                put("description", "Optional: a link to where it gets done.")
                            }
                            putJsonObject("settled_by") {
                                put("type", "string")
                                put(
                                    "description",
                                    "Optional, defaults to declared. observed, attested or declared. A key " +
                                        "the system can check is always observed, whatever is asked for.",
                                )
                            }
                        }
                        putJsonArray("required") {
                            add("key")
                            add("title")
                        }
                    }
                }
            }
            putJsonArray("required") { add("steps") }
        },
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val requested = call.arguments.objectArray("steps")
        if (requested.isEmpty()) {
            return TeamActionDraft.Refused("No steps were given, so there is nothing to offer.")
        }

        val taken = arrivalStepService.listForAuthoring(context.projectId).map { it.key }.toSet()
        // The company-wide list, read to warn rather than to offer. A project-scoped step wins its
        // key outright -- `resolve` drops the company step of the same key from the hire's list --
        // so a manager adding "vpn" over a company "vpn" silently replaces it for everybody here.
        // That is the manager's own decision to make, but only if the preview says it is being made.
        // Nothing of this list reaches the model except the title of a step it collided with, which
        // the manager already sees on their own arrival list.
        val companyTitles = arrivalStepService.listForAuthoring(null).associate { it.key to it.title }
        val drafted = mutableListOf<DraftedStep>()
        for (entry in requested) {
            val key = normalizeKey(entry.text("key"))
            if (!KEY.matches(key)) {
                return TeamActionDraft.Refused(
                    "'${entry.text("key")}' is not a usable key. Keys are lower-case letters, digits, '-' " +
                        "and '_', starting with a letter or digit.",
                )
            }
            if (key in taken) {
                return TeamActionDraft.Refused(
                    "This project already has an arrival step '$key'. Offer an update to that one instead, " +
                        "or choose a different key.",
                )
            }
            if (drafted.any { it.key == key }) {
                return TeamActionDraft.Refused("The same key '$key' appears twice in one batch.")
            }
            val title = entry.text("title")
            if (title.isBlank()) {
                return TeamActionDraft.Refused("The step '$key' has no title, and a step needs one.")
            }
            val requestedRigor = entry.text("settled_by").ifBlank { Rigor.DECLARED.name }
            val rigor = rigorOrNull(requestedRigor)
                ?: return TeamActionDraft.Refused("'$requestedRigor' is not a way of settling a step. $RIGOR_VALUES")
            drafted += DraftedStep(
                key = key,
                title = title,
                description = entry.text("description").ifBlank { null },
                href = entry.text("href").ifBlank { null },
                rigor = effectiveRigor(key, rigor),
                replaces = companyTitles[key],
            )
        }

        return TeamActionDraft.Proposed(
            params = buildJsonObject {
                putJsonArray("steps") {
                    drafted.forEach { step ->
                        addJsonObject {
                            put("key", step.key)
                            put("title", step.title)
                            step.description?.let { put("description", it) }
                            step.href?.let { put("href", it) }
                            put("settled_by", step.rigor.name)
                        }
                    }
                }
            },
            label = if (drafted.size == 1) {
                "Add arrival step: ${drafted.single().title.forLabel()}"
            } else {
                "Add ${drafted.size} arrival steps"
            },
            preview = preview(drafted),
        )
    }

    private fun preview(drafted: List<DraftedStep>): String = buildString {
        appendLine(
            if (drafted.size == 1) {
                "Add this to the end of the project's arrival list:"
            } else {
                "Add these ${drafted.size} to the end of the project's arrival list, in this order:"
            },
        )
        drafted.forEach { step ->
            appendLine()
            appendLine("${step.title} [key: ${step.key}]")
            step.description?.let { appendLine(it) }
            step.href?.let { appendLine("Link: $it") }
            append("Settled by ${settlementWords(step.rigor)}")
            if (ArrivalDerivation.forStepKey(step.key) != null) {
                append(" — the system checks this key itself, so it is observed whatever was asked for")
            }
            appendLine(".")
            step.replaces?.let {
                appendLine(
                    "This takes the place of the company-wide step “$it” for everyone on this project — " +
                        "they see this one instead.",
                )
            }
        }
        appendLine()
        append(
            "Everyone on this project sees these on their arrival list. An outstanding step does not stop " +
                "anybody working.",
        )
    }.trim()

    override fun recheck(params: JsonObject, context: TeamToolContext): String? {
        val taken = arrivalStepService.listForAuthoring(context.projectId).map { it.key }.toSet()
        val clash = params.objectArray("steps").map { it.text("key") }.firstOrNull { it in taken }
        return clash?.let { "An arrival step '$it' was added since, so nothing was changed." }
    }

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        val steps = params.objectArray("steps").map { step ->
            NewArrivalStep(
                key = step.text("key"),
                title = step.text("title"),
                description = step.text("description").ifBlank { null },
                href = step.text("href").ifBlank { null },
                settledBy = rigorOrNull(step.text("settled_by")) ?: Rigor.DECLARED,
            )
        }
        arrivalStepService.createAll(context.projectId, steps)
        return if (steps.size == 1) {
            "Added. “${steps.single().title}” is now on this project's arrival list."
        } else {
            "Added ${steps.size} steps to this project's arrival list."
        }
    }

    private data class DraftedStep(
        val key: String,
        val title: String,
        val description: String?,
        val href: String?,
        val rigor: Rigor,
        /** The company-wide step this one would take the place of for this project, if any. */
        val replaces: String?,
    )
}

/** Offers to change a step's wording, link, place or how it gets settled — never its key. */
@Component
class UpdateArrivalStepAction(
    private val arrivalStepService: ArrivalStepService,
) : TeamActionHandler {
    override val area = TeamArea.ARRIVAL
    override val risk = BuddyProposalRisk.STANDARD
    override val spec = BuddyToolSpecDto(
        name = "update_arrival_step",
        description = "Offer to change one arrival step on this project: its title, description, link, place " +
            "in the list, or how it gets settled. The key cannot be changed — state is recorded against it, " +
            "so renaming would lose every hire's record of having done the step. To rename, offer a delete " +
            "and a create and say that is what it is. Pass only the fields you are changing. This does NOT " +
            "change anything by itself.",
        parameters = stringFields(
            "key" to "The key from list_arrival_steps.",
            "title" to "Optional: the new title.",
            "description" to "Optional: the new description.",
            "href" to "Optional: the new link.",
            "position" to "Optional: a 0-based place in the list. Prefer reorder_arrival_steps for ordering.",
            "settled_by" to "Optional: observed, attested or declared.",
            required = listOf("key"),
        ),
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

    /** The fields the call actually changes, or the first reason it cannot be offered. */
    private fun read(call: BuddyToolCallDto, step: ArrivalStep, context: TeamToolContext): Read {
        val position = when (val parsed = readPosition(call, step, context)) {
            is Parsed.Bad -> return Read.Refused(parsed.reason)
            is Parsed.Value -> parsed.value
        }
        val rigor = when (val parsed = readRigor(call, step.key)) {
            is Parsed.Bad -> return Read.Refused(parsed.reason)
            is Parsed.Value -> parsed.value
        }
        val changes = Read.Changes(
            title = call.textArgument("title").ifBlank { null },
            description = call.textArgument("description").ifBlank { null },
            href = call.textArgument("href").ifBlank { null },
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
     * A place in the list, refused when another step already holds it.
     *
     * Two steps on one position order arbitrarily against each other, and a manager who confirmed a
     * place would not be able to tell that is what happened. Reordering is the operation that keeps
     * the list coherent, so a collision points there instead of being created quietly.
     */
    private fun readPosition(call: BuddyToolCallDto, step: ArrivalStep, context: TeamToolContext): Parsed<Int?> {
        val text = call.textArgument("position")
        if (text.isBlank()) return Parsed.Value(null)

        val position = text.toIntOrNull()?.takeIf { it >= 0 }
            ?: return Parsed.Bad("That is not a place in the list. Give a whole number from 0.")
        val occupant = arrivalStepService
            .listForAuthoring(context.projectId)
            .firstOrNull { it.position == position && it.key != step.key }
            ?: return Parsed.Value(position)

        return Parsed.Bad(
            "“${occupant.title}” is already at place $position. Offer reorder_arrival_steps with the whole " +
                "list instead, so the order stays unambiguous.",
        )
    }

    /** How the step should settle, refused when the system already decides that for this key. */
    private fun readRigor(call: BuddyToolCallDto, key: String): Parsed<Rigor?> {
        val text = call.textArgument("settled_by")
        if (text.isBlank()) return Parsed.Value(null)

        val rigor = rigorOrNull(text)
            ?: return Parsed.Bad("That is not a way of settling a step. $RIGOR_VALUES")
        // The same rule create applies: a key the system checks is observed, and a manager confirming
        // "declared" on one would be agreeing to something that cannot hold.
        if (ArrivalDerivation.forStepKey(key) != null && rigor != Rigor.OBSERVED) {
            return Parsed.Bad(
                "That step is one the system checks itself, so it is always settled by observing it. Leave " +
                    "settled_by alone, or delete it and add one with a key the system does not check.",
            )
        }
        return Parsed.Value(rigor)
    }

    private fun propose(step: ArrivalStep, key: String, changes: Read.Changes): TeamActionDraft {
        val lines = buildList {
            changes.title?.let { add("Title: “${step.title}” becomes “$it”") }
            changes.description?.let { add("Description: “${step.description ?: "(none)"}” becomes “$it”") }
            changes.href?.let { add("Link: ${step.href ?: "(none)"} becomes $it") }
            changes.position?.let { add("Place in the list: ${step.position} becomes $it") }
            changes.rigor?.let {
                add("Settled by ${settlementWords(step.settledBy)} becomes ${settlementWords(it)}")
            }
        }
        return TeamActionDraft.Proposed(
            params = buildJsonObject {
                put("key", key)
                changes.title?.let { put("title", it) }
                changes.description?.let { put("description", it) }
                changes.href?.let { put("href", it) }
                changes.position?.let { put("position", it) }
                changes.rigor?.let { put("settled_by", it.name) }
            },
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

    override fun recheck(params: JsonObject, context: TeamToolContext): String? =
        GONE_SINCE.takeIf { arrivalStepService.stepOn(params.text("key"), context.projectId) == null }

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        val key = params.text("key")
        arrivalStepService.update(
            key = key,
            projectId = context.projectId,
            title = params.text("title").ifBlank { null },
            description = params.text("description").ifBlank { null },
            href = params.text("href").ifBlank { null },
            position = params.text("position").toIntOrNull(),
            settledBy = rigorOrNull(params.text("settled_by")),
        )
        return "Updated. That step now reads as proposed on this project's arrival list."
    }

    /** One optional field, read or refused. */
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

/**
 * Offers a whole new order for the project's arrival list.
 *
 * The complete list, never a move: the service takes the full ordering so two people reordering
 * concurrently cannot interleave into an order neither chose, and a partial list would leave the
 * steps nobody named sitting on positions that now collide.
 */
@Component
class ReorderArrivalStepsAction(
    private val arrivalStepService: ArrivalStepService,
) : TeamActionHandler {
    override val area = TeamArea.ARRIVAL
    override val risk = BuddyProposalRisk.STANDARD
    override val spec = BuddyToolSpecDto(
        name = "reorder_arrival_steps",
        description = "Offer a new order for this project's arrival list. Pass every key the project has, " +
            "once each, in the order they should appear — call list_arrival_steps first and reorder that " +
            "list. A partial list is refused. This does NOT reorder anything by itself.",
        parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("ordered_keys") {
                    put("type", "array")
                    put("description", "Every arrival step key on this project, once each, in the new order.")
                    putJsonObject("items") { put("type", "string") }
                }
            }
            putJsonArray("required") { add("ordered_keys") }
        },
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val steps = arrivalStepService.listForAuthoring(context.projectId)
        if (steps.isEmpty()) {
            return TeamActionDraft.Refused("This project has no arrival steps to reorder.")
        }
        val ordered = call.arguments.textArray("ordered_keys").map { normalizeKey(it) }
        val refusal = disagreement(ordered, steps.map { it.key })
        if (refusal != null) {
            return TeamActionDraft.Refused(refusal)
        }
        if (ordered == steps.map { it.key }) {
            return TeamActionDraft.Refused("That is the order the list is already in.")
        }

        val titles = steps.associate { it.key to it.title }
        return TeamActionDraft.Proposed(
            params = buildJsonObject { putJsonArray("ordered_keys") { ordered.forEach { add(it) } } },
            label = "Reorder ${ordered.size} arrival steps",
            preview = buildString {
                appendLine("Put this project's arrival list in this order:")
                appendLine()
                ordered.forEachIndexed { index, key -> appendLine("${index + 1}. ${titles[key]} [key: $key]") }
                appendLine()
                append("Only the order changes. No step is added, removed or reworded.")
            }.trim(),
        )
    }

    /** Why [ordered] is not a rearrangement of [present], or null when it is one. */
    private fun disagreement(ordered: List<String>, present: List<String>): String? {
        val duplicate = ordered
            .groupingBy { it }
            .eachCount()
            .entries
            .firstOrNull { it.value > 1 }
        if (duplicate != null) {
            return "'${duplicate.key}' appears ${duplicate.value} times. Each key goes in the list once."
        }
        val unknown = ordered.firstOrNull { it !in present }
        if (unknown != null) {
            return "This project has no arrival step '$unknown'. Call list_arrival_steps for the keys it has."
        }
        val missing = present.filter { it !in ordered }
        if (missing.isNotEmpty()) {
            return "The order left out ${missing.joinToString(", ") { "'$it'" }}. Pass every key, once each, " +
                "so the whole list is accounted for."
        }
        return null
    }

    override fun recheck(params: JsonObject, context: TeamToolContext): String? {
        val present = arrivalStepService.listForAuthoring(context.projectId).map { it.key }
        val ordered = params.textArray("ordered_keys")
        return "The arrival list changed since, so the order was not applied."
            .takeIf { disagreement(ordered, present) != null }
    }

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        val ordered = params.textArray("ordered_keys")
        arrivalStepService.reorder(context.projectId, ordered)
        return "Reordered. This project's arrival list now runs in the order you confirmed."
    }
}

/** Offers to take a step off the project's arrival list. */
@Component
class DeleteArrivalStepAction(
    private val arrivalStepService: ArrivalStepService,
) : TeamActionHandler {
    override val area = TeamArea.ARRIVAL
    override val risk = BuddyProposalRisk.DESTRUCTIVE
    override val spec = BuddyToolSpecDto(
        name = "delete_arrival_step",
        description = "Offer to remove a step from this project's arrival list. Prefer updating a step that " +
            "is merely worded badly. This does NOT remove anything by itself; the manager confirms.",
        parameters = stringFields(
            "key" to "The key from list_arrival_steps.",
            required = listOf("key"),
        ),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val key = normalizeKey(call.textArgument("key"))
        val step = arrivalStepService.stepOn(key, context.projectId)
            ?: return TeamActionDraft.Refused(NO_SUCH_STEP)

        // Removing a project step does not always remove a step. The project's definition was
        // winning this key outright, so deleting it lets the company-wide one of the same key back
        // onto every hire's list here -- a different step, with different wording and possibly a
        // different way of settling. "It stops appearing" would be untrue in exactly that case.
        val restored = arrivalStepService.listForAuthoring(null).firstOrNull { it.key == key }

        return TeamActionDraft.Proposed(
            params = buildJsonObject { put("key", key) },
            label = "Remove arrival step: ${step.title.forLabel()}",
            preview = buildString {
                appendLine("Remove “${step.title}” [key: $key] from this project's arrival list.")
                appendLine()
                if (restored == null) {
                    appendLine("It stops appearing for everyone on this project.")
                } else {
                    appendLine(
                        "This project's version stops applying, and the company-wide step “${restored.title}” " +
                            "takes its place for everyone here — the step does not disappear, it reverts.",
                    )
                }
                append(
                    "What hires already did is not destroyed: their record is kept against the key '$key', so " +
                        "adding a step with that key back restores it.",
                )
            }.trim(),
        )
    }

    override fun recheck(params: JsonObject, context: TeamToolContext): String? =
        GONE_SINCE.takeIf { arrivalStepService.stepOn(params.text("key"), context.projectId) == null }

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        val key = params.text("key")
        arrivalStepService.delete(key, context.projectId)
        return "Removed. '$key' is off this project's arrival list, and hires' records of it are kept."
    }
}
