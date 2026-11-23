package com.sprintstart.sprintstartbackend.teamspace.service

import com.sprintstart.sprintstartbackend.assistant.external.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.blueprint.external.enums.StepType
import com.sprintstart.sprintstartbackend.path.external.StepAuthoringApi
import com.sprintstart.sprintstartbackend.path.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.path.external.model.PathElementKind
import com.sprintstart.sprintstartbackend.path.model.request.step.CreateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.path.model.request.step.UpdateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.shared.ids.PhaseId
import com.sprintstart.sprintstartbackend.shared.ids.StepId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.springframework.stereotype.Component
import java.util.UUID

/*
 * The step actions of team mode's content area.
 *
 * Steps are where a person's progress lives: a step is waiting, in progress, finished or skipped.
 * None of these actions sets that. A new step starts waiting, an edit leaves the status alone, and
 * a delete says in its preview what it throws away.
 */

private val STEP_TYPES = StepType.entries.map { it.name }

private const val DEFAULT_MINUTES = 15

private fun stepTypeOf(text: String): StepType? = StepType.entries.firstOrNull {
    it.name.equals(text, ignoreCase = true)
}

/** What a preview says about the step's status when it matters to the person. */
private fun StepStatus?.aboutProgress(name: String): String? =
    when (this) {
        StepStatus.IN_PROGRESS -> "$name is working on it right now."
        StepStatus.FINISHED -> "$name has already finished it; that progress is deleted with it."
        StepStatus.SKIPPED -> "$name has already skipped it; that decision is deleted with it."
        else -> null
    }

/** The step to add to a phase, and where it goes. */
@Serializable
data class AddStepParams(
    @SerialName("phase_id") @Serializable(with = UuidSerializer::class) val phaseId: UUID,
    val title: String,
    val description: String,
    val type: StepType,
    @SerialName("estimated_minutes") val estimatedMinutes: Int,
    @SerialName("expected_outcome") val expectedOutcome: String,
    val position: Int,
)

/** The edits a confirmed step update applies; a null field is left as the step has it by then. */
@Serializable
data class UpdateStepParams(
    @SerialName("step_id") @Serializable(with = UuidSerializer::class) val stepId: UUID,
    val title: String? = null,
    val description: String? = null,
    val type: StepType? = null,
    @SerialName("estimated_minutes") val estimatedMinutes: Int? = null,
    @SerialName("expected_outcome") val expectedOutcome: String? = null,
    val position: Int? = null,
)

/** The step a confirmed deletion removes. */
@Serializable
data class DeleteStepParams(
    @SerialName("step_id") @Serializable(with = UuidSerializer::class) val stepId: UUID,
)

/** Offers to add a step to a phase of a member's path. */
@Component
class AddStepAction(
    private val scope: ContentScope,
    private val stepAuthoringApi: StepAuthoringApi,
) : TypedTeamAction<AddStepParams>(AddStepParams.serializer()) {
    override val area = TeamArea.CONTENT
    override val risk = BuddyProposalRisk.STANDARD
    override val spec = BuddyToolSpecDto(
        name = "add_step",
        description = "Offer to add a step to a phase of a member's onboarding path. Use the phase_id from " +
            "get_member_path. It starts waiting, like every new step; this cannot mark anything done. Omit " +
            "place to add it at the end of the phase. This does NOT add anything by itself; the manager confirms.",
        parameters = toolFields(
            ToolField("phase_id", "The phase_id from get_member_path."),
            ToolField("title", "The step's title."),
            ToolField("description", "What the person does in this step."),
            ToolField("type", "What kind of step it is.", values = STEP_TYPES),
            ToolField("estimated_minutes", "How long it should take, in minutes.", "integer"),
            ToolField("expected_outcome", "What the person should have when they are done."),
            ToolField("place", "Where in the phase it goes, counting from 1. Omitted means the end.", "integer"),
            required = listOf("phase_id", "title", "type"),
        ),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val target = scope.element(PathElementKind.PHASE, call.uuidArgument("phase_id"), context.projectId)
            ?: return TeamActionDraft.Refused(notInScope(PathElementKind.PHASE))
        val title = call.textArgument("title")
        if (title.isEmpty()) return TeamActionDraft.Refused("A step needs a title.")
        val type = stepTypeOf(call.textArgument("type"))
            ?: return TeamActionDraft.Refused("Type must be one of ${STEP_TYPES.joinToString(", ")}.")
        val minutesText = call.textArgument("estimated_minutes")
        val minutes = if (minutesText.isEmpty()) DEFAULT_MINUTES else minutesText.toIntOrNull()?.takeIf { it > 0 }
        if (minutes == null) {
            return TeamActionDraft.Refused("estimated_minutes must be a whole number of minutes above zero.")
        }

        val slots = target.element.children + 1
        val placeText = call.textArgument("place")
        val position = if (placeText.isEmpty()) slots - 1 else placeToPosition(placeText, slots)
        if (position == null) return TeamActionDraft.Refused("Place must be a number from 1 to $slots.")
        val description = call.textArgument("description")
        val outcome = call.textArgument("expected_outcome")

        return proposed(
            params = AddStepParams(target.element.id, title, description, type, minutes, outcome, position),
            label = "Add step “${title.forLabel()}”",
            preview = buildString {
                appendLine("Add a step to “${target.element.title}” on ${target.owner.displayName}'s path:")
                appendLine("“$title” — ${type.name.lowercase()}, about $minutes min, ${placeOf(position, slots)}")
                if (description.isNotEmpty()) appendLine(description)
                if (outcome.isNotEmpty()) appendLine("Expected outcome: $outcome")
                appendLine()
                if (position < slots - 1) appendLine("The steps after it move down one place.")
                append("It starts waiting; nobody is marked as having started or finished it.")
                scope.sharedNote(target.owner, context.projectId).takeIf { it.isNotEmpty() }?.let { append("\n\n$it") }
            }.trim(),
        )
    }

    override fun recheckParams(params: AddStepParams, context: TeamToolContext): String? {
        val target = scope.element(PathElementKind.PHASE, params.phaseId, context.projectId)
            ?: return goneSince(PathElementKind.PHASE)
        return (
            "The phase has fewer steps than it had — that place is gone — so nothing was changed. " +
                "Offer it again."
        ).takeIf { params.position !in 0..target.element.children }
    }

    override suspend fun performParams(params: AddStepParams, context: TeamToolContext): String {
        stepAuthoringApi.createStep(
            PhaseId(params.phaseId),
            CreateOnboardingStepRequest(
                position = params.position,
                title = params.title,
                description = params.description,
                type = params.type,
                estimatedMinutes = params.estimatedMinutes,
                expectedOutcome = params.expectedOutcome,
            ),
        )
        return "Added. “${params.title}” is on the path now, waiting."
    }
}

