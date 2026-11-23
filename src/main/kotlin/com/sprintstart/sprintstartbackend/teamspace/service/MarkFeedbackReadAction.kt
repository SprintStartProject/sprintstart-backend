package com.sprintstart.sprintstartbackend.teamspace.service

import com.sprintstart.sprintstartbackend.assistant.external.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.assistant.external.TeamActionDraft
import com.sprintstart.sprintstartbackend.assistant.external.TeamArea
import com.sprintstart.sprintstartbackend.assistant.external.TeamToolContext
import com.sprintstart.sprintstartbackend.assistant.external.TypedTeamAction
import com.sprintstart.sprintstartbackend.assistant.external.UuidSerializer
import com.sprintstart.sprintstartbackend.assistant.external.forLabel
import com.sprintstart.sprintstartbackend.assistant.external.stringFields
import com.sprintstart.sprintstartbackend.path.external.PathReviewApi
import com.sprintstart.sprintstartbackend.path.external.model.PathElementKind
import com.sprintstart.sprintstartbackend.shared.ids.FeedbackId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.springframework.stereotype.Component
import java.util.UUID

/** The feedback a confirmed review marks as read. */
@Serializable
data class MarkFeedbackReadParams(
    @SerialName("feedback_id") @Serializable(with = UuidSerializer::class) val feedbackId: UUID,
)

/**
 * Offers to mark a hire's feedback as read.
 *
 * The admin service has no read-by-id, and its by-id write loads the feedback without asking whose it
 * is — so the owner is resolved through [ContentScope] first, and the message is read back through
 * that owner's own feedback so the preview can show what is being marked.
 */
@Component
class MarkFeedbackReadAction(
    private val scope: ContentScope,
    private val pathReviewApi: PathReviewApi,
) : TypedTeamAction<MarkFeedbackReadParams>(MarkFeedbackReadParams.serializer()) {
    override val area = TeamArea.CONTENT
    override val risk = BuddyProposalRisk.STANDARD
    override val spec = BuddyToolSpecDto(
        name = "mark_feedback_read",
        description = "Offer to mark one piece of a hire's onboarding feedback as read, once the manager has " +
            "seen it. It only clears it from the unread list; nothing is sent to the hire. Use the " +
            "feedback_id from list_feedback. This does NOT mark anything by itself; the manager confirms.",
        parameters = stringFields(
            "feedback_id" to "The feedback_id from list_feedback.",
            required = listOf("feedback_id"),
        ),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val scoped = scope.element(PathElementKind.FEEDBACK, call.uuidArgument("feedback_id"), context.projectId)
            ?: return TeamActionDraft.Refused(notInScope(PathElementKind.FEEDBACK))
        val feedback = pathReviewApi
            .feedbackOf(UserId(scoped.owner.userId))
            .firstOrNull { it.id == scoped.element.id }
            ?: return TeamActionDraft.Refused(goneSince(PathElementKind.FEEDBACK))
        if (feedback.read) return TeamActionDraft.Refused("That feedback is already marked as read.")

        return proposed(
            params = MarkFeedbackReadParams(feedback.id),
            label = "Mark ${scoped.owner.displayName.forLabel()}'s feedback read",
            preview = buildString {
                appendLine("Mark this feedback from ${scoped.owner.displayName} as read:")
                val where = feedback.stepTitle?.let { "“$it”" } ?: "their path as a whole"
                appendLine("On $where: “${feedback.message}”")
                appendLine()
                append("It only leaves the unread list. They are not told, and nothing else changes.")
            }.trim(),
        )
    }

    override fun recheckParams(params: MarkFeedbackReadParams, context: TeamToolContext): String? {
        val scoped = scope.element(PathElementKind.FEEDBACK, params.feedbackId, context.projectId)
            ?: return goneSince(PathElementKind.FEEDBACK)
        val alreadyRead = pathReviewApi
            .feedbackOf(UserId(scoped.owner.userId))
            .firstOrNull { it.id == scoped.element.id }
            ?.read
        return "Somebody already marked that feedback as read, so nothing was changed.".takeIf { alreadyRead == true }
    }

    override suspend fun performParams(params: MarkFeedbackReadParams, context: TeamToolContext): String {
        pathReviewApi.markFeedbackRead(FeedbackId(params.feedbackId))
        return "Marked as read."
    }
}
