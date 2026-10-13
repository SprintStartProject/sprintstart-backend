package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.springframework.stereotype.Component
import java.util.UUID

/** The member whose path a confirmed reset deletes. */
@Serializable
data class ResetMemberPathParams(
    @SerialName("member_id") @Serializable(with = UuidSerializer::class) val memberId: UUID,
)

/**
 * Offers to delete a member's whole onboarding path.
 *
 * The largest single thing the content area can do, and the one that throws the most of a person's
 * work away — so the preview says how much of it there is, in numbers, rather than leaving the
 * manager to imagine it.
 */
@Component
class ResetMemberPathAction(
    private val scope: ContentScope,
    private val pathElements: PathElements,
    private val onboardingPathService: OnboardingPathService,
) : TypedTeamAction<ResetMemberPathParams>(ResetMemberPathParams.serializer()) {
    override val area = TeamArea.CONTENT
    override val risk = BuddyProposalRisk.DESTRUCTIVE
    override val spec = BuddyToolSpecDto(
        name = "reset_member_path",
        description = "Offer to delete a member's whole onboarding path — every phase, step, task and link, " +
            "and everything they got through. It is the biggest thing here and cannot be undone; prefer " +
            "deleting the one phase or step that is wrong. Use the member_id from find_member. This does NOT " +
            "delete anything by itself; the manager confirms.",
        parameters = stringFields("member_id" to "The member_id from find_member.", required = listOf("member_id")),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val owner = scope.member(call.uuidArgument("member_id"), context.projectId)
            ?: return TeamActionDraft.Refused(NOT_A_MEMBER_HERE)
        val summary = pathElements.pathOf(owner.userId)
            ?: return TeamActionDraft.Refused(
                "${owner.displayName} has no onboarding path, so there is nothing to delete.",
            )

        return proposed(
            params = ResetMemberPathParams(owner.userId),
            label = "Delete ${owner.displayName.forLabel()}'s whole path",
            preview = buildString {
                appendLine("Delete ${owner.displayName}'s whole onboarding path.")
                appendLine()
                append(
                    "That is ${summary.phases} phase${if (summary.phases == 1) "" else "s"} and ${summary.steps} step",
                )
                append(if (summary.steps == 1) "" else "s")
                appendLine(", with their tasks, links, skip requests and feedback.")
                if (summary.checks > 0) appendLine("${summary.checks} knowledge-check questions go too.")
                if (summary.finishedSteps > 0) {
                    appendLine(
                        "${owner.displayName} has already got through ${summary.finishedSteps} of those steps; " +
                            "that progress is deleted with them.",
                    )
                }
                append("Nothing here makes a new path. This cannot be undone.")
                scope.sharedNote(owner, context.projectId).takeIf { it.isNotEmpty() }?.let { append("\n\n$it") }
            }.trim(),
        )
    }

    override fun recheckParams(params: ResetMemberPathParams, context: TeamToolContext): String? {
        val owner = scope.member(params.memberId, context.projectId) ?: return LEFT_SINCE_HERE
        return "That person's path is already gone, so nothing was changed."
            .takeIf { pathElements.pathOf(owner.userId) == null }
    }

    override suspend fun performParams(params: ResetMemberPathParams, context: TeamToolContext): String {
        onboardingPathService.deleteOnboardingPathByUserId(params.memberId)
        return "Deleted. The path and everything on it are gone."
    }
}
