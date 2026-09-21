package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.model.request.phase.CreateOnboardingPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.phase.UpdateOnboardingPhaseRequest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.springframework.stereotype.Component

/*
 * The phase actions of team mode's content area.
 *
 * A phase hangs off a person's path, and the service behind these loads it by id without asking whose
 * it is. So every action resolves its target through ContentScope — the path's owner must be on the
 * turn's project — when drafting and again when the manager confirms.
 */

private const val NO_PATH_YET =
    "That person has no onboarding path yet, so there is nothing to add a phase to. Their path is made " +
        "when they start onboarding; it cannot be started from here."

/** Offers to add a phase to a member's path. */
@Component
class AddPhaseAction(
    private val scope: ContentScope,
    private val pathElements: PathElements,
    private val onboardingPhaseService: OnboardingPhaseService,
) : TeamActionHandler {
    override val area = TeamArea.CONTENT
    override val risk = BuddyProposalRisk.STANDARD
    override val spec = BuddyToolSpecDto(
        name = "add_phase",
        description = "Offer to add a phase to a member's onboarding path. Use the member_id from find_member. " +
            "Omit place to add it at the end. The phase starts empty; steps are added to it afterwards. This " +
            "does NOT add anything by itself; the manager confirms.",
        parameters = toolFields(
            ToolField("member_id", "The member_id from find_member."),
            ToolField("title", "The phase's title."),
            ToolField("description", "What the phase is for, in a sentence or two."),
            ToolField("place", "Where in the path it goes, counting from 1. Omitted means the end.", "integer"),
            required = listOf("member_id", "title"),
        ),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val owner = scope.member(call.uuidArgument("member_id"), context.projectId)
            ?: return TeamActionDraft.Refused(NOT_A_MEMBER_HERE)
        val title = call.textArgument("title")
        if (title.isEmpty()) return TeamActionDraft.Refused("A phase needs a title.")
        val phases = pathElements.pathOf(owner.userId)?.phases ?: return TeamActionDraft.Refused(NO_PATH_YET)

        val placeText = call.textArgument("place")
        val position = if (placeText.isEmpty()) phases else placeToPosition(placeText, phases + 1)
        if (position == null) return TeamActionDraft.Refused("Place must be a number from 1 to ${phases + 1}.")
        val description = call.textArgument("description")

        return TeamActionDraft.Proposed(
            params = buildJsonObject {
                put("member_id", owner.userId.toString())
                put("title", title)
                put("description", description)
                put("position", position)
            },
            label = "Add phase “${title.forLabel()}” for ${owner.displayName.forLabel()}",
            preview = buildString {
                appendLine("Add a phase to ${owner.displayName}'s onboarding path:")
                appendLine("“$title” — ${placeOf(position, phases + 1)}")
                if (description.isNotEmpty()) appendLine(description)
                appendLine()
                if (position < phases) appendLine("The phases after it move down one place.")
                append("It starts with no steps.")
                scope.sharedNote(owner, context.projectId).takeIf { it.isNotEmpty() }?.let { append("\n\n$it") }
            }.trim(),
        )
    }

    override fun recheck(params: JsonObject, context: TeamToolContext): String? {
        val owner = scope.member(params.uuid("member_id"), context.projectId) ?: return LEFT_SINCE_HERE
        val phases = pathElements.pathOf(owner.userId)?.phases ?: return NO_PATH_YET
        val position = params.text("position").toIntOrNull() ?: return "That offer is malformed."
        return "The path has fewer phases than it had — that place is gone — so nothing was changed. Offer it again."
            .takeIf { position !in 0..phases }
    }

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        onboardingPhaseService.createOnboardingPhaseForUserId(
            requireNotNull(params.uuid("member_id")),
            CreateOnboardingPhaseRequest(
                position = requireNotNull(params.text("position").toIntOrNull()),
                title = params.text("title"),
                description = params.text("description"),
            ),
        )
        return "Added. “${params.text("title")}” is on the path now, with no steps yet."
    }
}