/** Offers to change what a step says, or where it sits. Never its status. */
@Component
class UpdateStepAction(
    private val scope: ContentScope,
    private val stepAuthoringApi: StepAuthoringApi,
) : TypedTeamAction<UpdateStepParams>(UpdateStepParams.serializer()) {
    override val area = TeamArea.CONTENT
    override val risk = BuddyProposalRisk.STANDARD
    override val spec = BuddyToolSpecDto(
        name = "update_step",
        description = "Offer to change a step on a member's onboarding path: what it says, how long it is, " +
            "or where it sits in its phase. Pass only what should change. Use the step_id from " +
            "get_member_path. This never starts, finishes or skips a step. It does NOT change anything by " +
            "itself; the manager confirms.",
        parameters = toolFields(
            ToolField("step_id", "The step_id from get_member_path."),
            ToolField("title", "The new title."),
            ToolField("description", "The new description."),
            ToolField("type", "The new kind of step.", values = STEP_TYPES),
            ToolField("estimated_minutes", "The new length, in minutes.", "integer"),
            ToolField("expected_outcome", "The new expected outcome."),
            ToolField("place", "The new place in its phase, counting from 1.", "integer"),
            required = listOf("step_id"),
        ),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val target = scope.element(PathElementKind.STEP, call.uuidArgument("step_id"), context.projectId)
            ?: return TeamActionDraft.Refused(notInScope(PathElementKind.STEP))
        val current = stepAuthoringApi.step(StepId(target.element.id))
        val siblings = stepAuthoringApi.stepsOf(PhaseId(current.phaseId)).size

        badArgument(call, siblings)?.let { return TeamActionDraft.Refused(it) }

        val newTitle = call.changedText("title", current.title, allowBlank = false)
        val newDescription = call.changedText("description", current.description)
        val newType = stepTypeOf(call.textArgument("type"))?.takeIf { it != current.type }
        val newMinutes = call.textArgument("estimated_minutes").toIntOrNull()?.takeIf { it != current.estimatedMinutes }
        val newOutcome = call.changedText("expected_outcome", current.expectedOutcomes.firstOrNull().orEmpty())
        val newPosition = placeToPosition(call.textArgument("place"), siblings)?.takeIf { it != current.position }

        val changes = ChangeLines()
        changes.add(newTitle) { "Title: “${current.title}” becomes “$it”" }
        changes.add(newDescription) { "Description becomes: ${it.ifEmpty { "(empty)" }}" }
        changes.add(newType) { "Type: ${current.type.name.lowercase()} becomes ${it.name.lowercase()}" }
        changes.add(newMinutes) { "Length: ${current.estimatedMinutes} min becomes $it min" }
        changes.add(newOutcome) { "Expected outcome becomes: ${it.ifEmpty { "(empty)" }}" }
        changes.add(newPosition) { "Place: ${placeOf(current.position, siblings)} becomes ${placeOf(it, siblings)}" }
        if (changes.isEmpty) return TeamActionDraft.Refused("Nothing would change: that is already how the step is.")

        return proposed(
            params = UpdateStepParams(
                current.id,
                newTitle,
                newDescription,
                newType,
                newMinutes,
                newOutcome,
                newPosition,
            ),
            label = "Change step “${current.title.forLabel()}”",
            preview = buildString {
                appendLine("Change the step “${current.title}” on ${target.owner.displayName}'s path:")
                appendLine(changes.preview())
                append("Its status stays ${current.status.name.lowercase().replace('_', ' ')}.")
                scope.sharedNote(target.owner, context.projectId).takeIf { it.isNotEmpty() }?.let { append("\n\n$it") }
            },
        )
    }

    /** Why the call's type, length or place cannot be used, or null when each is either absent or fine. */
    private fun badArgument(call: BuddyToolCallDto, slots: Int): String? =
        when {
            call.textArgument("type").let { it.isNotEmpty() && stepTypeOf(it) == null } ->
                "Type must be one of ${STEP_TYPES.joinToString(", ")}."
            call.textArgument("estimated_minutes").let {
                it.isNotEmpty() &&
                    it.toIntOrNull()?.takeIf { m -> m > 0 } == null
            } ->
                "estimated_minutes must be a whole number of minutes above zero."
            call.textArgument("place").let { it.isNotEmpty() && placeToPosition(it, slots) == null } ->
                "Place must be a number from 1 to $slots."
            else -> null
        }

    override fun recheckParams(params: UpdateStepParams, context: TeamToolContext): String? =
        scope.missing(PathElementKind.STEP, params.stepId, context.projectId)

    override suspend fun performParams(params: UpdateStepParams, context: TeamToolContext): String {
        val id = params.stepId
        // Only what the manager saw is written; the rest is read fresh.
        val current = stepAuthoringApi.step(StepId(id))
        stepAuthoringApi.updateStep(
            StepId(id),
            UpdateOnboardingStepRequest(
                position = params.position ?: current.position,
                title = params.title ?: current.title,
                description = params.description ?: current.description,
                type = params.type ?: current.type,
                estimatedMinutes = params.estimatedMinutes ?: current.estimatedMinutes,
                expectedOutcome = params.expectedOutcome ?: current.expectedOutcomes.firstOrNull().orEmpty(),
            ),
        )
        return "Done. The step is updated; its status is as it was."
    }
}

