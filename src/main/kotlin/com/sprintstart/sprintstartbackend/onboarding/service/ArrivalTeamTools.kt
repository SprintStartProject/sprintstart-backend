package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.Rigor
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ArrivalStep
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The read tools of team mode's arrival area: what a project expects to be true before somebody can
 * work, and which of those the system knows how to check for itself.
 *
 * Both read the turn's project and only it. Every arrival method takes a nullable `projectId` where
 * `null` means the organisation-wide default list; team mode never passes `null`, so that list is
 * not reachable from a conversation — a manager authors their project's list, not everybody's.
 */
@Component
class ArrivalTeamTools(
    private val arrivalStepService: ArrivalStepService,
) : TeamAreaTools {
    override val area = TeamArea.ARRIVAL

    override fun toolSpecs(): List<BuddyToolSpecDto> = listOf(LIST_ARRIVAL_STEPS_SPEC, LIST_DERIVABLE_STEPS_SPEC)

    override fun handles(toolName: String): Boolean = toolName == LIST_ARRIVAL_STEPS || toolName == LIST_DERIVABLE_STEPS

    override fun execute(call: BuddyToolCallDto, context: TeamToolContext): String =
        when (call.name) {
            LIST_ARRIVAL_STEPS -> arrivalSteps(context.projectId)
            LIST_DERIVABLE_STEPS -> derivableSteps(context.projectId)
            else -> "Unknown tool: ${call.name}."
        }

    /** In list order, because that order is the thing a manager reorders and has to be able to see. */
    private fun arrivalSteps(projectId: UUID): String {
        val steps = arrivalStepService.listForAuthoring(projectId)
        if (steps.isEmpty()) {
            return "This project has no arrival steps yet. Nothing is blocked by that — an empty list simply " +
                "means nobody has written down what a new hire needs in place."
        }
        return buildString {
            appendLine("Arrival steps on this project, in list order:")
            steps.forEach { appendLine("- ${render(it)}") }
        }.trim()
    }

    private fun render(step: ArrivalStep): String = buildString {
        append("${step.title} [key: ${step.key}]")
        append(" — settled by ${settlement(step)}")
        step.description?.let { append("\n  $it") }
        step.href?.let { append("\n  Link: $it") }
    }

    /**
     * How a step gets settled, in words rather than the enum name: the manager is deciding whether
     * this is something the system proves, somebody else signs off, or the hire says themselves.
     */
    private fun settlement(step: ArrivalStep): String {
        val how = when (step.settledBy) {
            Rigor.OBSERVED -> "the system observing it"
            Rigor.ATTESTED -> "somebody else attesting it"
            Rigor.DECLARED -> "the hire declaring it"
        }
        return if (step.selfConfirmable) how else "$how (the hire cannot tick this one themselves)"
    }

    private fun derivableSteps(projectId: UUID): String {
        val derivable = arrivalStepService.derivable(projectId)
        return buildString {
            appendLine(
                "Steps the system knows how to check by itself. Adding one of these keys makes the step " +
                    "settle on its own evidence rather than on somebody saying so:",
            )
            derivable.forEach { (derivation, alreadyOnList) ->
                append("- ${derivation.suggestedTitle} [key: ${derivation.stepKey}]")
                append(if (alreadyOnList) " — already on this project's list" else " — not on this project's list")
                appendLine()
                appendLine("  ${derivation.suggestedDescription}")
            }
        }.trim()
    }

    companion object {
        const val LIST_ARRIVAL_STEPS = "list_arrival_steps"
        const val LIST_DERIVABLE_STEPS = "list_derivable_steps"

        /**
         * The two rules the hire-side `get_arrival_steps` follows, repeated here because the model
         * writes the manager's wording from these descriptions and would otherwise invent urgency
         * the feature does not have.
         */
        private const val NOT_A_GATE =
            "An outstanding arrival step never blocks anybody from working, and arrival is never totalled " +
                "into a fraction or a percentage — do not present it as progress or as a gate."

        val LIST_ARRIVAL_STEPS_SPEC = BuddyToolSpecDto(
            name = LIST_ARRIVAL_STEPS,
            description = "This project's arrival list in order: what a new hire needs in place, each with its " +
                "key, how it gets settled, and any link. Read it before changing the list, and use the keys it " +
                "gives when you do. $NOT_A_GATE Takes no arguments — it always reads this project.",
            parameters = noArgs(),
        )

        val LIST_DERIVABLE_STEPS_SPEC = BuddyToolSpecDto(
            name = LIST_DERIVABLE_STEPS,
            description = "The arrival steps the system can check for itself, and whether each is already on " +
                "this project's list. Suggest one of these before inventing a step with the same meaning: a " +
                "derived step settles on real evidence instead of somebody ticking a box. Takes no arguments.",
            parameters = noArgs(),
        )

        private fun noArgs() = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject { })
        }
    }
}
