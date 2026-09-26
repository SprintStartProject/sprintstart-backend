package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.springframework.stereotype.Component
import java.util.UUID

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
    private val onboardingFeedbackService: OnboardingFeedbackService,
) : TeamActionHandler {
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
        val feedback = onboardingFeedbackService
            .getAllFeedbackByUserId(scoped.owner.userId)
            .firstOrNull { it.id == scoped.element.id }
            ?: return TeamActionDraft.Refused(goneSince(PathElementKind.FEEDBACK))
        if (feedback.read) return TeamActionDraft.Refused("That feedback is already marked as read.")

        return TeamActionDraft.Proposed(
            params = buildJsonObject { put("feedback_id", feedback.id.toString()) },
            label = "Mark ${scoped.owner.displayName.forLabel()}'s feedback read",
            preview = buildString {
                appendLine("Mark this feedback from ${scoped.owner.displayName} as read:")
                appendLine(
                    "On ${feedback.stepTitle?.let { "“$it”" } ?: "their path as a whole"}: “${feedback.message}”",
                )
                appendLine()
                append("It only leaves the unread list. They are not told, and nothing else changes.")
            }.trim(),
        )
    }

    override fun recheck(params: JsonObject, context: TeamToolContext): String? {
        val scoped = scope.element(PathElementKind.FEEDBACK, params.uuid("feedback_id"), context.projectId)
            ?: return goneSince(PathElementKind.FEEDBACK)
        val alreadyRead = onboardingFeedbackService
            .getAllFeedbackByUserId(scoped.owner.userId)
            .firstOrNull { it.id == scoped.element.id }
            ?.read
        return "Somebody already marked that feedback as read, so nothing was changed.".takeIf { alreadyRead == true }
    }

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        val id: UUID = requireNotNull(params.uuid("feedback_id"))
        onboardingFeedbackService.markFeedbackAsRead(id)
        return "Marked as read."
    }
}
