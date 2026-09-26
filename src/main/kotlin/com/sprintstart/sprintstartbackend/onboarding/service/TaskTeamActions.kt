package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.model.request.task.CreateOnboardingTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.task.UpdateOnboardingTaskRequest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.springframework.stereotype.Component

/*
 * The task actions of team mode's content area.
 *
 * A task is what a person ticks off. The update service takes `finished` alongside the text, so an
 * edit here carries the tick through exactly as it is: team mode never sets a hire's progress, and an
 * edit that quietly un-ticked a task would be doing exactly that.
 */

/** Offers to add a task to a step of a member's path. */
@Component
class AddTaskAction(
    private val scope: ContentScope,
    private val onboardingTaskService: OnboardingTaskService,
) : TeamActionHandler {
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

        return TeamActionDraft.Proposed(
            params = buildJsonObject {
                put("step_id", target.element.id.toString())
                put("title", title)
                put("description", description)
                put("position", position)
            },
            label = "Add task “${title.forLabel()}”",
            preview = buildString {
                appendLine("Add a task to “${target.element.title}” on ${target.owner.displayName}'s path:")
                appendLine("“$title” — ${placeOf(position, slots)}")
                if (description.isNotEmpty()) appendLine(description)
                appendLine()
                if (position < slots - 1) appendLine("The tasks after it move down one place.")
                append("It starts unfinished.")
                if (target.element.stepStatus == StepStatus.FINISHED) {
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

    override fun recheck(params: JsonObject, context: TeamToolContext): String? {
        val target = scope.element(PathElementKind.STEP, params.uuid("step_id"), context.projectId)
            ?: return goneSince(PathElementKind.STEP)
        val position = params.text("position").toIntOrNull() ?: return "That offer is malformed."
        return "The step has fewer tasks than it had — that place is gone — so nothing was changed. Offer it again."
            .takeIf { position !in 0..target.element.children }
    }

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        onboardingTaskService.createOnboardingTaskForStepId(
            requireNotNull(params.uuid("step_id")),
            CreateOnboardingTaskRequest(
                position = requireNotNull(params.text("position").toIntOrNull()),
                title = params.text("title"),
                description = params.text("description"),
            ),
        )
        return "Added. “${params.text("title")}” is on the step now, unfinished."
    }
}

/** Offers to change what a task says, or where it sits. Never whether it is ticked. */
@Component
class UpdateTaskAction(
    private val scope: ContentScope,
    private val onboardingTaskService: OnboardingTaskService,
) : TeamActionHandler {
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

        val changes = ChangeSet("task_id", current.id)
        changes.add("title", call.changedText("title", current.title, allowBlank = false)) {
            "Title: “${current.title}” becomes “$it”"
        }
        changes.add("description", call.changedText("description", current.description)) {
            "Description becomes: ${it.ifEmpty { "(empty)" }}"
        }
        changes.add("position", requested.takeIf { it != current.position }?.toString()) {
            "Place: ${placeOf(current.position, siblings)} becomes ${placeOf(it.toInt(), siblings)}"
        }
        if (changes.isEmpty) return TeamActionDraft.Refused("Nothing would change: that is already how the task is.")

        return TeamActionDraft.Proposed(
            params = changes.params(),
            label = "Change task “${current.title.forLabel()}”",
            preview = "Change the task “${current.title}” on ${target.owner.displayName}'s path:\n" +
                changes.preview() + "\n" +
                "Whether it is ticked off stays as it is (${if (current.finished) "done" else "not done"})." +
                scope.sharedNote(target.owner, context.projectId).let { if (it.isEmpty()) "" else "\n\n$it" },
        )
    }

    override fun recheck(params: JsonObject, context: TeamToolContext): String? =
        scope.missing(PathElementKind.TASK, params.uuid("task_id"), context.projectId)

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        val id = requireNotNull(params.uuid("task_id"))
        // `finished` is read now and passed back unchanged: the request carries it, and it must never
        // be the thing an edit from here decides.
        val current = onboardingTaskService.getOnboardingTaskById(id)
        onboardingTaskService.updateOnboardingTaskById(
            id,
            UpdateOnboardingTaskRequest(
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
) : TeamActionHandler {
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

        return TeamActionDraft.Proposed(
            params = buildJsonObject { put("task_id", task.id.toString()) },
            label = "Delete task “${task.title.forLabel()}”",
            preview = buildString {
                appendLine("Delete the task “${task.title}” from ${target.owner.displayName}'s onboarding path.")
                if (task.hasLaterSiblings()) append("The tasks after it move up one place. ")
                append("Any tick on it goes with it. This cannot be undone.")
                scope.sharedNote(target.owner, context.projectId).takeIf { it.isNotEmpty() }?.let { append("\n\n$it") }
            }.trim(),
        )
    }

    override fun recheck(params: JsonObject, context: TeamToolContext): String? =
        scope.missing(PathElementKind.TASK, params.uuid("task_id"), context.projectId)

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        onboardingTaskService.deleteOnboardingTaskById(requireNotNull(params.uuid("task_id")))
        return "Deleted. The task is gone."
    }
}
