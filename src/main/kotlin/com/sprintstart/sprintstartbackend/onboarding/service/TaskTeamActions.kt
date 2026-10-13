package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.model.request.task.CreateOnboardingTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.task.UpdateOnboardingTaskRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.springframework.stereotype.Component
import java.util.UUID

/*
 * The task actions of team mode's content area.
 *
 * A task is what a person ticks off. The update service takes `finished` alongside the text, so an
 * edit here carries the tick through exactly as it is: team mode never sets a hire's progress, and an
 * edit that quietly un-ticked a task would be doing exactly that.
 */

private const val STEP_FINISHED_SINCE =
    "The step was finished since this was proposed, and adding a task would reopen it — which the offer did not " +
        "say — so nothing was changed. Offer it again."

/** The task to add, where it goes, and whether the preview announced that it reopens a finished step. */
@Serializable
data class AddTaskParams(
    @SerialName("step_id") @Serializable(with = UuidSerializer::class) val stepId: UUID,
    val title: String,
    val description: String,
    val position: Int,
    val reopens: Boolean,
)

/** The edits a confirmed task update applies; a null field is left as the task has it by then. */
@Serializable
data class UpdateTaskParams(
    @SerialName("task_id") @Serializable(with = UuidSerializer::class) val taskId: UUID,
    val title: String? = null,
    val description: String? = null,
    val position: Int? = null,
)

/** The task a confirmed deletion removes. */
@Serializable
data class DeleteTaskParams(
    @SerialName("task_id") @Serializable(with = UuidSerializer::class) val taskId: UUID,
)

/** Offers to add a task to a step of a member's path. */
@Component
class AddTaskAction(
    private val scope: ContentScope,
    private val onboardingTaskService: OnboardingTaskService,
) : TypedTeamAction<AddTaskParams>(AddTaskParams.serializer()) {
    override val area = TeamArea.CONTENT
    override val risk = BuddyProposalRisk.STANDARD
    override val spec = BuddyToolSpecDto(
        name = "add_task",
        description = "Offer to add a task to a step of a member's onboarding path. Use the step_id from " +
            "get_member_path. It starts unfinished. Omit place to add it at the end of the step. This does " +
            "NOT add anything by itself; the manager confirms.",
        parameters = toolFields(
            ToolField("step_id", "The step_id from get_member_path."),
            ToolField("title", "The task's title."),
            ToolField("description", "What the person does."),
            ToolField("place", "Where in the step it goes, counting from 1. Omitted means the end.", "integer"),
            required = listOf("step_id", "title"),
        ),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val target = scope.element(PathElementKind.STEP, call.uuidArgument("step_id"), context.projectId)
            ?: return TeamActionDraft.Refused(notInScope(PathElementKind.STEP))
        val title = call.textArgument("title")
        if (title.isEmpty()) return TeamActionDraft.Refused("A task needs a title.")

        val slots = target.element.children + 1
        val placeText = call.textArgument("place")
        val position = if (placeText.isEmpty()) slots - 1 else placeToPosition(placeText, slots)
        if (position == null) return TeamActionDraft.Refused("Place must be a number from 1 to $slots.")
        val description = call.textArgument("description")
        val reopens = target.element.stepStatus == StepStatus.FINISHED

        return proposed(
            params = AddTaskParams(target.element.id, title, description, position, reopens),
            label = "Add task “${title.forLabel()}”",
            preview = buildString {
                appendLine("Add a task to “${target.element.title}” on ${target.owner.displayName}'s path:")
                appendLine("“$title” — ${placeOf(position, slots)}")
                if (description.isNotEmpty()) appendLine(description)
                appendLine()
                if (position < slots - 1) appendLine("The tasks after it move down one place.")
                append("It starts unfinished.")
                if (reopens) {
                    // The service does this, and it is a change to progress the manager has to agree to.
                    append(
                        "\n\nThe step is already finished. A step can only stay finished while all its tasks " +
                            "are done, so this reopens it: it goes back to in progress for " +
                            "${target.owner.displayName}.",
                    )
                }
                scope.sharedNote(target.owner, context.projectId).takeIf { it.isNotEmpty() }?.let { append("\n\n$it") }
            }.trim(),
        )
    }

    override fun recheckParams(params: AddTaskParams, context: TeamToolContext): String? {
        val target = scope.element(PathElementKind.STEP, params.stepId, context.projectId)
            ?: return goneSince(PathElementKind.STEP)
        if (params.position !in 0..target.element.children) {
            return "The step has fewer tasks than it had — that place is gone — so nothing was changed. " +
                "Offer it again."
        }
        // Finished since the preview: the service would reopen it, which this offer never said.
        return STEP_FINISHED_SINCE.takeIf {
            target.element.stepStatus == StepStatus.FINISHED && !params.reopens
        }
    }

    override suspend fun performParams(params: AddTaskParams, context: TeamToolContext): String {
        onboardingTaskService.createOnboardingTaskForStepId(
            params.stepId,
            CreateOnboardingTaskRequest(
                position = params.position,
                title = params.title,
                description = params.description,
            ),
        )
        return "Added. “${params.title}” is on the step now, unfinished."
    }
}

