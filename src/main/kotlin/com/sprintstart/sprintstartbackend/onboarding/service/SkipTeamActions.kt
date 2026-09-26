package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.enums.SkipStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.model.request.skip.ReviewOnboardingSkipRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.skip.GetOnboardingSkipResponse
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/*
 * The skip-request actions of team mode's content area.
 *
 * A hire asks to skip a step and the request waits for somebody to answer it. Accepting and denying
 * are the two places in this area where an action changes a hire's progress — an accepted skip marks
 * the step skipped, which counts as done — and both are explicitly the manager's decision in the
 * admin surface these mirror. Each preview says what the answer does to the step.
 *
 * Only a pending request can be answered or deleted; the services say so at the write, and these
 * say so at proposal and again at confirm, so a request somebody else answered in between is turned
 * down rather than failing halfway.
 */

private const val NOT_PENDING = "That skip request has already been answered, so there is nothing to decide."

/** A pending skip request on a member's path. */
private class PendingSkip(
    val scoped: ScopedElement,
    val skip: GetOnboardingSkipResponse,
)

/** What looking up a skip request found. */
private sealed interface SkipLookup {
    data class Found(
        val pending: PendingSkip,
    ) : SkipLookup

    data class Refused(
        val reason: String,
    ) : SkipLookup
}

private fun lookUpPendingSkip(
    scope: ContentScope,
    skips: OnboardingSkipService,
    id: UUID?,
    projectId: UUID,
): SkipLookup {
    val scoped = scope.element(PathElementKind.SKIP, id, projectId)
        ?: return SkipLookup.Refused(notInScope(PathElementKind.SKIP))
    // Gone between the scope check and this read is the same answer as gone before it.
    val skip = try {
        skips.getSkipById(scoped.element.id)
    } catch (_: ResponseStatusException) {
        return SkipLookup.Refused(goneSince(PathElementKind.SKIP))
    }
    if (skip.status != SkipStatus.PENDING) return SkipLookup.Refused(NOT_PENDING)
    return SkipLookup.Found(PendingSkip(scoped, skip))
}

/** What the step's status says about what an answer does, beyond what the answer itself does. */
private fun StepStatus?.beforeDenying(name: String): String =
    if (this == StepStatus.IN_PROGRESS) {
        "$name had started it; denying puts the step back to waiting. "
    } else {
        ""
    }

private fun SkipLookup.pendingOrNull(): PendingSkip? = (this as? SkipLookup.Found)?.pending

private fun SkipLookup.reason(): String = (this as? SkipLookup.Refused)?.reason.orEmpty()

private fun reviewParams(pending: PendingSkip, comment: String): JsonObject =
    buildJsonObject {
        put("skip_id", pending.skip.id.toString())
        put("review_comment", comment)
    }

/** The comment, quoted in full, or a sentence saying there is none. */
private fun commentLine(comment: String): String =
    if (comment.isEmpty()) "No comment goes with it." else "It goes with this comment: “$comment”"

/** Offers to let a hire skip a step. */
@Component
class AcceptSkipAction(
    private val scope: ContentScope,
    private val onboardingSkipService: OnboardingSkipService,
) : TeamActionHandler {
    override val area = TeamArea.CONTENT
    override val risk = BuddyProposalRisk.STANDARD
    override val spec = BuddyToolSpecDto(
        name = "accept_skip",
        description = "Offer to accept a hire's request to skip a step. The step is marked skipped and counts " +
            "as done for them. Use the skip_id from list_pending_skips and read what they wrote first. A " +
            "comment to the hire is optional. This does NOT accept anything by itself; the manager confirms.",
        parameters = stringFields(
            "skip_id" to "The skip_id from list_pending_skips.",
            "review_comment" to "Optional. A note the hire will see with the answer.",
            required = listOf("skip_id"),
        ),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val lookup = lookUpPendingSkip(scope, onboardingSkipService, call.uuidArgument("skip_id"), context.projectId)
        val pending = lookup.pendingOrNull() ?: return TeamActionDraft.Refused(lookup.reason())
        val comment = call.textArgument("review_comment")
        val owner = pending.scoped.owner.displayName
        val step = pending.scoped.element

        return TeamActionDraft.Proposed(
            params = reviewParams(pending, comment),
            label = "Let $owner skip “${step.title.forLabel()}”",
            preview = buildString {
                appendLine("Accept $owner's request to skip the step “${step.title}”.")
                appendLine("Their reason: “${pending.skip.reason}”")
                appendLine(commentLine(comment))
                appendLine()
                append(
                    "The step is marked skipped and counts as done in their progress. If it was the last " +
                        "step they had open, that finishes their onboarding.",
                )
                scope
                    .sharedNote(pending.scoped.owner, context.projectId)
                    .takeIf { it.isNotEmpty() }
                    ?.let { append("\n\n$it") }
            }.trim(),
        )
    }

    override fun recheck(params: JsonObject, context: TeamToolContext): String? =
        recheckPending(scope, onboardingSkipService, params, context)

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        onboardingSkipService.acceptSkipById(
            requireNotNull(params.uuid("skip_id")),
            ReviewOnboardingSkipRequest(reviewComment = params.text("review_comment")),
        )
        return "Accepted. The step is skipped, and it counts as done for them."
    }
}

