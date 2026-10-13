package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import kotlinx.serialization.Serializable
import org.springframework.stereotype.Component

/** The arrival step, by key, that a confirmed deletion removes. */
@Serializable
data class DeleteArrivalStepParams(
    val key: String,
)

/** Offers to take a step off the project's arrival list. */
@Component
class DeleteArrivalStepAction(
    private val arrivalStepService: ArrivalStepService,
) : TypedTeamAction<DeleteArrivalStepParams>(DeleteArrivalStepParams.serializer()) {
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

        // Deleting a project step lets the company step of the same key back onto every hire's list.
        val restored = arrivalStepService.listForAuthoring(null).firstOrNull { it.key == key }

        return proposed(
            params = DeleteArrivalStepParams(key),
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

    override fun recheckParams(params: DeleteArrivalStepParams, context: TeamToolContext): String? =
        STEP_GONE_SINCE.takeIf { arrivalStepService.stepOn(params.key, context.projectId) == null }

    override suspend fun performParams(params: DeleteArrivalStepParams, context: TeamToolContext): String {
        val key = params.key
        arrivalStepService.delete(key, context.projectId)
        return "Removed. '$key' is off this project's arrival list, and hires' records of it are kept."
    }
}