/** Offers to change what a task says, or where it sits. Never whether it is ticked. */
@Component
class UpdateTaskAction(
    private val scope: ContentScope,
    private val onboardingTaskService: OnboardingTaskService,
) : TypedTeamAction<UpdateTaskParams>(UpdateTaskParams.serializer()) {
    override val area = TeamArea.CONTENT
    override val risk = BuddyProposalRisk.STANDARD
    override val spec = BuddyToolSpecDto(
        name = "update_task",
        description = "Offer to change a task on a member's onboarding path: its title, its description or " +
            "where it sits in its step. Pass only what should change. Use the task_id from get_member_path. " +
            "This can never tick or un-tick a task. It does NOT change anything by itself; the manager " +
            "confirms.",
        parameters = toolFields(
            ToolField("task_id", "The task_id from get_member_path."),
            ToolField("title", "The new title."),
            ToolField("description", "The new description."),
            ToolField("place", "The new place in its step, counting from 1.", "integer"),
            required = listOf("task_id"),
        ),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val target = scope.element(PathElementKind.TASK, call.uuidArgument("task_id"), context.projectId)
            ?: return TeamActionDraft.Refused(notInScope(PathElementKind.TASK))
        val current = onboardingTaskService.getOnboardingTaskById(target.element.id)
        val siblings = onboardingTaskService.getOnboardingTasksByStepId(current.stepId).size

        val placeText = call.textArgument("place")
        val requested = if (placeText.isEmpty()) current.position else placeToPosition(placeText, siblings)
        if (requested == null) return TeamActionDraft.Refused("Place must be a number from 1 to $siblings.")

        val newTitle = call.changedText("title", current.title, allowBlank = false)
        val newDescription = call.changedText("description", current.description)
        val newPosition = requested.takeIf { it != current.position }
        val changes = ChangeLines()
        changes.add(newTitle) { "Title: “${current.title}” becomes “$it”" }
        changes.add(newDescription) { "Description becomes: ${it.ifEmpty { "(empty)" }}" }
        changes.add(newPosition) { "Place: ${placeOf(current.position, siblings)} becomes ${placeOf(it, siblings)}" }
        if (changes.isEmpty) return TeamActionDraft.Refused("Nothing would change: that is already how the task is.")

        return proposed(
            params = UpdateTaskParams(current.id, newTitle, newDescription, newPosition),
            label = "Change task “${current.title.forLabel()}”",
            preview = buildString {
                appendLine("Change the task “${current.title}” on ${target.owner.displayName}'s path:")
                appendLine(changes.preview())
                append("Whether it is ticked off stays as it is (${if (current.finished) "done" else "not done"}).")
                scope.sharedNote(target.owner, context.projectId).takeIf { it.isNotEmpty() }?.let { append("\n\n$it") }
            },
        )
    }

    override fun recheckParams(params: UpdateTaskParams, context: TeamToolContext): String? =
        scope.missing(PathElementKind.TASK, params.taskId, context.projectId)

    override suspend fun performParams(params: UpdateTaskParams, context: TeamToolContext): String {
        val id = params.taskId
        // `finished` is read now and passed back unchanged: the request carries it, and it must never
        // be the thing an edit from here decides.
        val current = onboardingTaskService.getOnboardingTaskById(id)
        onboardingTaskService.updateOnboardingTaskById(
            id,
            UpdateOnboardingTaskRequest(
                position = params.position ?: current.position,
                title = params.title ?: current.title,
                description = params.description ?: current.description,
                finished = current.finished,
            ),
        )
        return "Done. The task is updated; whether it is ticked off is as it was."
    }
}

/** Offers to delete a task. */
@Component
class DeleteTaskAction(
    private val scope: ContentScope,
    private val onboardingTaskService: OnboardingTaskService,
) : TypedTeamAction<DeleteTaskParams>(DeleteTaskParams.serializer()) {
    override val area = TeamArea.CONTENT
    override val risk = BuddyProposalRisk.DESTRUCTIVE
    override val spec = BuddyToolSpecDto(
        name = "delete_task",
        description = "Offer to delete a task from a member's onboarding path. Use the task_id from " +
            "get_member_path. This does NOT delete anything by itself; the manager confirms.",
        parameters = stringFields("task_id" to "The task_id from get_member_path.", required = listOf("task_id")),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val target = scope.element(PathElementKind.TASK, call.uuidArgument("task_id"), context.projectId)
            ?: return TeamActionDraft.Refused(notInScope(PathElementKind.TASK))
        val task = target.element

        return proposed(
            params = DeleteTaskParams(task.id),
            label = "Delete task “${task.title.forLabel()}”",
            preview = buildString {
                appendLine("Delete the task “${task.title}” from ${target.owner.displayName}'s onboarding path.")
                if (task.hasLaterSiblings()) append("The tasks after it move up one place. ")
                append("Any tick on it goes with it. This cannot be undone.")
                scope.sharedNote(target.owner, context.projectId).takeIf { it.isNotEmpty() }?.let { append("\n\n$it") }
            }.trim(),
        )
    }

    override fun recheckParams(params: DeleteTaskParams, context: TeamToolContext): String? =
        scope.missing(PathElementKind.TASK, params.taskId, context.projectId)

    override suspend fun performParams(params: DeleteTaskParams, context: TeamToolContext): String {
        onboardingTaskService.deleteOnboardingTaskById(params.taskId)
        return "Deleted. The task is gone."
    }
}