/** Offers to delete a step, with its tasks and resources. */
@Component
class DeleteStepAction(
    private val scope: ContentScope,
    private val stepAuthoringApi: StepAuthoringApi,
) : TypedTeamAction<DeleteStepParams>(DeleteStepParams.serializer()) {
    override val area = TeamArea.CONTENT
    override val risk = BuddyProposalRisk.DESTRUCTIVE
    override val spec = BuddyToolSpecDto(
        name = "delete_step",
        description = "Offer to delete a step from a member's onboarding path, with its tasks and resources. " +
            "Use the step_id from get_member_path. This does NOT delete anything by itself; the manager " +
            "confirms.",
        parameters = stringFields("step_id" to "The step_id from get_member_path.", required = listOf("step_id")),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val target = scope.element(PathElementKind.STEP, call.uuidArgument("step_id"), context.projectId)
            ?: return TeamActionDraft.Refused(notInScope(PathElementKind.STEP))
        val step = target.element

        return proposed(
            params = DeleteStepParams(step.id),
            label = "Delete step “${step.title.forLabel()}”",
            preview = buildString {
                appendLine("Delete the step “${step.title}” from ${target.owner.displayName}'s onboarding path.")
                if (step.contains.isNotEmpty()) appendLine("It takes ${step.contains} with it.")
                step.stepStatus.aboutProgress(target.owner.displayName)?.let { appendLine(it) }
                if (step.hasLaterSiblings()) append("The steps after it move up one place. ")
                append("This cannot be undone.")
                scope.sharedNote(target.owner, context.projectId).takeIf { it.isNotEmpty() }?.let { append("\n\n$it") }
            }.trim(),
        )
    }

    override fun recheckParams(params: DeleteStepParams, context: TeamToolContext): String? =
        scope.missing(PathElementKind.STEP, params.stepId, context.projectId)

    override suspend fun performParams(params: DeleteStepParams, context: TeamToolContext): String {
        stepAuthoringApi.deleteStep(StepId(params.stepId))
        return "Deleted. The step and what was in it are gone."
    }
}
