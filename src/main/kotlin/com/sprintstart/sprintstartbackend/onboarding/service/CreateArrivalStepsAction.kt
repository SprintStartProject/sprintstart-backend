package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.ArrivalDerivation
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.enums.Rigor
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.stereotype.Component

/** One step of a confirmed batch. */
@Serializable
data class NewArrivalStepParams(
    val key: String,
    val title: String,
    val description: String? = null,
    val href: String? = null,
    @SerialName("settled_by") val settledBy: Rigor = Rigor.DECLARED,
)

/** The steps a confirmed batch adds, in order. */
@Serializable
data class CreateArrivalStepsParams(
    val steps: List<NewArrivalStepParams>,
)

/**
 * Offers to add one or more steps to the project's arrival list.
 *
 * A batch is one proposal and one confirm, applied in one transaction, so a taken key fails the whole batch.
 */
@Component
class CreateArrivalStepsAction(
    private val arrivalStepService: ArrivalStepService,
) : TypedTeamAction<CreateArrivalStepsParams>(CreateArrivalStepsParams.serializer()) {
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
        // Read to warn, not to offer: a project step replaces the company step of the same key for everybody.
        val companyTitles = arrivalStepService.listForAuthoring(null).associate { it.key to it.title }
        val drafted = mutableListOf<DraftedStep>()
        for (entry in requested) {
            val key = normalizeKey(entry.text("key"))
            if (!ArrivalStepService.KEY.matches(key)) {
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

        return proposed(
            params = CreateArrivalStepsParams(
                drafted.map { NewArrivalStepParams(it.key, it.title, it.description, it.href, it.rigor) },
            ),
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

    override fun recheckParams(params: CreateArrivalStepsParams, context: TeamToolContext): String? {
        val taken = arrivalStepService.listForAuthoring(context.projectId).map { it.key }.toSet()
        val clash = params.steps.map { it.key }.firstOrNull { it in taken }
        return clash?.let { "An arrival step '$it' was added since, so nothing was changed." }
    }

    override suspend fun performParams(params: CreateArrivalStepsParams, context: TeamToolContext): String {
        val steps = params.steps.map { step ->
            NewArrivalStep(
                key = step.key,
                title = step.title,
                description = step.description?.ifBlank { null },
                href = step.href?.ifBlank { null },
                settledBy = step.settledBy,
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
