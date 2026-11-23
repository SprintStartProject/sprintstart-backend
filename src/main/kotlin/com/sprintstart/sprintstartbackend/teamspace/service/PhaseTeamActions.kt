package com.sprintstart.sprintstartbackend.teamspace.service

import com.sprintstart.sprintstartbackend.assistant.external.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.assistant.external.ChangeLines
import com.sprintstart.sprintstartbackend.assistant.external.TeamActionDraft
import com.sprintstart.sprintstartbackend.assistant.external.TeamArea
import com.sprintstart.sprintstartbackend.assistant.external.TeamToolContext
import com.sprintstart.sprintstartbackend.assistant.external.ToolField
import com.sprintstart.sprintstartbackend.assistant.external.TypedTeamAction
import com.sprintstart.sprintstartbackend.assistant.external.UuidSerializer
import com.sprintstart.sprintstartbackend.assistant.external.changedText
import com.sprintstart.sprintstartbackend.assistant.external.forLabel
import com.sprintstart.sprintstartbackend.assistant.external.placeOf
import com.sprintstart.sprintstartbackend.assistant.external.placeToPosition
import com.sprintstart.sprintstartbackend.assistant.external.stringFields
import com.sprintstart.sprintstartbackend.assistant.external.toolFields
import com.sprintstart.sprintstartbackend.path.external.PathReviewApi
import com.sprintstart.sprintstartbackend.path.external.PhaseAuthoringApi
import com.sprintstart.sprintstartbackend.path.external.model.PathElementKind
import com.sprintstart.sprintstartbackend.path.model.request.phase.CreateOnboardingPhaseRequest
import com.sprintstart.sprintstartbackend.path.model.request.phase.UpdateOnboardingPhaseRequest
import com.sprintstart.sprintstartbackend.shared.ids.PhaseId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.springframework.stereotype.Component
import java.util.UUID

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
    private val pathReviewApi: PathReviewApi,
    private val phaseAuthoringApi: PhaseAuthoringApi,
) : TypedTeamAction<AddPhaseParams>(AddPhaseParams.serializer()) {
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
        val phases =
            pathReviewApi.summaryOf(UserId(owner.userId))?.phases ?: return TeamActionDraft.Refused(NO_PATH_YET)

        val placeText = call.textArgument("place")
        val position = if (placeText.isEmpty()) phases else placeToPosition(placeText, phases + 1)
        if (position == null) return TeamActionDraft.Refused("Place must be a number from 1 to ${phases + 1}.")
        val description = call.textArgument("description")

        return proposed(
            params = AddPhaseParams(owner.userId, title, description, position),
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

    override fun recheckParams(params: AddPhaseParams, context: TeamToolContext): String? {
        val owner = scope.member(params.memberId, context.projectId) ?: return LEFT_SINCE_HERE
        val phases = pathReviewApi.summaryOf(UserId(owner.userId))?.phases ?: return NO_PATH_YET
        return (
            "The path has fewer phases than it had — that place is gone — so nothing was changed. " +
                "Offer it again."
        ).takeIf { params.position !in 0..phases }
    }

    override suspend fun performParams(params: AddPhaseParams, context: TeamToolContext): String {
        phaseAuthoringApi.createPhase(
            UserId(params.memberId),
            CreateOnboardingPhaseRequest(
                position = params.position,
                title = params.title,
                description = params.description,
            ),
        )
        return "Added. “${params.title}” is on the path now, with no steps yet."
    }
}

/** The phase to add to a member's path, and where it goes. */
@Serializable
data class AddPhaseParams(
    @SerialName("member_id") @Serializable(with = UuidSerializer::class) val memberId: UUID,
    val title: String,
    val description: String,
    val position: Int,
)

/** The edits a confirmed phase update applies; a null field is left as the phase has it by then. */
@Serializable
data class UpdatePhaseParams(
    @SerialName("phase_id") @Serializable(with = UuidSerializer::class) val phaseId: UUID,
    val title: String? = null,
    val description: String? = null,
    val position: Int? = null,
)

/** The phase a confirmed deletion removes. */
@Serializable
data class DeletePhaseParams(
    @SerialName("phase_id") @Serializable(with = UuidSerializer::class) val phaseId: UUID,
)

/** Offers to rename, redescribe or move a phase. */
@Component
class UpdatePhaseAction(
    private val scope: ContentScope,
    private val phaseAuthoringApi: PhaseAuthoringApi,
) : TypedTeamAction<UpdatePhaseParams>(UpdatePhaseParams.serializer()) {
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
        val current = phaseAuthoringApi.phase(PhaseId(target.element.id))
        val siblings = phaseAuthoringApi.phasesOf(UserId(target.owner.userId)).size

        val placeText = call.textArgument("place")
        val requested = if (placeText.isEmpty()) current.position else placeToPosition(placeText, siblings)
        if (requested == null) return TeamActionDraft.Refused("Place must be a number from 1 to $siblings.")

        val newTitle = call.changedText("title", current.title, allowBlank = false)
        val newDescription = call.changedText("description", current.description)
        val newPosition = requested.takeIf { it != current.position }

        val changes = ChangeLines()
        changes.add(newTitle) { "Title: “${current.title}” becomes “$it”" }
        changes.add(newDescription) { "Description becomes: ${it.ifEmpty { "(empty)" }}" }
        changes.add(newPosition) {
            "Place: ${placeOf(current.position, siblings)} becomes ${placeOf(it, siblings)}\n" +
                "The phases in between move by one place."
        }
        if (changes.isEmpty) return TeamActionDraft.Refused("Nothing would change: that is already how the phase is.")

        return proposed(
            params = UpdatePhaseParams(target.element.id, newTitle, newDescription, newPosition),
            label = "Change phase “${current.title.forLabel()}”",
            preview = "Change the phase “${current.title}” on ${target.owner.displayName}'s path:\n" +
                changes.preview() +
                scope.sharedNote(target.owner, context.projectId).let { if (it.isEmpty()) "" else "\n\n$it" },
        )
    }

    override fun recheckParams(params: UpdatePhaseParams, context: TeamToolContext): String? =
        scope.missing(PathElementKind.PHASE, params.phaseId, context.projectId)

    override suspend fun performParams(params: UpdatePhaseParams, context: TeamToolContext): String {
        val id = params.phaseId
        // Only what the manager saw is written. Everything else is read fresh, so a change made to the
        // other fields since the preview is not undone by a confirm.
        val current = phaseAuthoringApi.phase(PhaseId(id))
        phaseAuthoringApi.updatePhase(
            PhaseId(id),
            UpdateOnboardingPhaseRequest(
                position = params.position ?: current.position,
                title = params.title ?: current.title,
                description = params.description ?: current.description,
            ),
        )
        return "Done. The phase is updated."
    }
}

/** Offers to delete a phase, with everything in it. */
@Component
class DeletePhaseAction(
    private val scope: ContentScope,
    private val phaseAuthoringApi: PhaseAuthoringApi,
) : TypedTeamAction<DeletePhaseParams>(DeletePhaseParams.serializer()) {
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

        return proposed(
            params = DeletePhaseParams(phase.id),
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
                if (phase.hasLaterSiblings()) append("The phases after it move up one place. ")
                append("This cannot be undone.")
                scope.sharedNote(target.owner, context.projectId).takeIf { it.isNotEmpty() }?.let { append("\n\n$it") }
            }.trim(),
        )
    }

    override fun recheckParams(params: DeletePhaseParams, context: TeamToolContext): String? =
        scope.missing(PathElementKind.PHASE, params.phaseId, context.projectId)

    override suspend fun performParams(params: DeletePhaseParams, context: TeamToolContext): String {
        phaseAuthoringApi.deletePhase(PhaseId(params.phaseId))
        return "Deleted. The phase and everything in it are gone."
    }
}