/** Offers to rename, redescribe or move a phase. */
@Component
class UpdatePhaseAction(
    private val scope: ContentScope,
    private val onboardingPhaseService: OnboardingPhaseService,
) : TeamActionHandler {
    override val area = TeamArea.CONTENT
    override val risk = BuddyProposalRisk.STANDARD
    override val spec = BuddyToolSpecDto(
        name = "update_phase",
        description = "Offer to change a phase on a member's onboarding path: its title, its description, or " +
            "where it sits. Pass only what should change. Use the phase_id from get_member_path. This does " +
            "NOT change anything by itself; the manager confirms.",
        parameters = toolFields(
            ToolField("phase_id", "The phase_id from get_member_path."),
            ToolField("title", "The new title."),
            ToolField("description", "The new description."),
            ToolField("place", "The new place in the path, counting from 1.", "integer"),
            required = listOf("phase_id"),
        ),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val target = scope.element(PathElementKind.PHASE, call.uuidArgument("phase_id"), context.projectId)
            ?: return TeamActionDraft.Refused(notInScope(PathElementKind.PHASE))
        val current = onboardingPhaseService.getOnboardingPhaseById(target.element.id)
        val siblings = onboardingPhaseService.getOnboardingPhasesForUser(target.owner.userId).size

        val placeText = call.textArgument("place")
        val requested = if (placeText.isEmpty()) current.position else placeToPosition(placeText, siblings)
        if (requested == null) return TeamActionDraft.Refused("Place must be a number from 1 to $siblings.")

        val changes = ChangeSet("phase_id", target.element.id)
        changes.add("title", call.changedText("title", current.title, allowBlank = false)) {
            "Title: “${current.title}” becomes “$it”"
        }
        changes.add("description", call.changedText("description", current.description)) {
            "Description becomes: ${it.ifEmpty { "(empty)" }}"
        }
        changes.add("position", requested.takeIf { it != current.position }?.toString()) {
            "Place: ${placeOf(current.position, siblings)} becomes ${placeOf(it.toInt(), siblings)}\n" +
                "The phases in between move by one place."
        }
        if (changes.isEmpty) return TeamActionDraft.Refused("Nothing would change: that is already how the phase is.")

        return TeamActionDraft.Proposed(
            params = changes.params(),
            label = "Change phase “${current.title.forLabel()}”",
            preview = "Change the phase “${current.title}” on ${target.owner.displayName}'s path:\n" +
                changes.preview() +
                scope.sharedNote(target.owner, context.projectId).let { if (it.isEmpty()) "" else "\n\n$it" },
        )
    }

    override fun recheck(params: JsonObject, context: TeamToolContext): String? =
        scope.missing(PathElementKind.PHASE, params.uuid("phase_id"), context.projectId)

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        val id = requireNotNull(params.uuid("phase_id"))
        // Only what the manager saw is written. Everything else is read fresh, so a change made to the
        // other fields since the preview is not undone by a confirm.
        val current = onboardingPhaseService.getOnboardingPhaseById(id)
        onboardingPhaseService.updateOnboardingPhaseById(
            id,
            UpdateOnboardingPhaseRequest(
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
            ),
        )
        return "Done. The phase is updated."
    }
}

/** Offers to delete a phase, with everything in it. */
@Component
class DeletePhaseAction(
    private val scope: ContentScope,
    private val onboardingPhaseService: OnboardingPhaseService,
) : TeamActionHandler {
    override val area = TeamArea.CONTENT
    override val risk = BuddyProposalRisk.DESTRUCTIVE
    override val spec = BuddyToolSpecDto(
        name = "delete_phase",
        description = "Offer to delete a phase from a member's onboarding path, with its steps, tasks, " +
            "resources and knowledge checks. Use the phase_id from get_member_path. This does NOT delete " +
            "anything by itself; the manager confirms.",
        parameters = stringFields("phase_id" to "The phase_id from get_member_path.", required = listOf("phase_id")),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val target = scope.element(PathElementKind.PHASE, call.uuidArgument("phase_id"), context.projectId)
            ?: return TeamActionDraft.Refused(notInScope(PathElementKind.PHASE))
        val phase = target.element

        return TeamActionDraft.Proposed(
            params = buildJsonObject { put("phase_id", phase.id.toString()) },
            label = "Delete phase “${phase.title.forLabel()}”",
            preview = buildString {
                appendLine("Delete the phase “${phase.title}” from ${target.owner.displayName}'s onboarding path.")
                if (phase.contains.isNotEmpty()) appendLine("It takes ${phase.contains} with it.")
                if (phase.finishedSteps > 0) {
                    appendLine(
                        "${target.owner.displayName} has already got through ${phase.finishedSteps} of its steps; " +
                            "that progress is deleted with them.",
                    )
                }
                append("The phases after it move up one place. This cannot be undone.")
                scope.sharedNote(target.owner, context.projectId).takeIf { it.isNotEmpty() }?.let { append("\n\n$it") }
            }.trim(),
        )
    }

    override fun recheck(params: JsonObject, context: TeamToolContext): String? =
        scope.missing(PathElementKind.PHASE, params.uuid("phase_id"), context.projectId)

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        onboardingPhaseService.deleteOnboardingPhaseById(requireNotNull(params.uuid("phase_id")))
        return "Deleted. The phase and everything in it are gone."
    }
}
