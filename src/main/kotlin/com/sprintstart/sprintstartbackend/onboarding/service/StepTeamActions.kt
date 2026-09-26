package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.model.request.step.CreateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.step.UpdateOnboardingStepRequest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.springframework.stereotype.Component

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

/** Offers to add a step to a phase of a member's path. */
@Component
class AddStepAction(
    private val scope: ContentScope,
    private val onboardingStepService: OnboardingStepService,
) : TeamActionHandler {
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

        return TeamActionDraft.Proposed(
            params = buildJsonObject {
                put("phase_id", target.element.id.toString())
                put("title", title)
                put("description", description)
                put("type", type.name)
                put("estimated_minutes", minutes)
                put("expected_outcome", outcome)
                put("position", position)
            },
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

    override fun recheck(params: JsonObject, context: TeamToolContext): String? {
        val target = scope.element(PathElementKind.PHASE, params.uuid("phase_id"), context.projectId)
            ?: return goneSince(PathElementKind.PHASE)
        val position = params.text("position").toIntOrNull() ?: return "That offer is malformed."
        return "The phase has fewer steps than it had — that place is gone — so nothing was changed. Offer it again."
            .takeIf { position !in 0..target.element.children }
    }

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        onboardingStepService.createOnboardingStepForPhaseId(
            requireNotNull(params.uuid("phase_id")),
            CreateOnboardingStepRequest(
                position = requireNotNull(params.text("position").toIntOrNull()),
                title = params.text("title"),
                description = params.text("description"),
                type = requireNotNull(stepTypeOf(params.text("type"))),
                estimatedMinutes = params.text("estimated_minutes").toIntOrNull() ?: DEFAULT_MINUTES,
                expectedOutcome = params.text("expected_outcome"),
            ),
        )
        return "Added. “${params.text("title")}” is on the path now, waiting."
    }
}

/** Offers to change what a step says, or where it sits. Never its status. */
@Component
class UpdateStepAction(
    private val scope: ContentScope,
    private val onboardingStepService: OnboardingStepService,
) : TeamActionHandler {
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
        val current = onboardingStepService.getOnboardingStepById(target.element.id)
        val siblings = onboardingStepService.getOnboardingStepsByPhaseId(current.phaseId).size

        badArgument(call, siblings)?.let { return TeamActionDraft.Refused(it) }

        val changes = ChangeSet("step_id", current.id)
        changes.add("title", call.changedText("title", current.title, allowBlank = false)) {
            "Title: “${current.title}” becomes “$it”"
        }
        changes.add("description", call.changedText("description", current.description)) {
            "Description becomes: ${it.ifEmpty { "(empty)" }}"
        }
        changes.add("type", stepTypeOf(call.textArgument("type"))?.takeIf { it != current.type }?.name) {
            "Type: ${current.type.name.lowercase()} becomes ${it.lowercase()}"
        }
        val minutes = call.textArgument("estimated_minutes").toIntOrNull()?.takeIf { it != current.estimatedMinutes }
        changes.add("estimated_minutes", minutes?.toString()) {
            "Length: ${current.estimatedMinutes} min becomes $it min"
        }
        changes.add(
            "expected_outcome",
            call.changedText("expected_outcome", current.expectedOutcomes.firstOrNull().orEmpty()),
        ) {
            "Expected outcome becomes: ${it.ifEmpty { "(empty)" }}"
        }
        val position = placeToPosition(call.textArgument("place"), siblings)?.takeIf { it != current.position }
        changes.add("position", position?.toString()) {
            "Place: ${placeOf(current.position, siblings)} becomes ${placeOf(it.toInt(), siblings)}"
        }
        if (changes.isEmpty) return TeamActionDraft.Refused("Nothing would change: that is already how the step is.")

        return TeamActionDraft.Proposed(
            params = changes.params(),
            label = "Change step “${current.title.forLabel()}”",
            preview = "Change the step “${current.title}” on ${target.owner.displayName}'s path:\n" +
                changes.preview() + "\n" +
                "Its status stays ${current.status.name.lowercase().replace('_', ' ')}." +
                scope.sharedNote(target.owner, context.projectId).let { if (it.isEmpty()) "" else "\n\n$it" },
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

    override fun recheck(params: JsonObject, context: TeamToolContext): String? =
        scope.missing(PathElementKind.STEP, params.uuid("step_id"), context.projectId)

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        val id = requireNotNull(params.uuid("step_id"))
        // Only what the manager saw is written; the rest is read fresh.
        val current = onboardingStepService.getOnboardingStepById(id)
        onboardingStepService.updateOnboardingStepById(
            id,
            UpdateOnboardingStepRequest(
                position = params.text("position").toIntOrNull() ?: current.position,
                title = params.text("title").ifEmpty { current.title },
                description = if (params.containsKey(
                        "description",
                    )
                ) {
                    params.text("description")
                } else {
                    current.description
                },
                type = stepTypeOf(params.text("type")) ?: current.type,
                estimatedMinutes = params.text("estimated_minutes").toIntOrNull() ?: current.estimatedMinutes,
                expectedOutcome = if (params.containsKey("expected_outcome")) {
                    params.text("expected_outcome")
                } else {
                    current.expectedOutcomes.firstOrNull().orEmpty()
                },
            ),
        )
        return "Done. The step is updated; its status is as it was."
    }
}

/** Offers to delete a step, with its tasks and resources. */
@Component
class DeleteStepAction(
    private val scope: ContentScope,
    private val onboardingStepService: OnboardingStepService,
) : TeamActionHandler {
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

        return TeamActionDraft.Proposed(
            params = buildJsonObject { put("step_id", step.id.toString()) },
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

    override fun recheck(params: JsonObject, context: TeamToolContext): String? =
        scope.missing(PathElementKind.STEP, params.uuid("step_id"), context.projectId)

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        onboardingStepService.deleteOnboardingStepById(requireNotNull(params.uuid("step_id")))
        return "Deleted. The step and what was in it are gone."
    }
}