/** Offers to refuse a hire's request to skip a step. */
@Component
class DenySkipAction(
    private val scope: ContentScope,
    private val onboardingSkipService: OnboardingSkipService,
) : TeamActionHandler {
    override val area = TeamArea.CONTENT
    override val risk = BuddyProposalRisk.STANDARD
    override val spec = BuddyToolSpecDto(
        name = "deny_skip",
        description = "Offer to deny a hire's request to skip a step. A denial needs a comment saying why, " +
            "which the hire will see; ask the manager for one rather than inventing it. Use the skip_id from " +
            "list_pending_skips. This does NOT deny anything by itself; the manager confirms.",
        parameters = stringFields(
            "skip_id" to "The skip_id from list_pending_skips.",
            "review_comment" to "Why it is denied, in the manager's words. Required.",
            required = listOf("skip_id", "review_comment"),
        ),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val comment = call.textArgument("review_comment")
        if (comment.isEmpty()) {
            return TeamActionDraft.Refused(
                "A denial needs a comment saying why, because the hire sees it. Ask the manager what to write.",
            )
        }
        val lookup = lookUpPendingSkip(scope, onboardingSkipService, call.uuidArgument("skip_id"), context.projectId)
        val pending = lookup.pendingOrNull() ?: return TeamActionDraft.Refused(lookup.reason())
        val owner = pending.scoped.owner.displayName
        val step = pending.scoped.element

        return TeamActionDraft.Proposed(
            params = reviewParams(pending, comment),
            label = "Deny $owner's skip of “${step.title.forLabel()}”",
            preview = buildString {
                appendLine("Deny $owner's request to skip the step “${step.title}”.")
                appendLine("Their reason: “${pending.skip.reason}”")
                appendLine("They are told: “$comment”")
                appendLine()
                append(step.stepStatus.beforeDenying(owner))
                append("The step stays for them to do.")
                scope
                    .sharedNote(pending.scoped.owner, context.projectId)
                    .takeIf { it.isNotEmpty() }
                    ?.let { append("\n\n$it") }
            }.trim(),
        )
    }

    override fun recheck(params: JsonObject, context: TeamToolContext): String? =
        recheckPending(scope, onboardingSkipService, params, context)

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        onboardingSkipService.denySkipById(
            requireNotNull(params.uuid("skip_id")),
            ReviewOnboardingSkipRequest(reviewComment = params.text("review_comment")),
        )
        return "Denied. They have your comment, and the step is theirs to do."
    }
}

/** Offers to delete a pending skip request without answering it. */
@Component
class DeleteSkipAction(
    private val scope: ContentScope,
    private val onboardingSkipService: OnboardingSkipService,
) : TeamActionHandler {
    override val area = TeamArea.CONTENT
    override val risk = BuddyProposalRisk.DESTRUCTIVE
    override val spec = BuddyToolSpecDto(
        name = "delete_skip",
        description = "Offer to delete a pending skip request without answering it, so the hire hears nothing. " +
            "Prefer accept_skip or deny_skip, which tell them. Use the skip_id from list_pending_skips. This " +
            "does NOT delete anything by itself; the manager confirms.",
        parameters = stringFields("skip_id" to "The skip_id from list_pending_skips.", required = listOf("skip_id")),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val lookup = lookUpPendingSkip(scope, onboardingSkipService, call.uuidArgument("skip_id"), context.projectId)
        val pending = lookup.pendingOrNull() ?: return TeamActionDraft.Refused(lookup.reason())
        val owner = pending.scoped.owner.displayName
        val step = pending.scoped.element

        return TeamActionDraft.Proposed(
            params = buildJsonObject { put("skip_id", pending.skip.id.toString()) },
            label = "Delete $owner's skip request",
            preview = buildString {
                appendLine("Delete $owner's request to skip “${step.title}”, without answering it.")
                appendLine("Their reason: “${pending.skip.reason}”")
                appendLine()
                append(
                    "They are not told anything, and the step stays as it is. Accepting or denying it would " +
                        "tell them. This cannot be undone.",
                )
            }.trim(),
        )
    }

    override fun recheck(params: JsonObject, context: TeamToolContext): String? =
        recheckPending(scope, onboardingSkipService, params, context)

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        onboardingSkipService.deleteSkipById(requireNotNull(params.uuid("skip_id")))
        return "Deleted. The request is gone; they were not told."
    }
}

/** The refusal for a stored skip proposal whose request is gone, out of scope, or already answered. */
private fun recheckPending(
    scope: ContentScope,
    skips: OnboardingSkipService,
    params: JsonObject,
    context: TeamToolContext,
): String? =
    when (val found = lookUpPendingSkip(scope, skips, params.uuid("skip_id"), context.projectId)) {
        is SkipLookup.Refused -> found.reason
        is SkipLookup.Found -> null
    }
