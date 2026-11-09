package com.sprintstart.sprintstartbackend.starterwork.service

import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.starterwork.external.enums.GoalClaimant
import com.sprintstart.sprintstartbackend.starterwork.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.starterwork.external.event.GoalClaimedEvent
import com.sprintstart.sprintstartbackend.starterwork.model.entity.UserGoal
import com.sprintstart.sprintstartbackend.starterwork.model.response.goal.GoalView
import com.sprintstart.sprintstartbackend.starterwork.repository.StarterWorkTaskProposalRepository
import com.sprintstart.sprintstartbackend.starterwork.repository.UserGoalRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

/**
 * A hire claiming, reading and dropping the contribution their path aims at.
 *
 * The hire chooses, not a PM and not a scoring function: `GET /me/matches` already ranks the live
 * starter-work pool by fit, and this turns one of those into a commitment. A rejected task cannot
 * be claimed, so this is a choice within a curated set, not an open field.
 *
 * "No goal yet" is a real, nameable state rather than an error or a silent fallback to the whole
 * graph: the path is simply the project's baseline until a hire picks a destination, and the
 * payload says so.
 */
@Service
class UserGoalService(
    private val userGoalRepository: UserGoalRepository,
    private val starterWorkTaskProposalRepository: StarterWorkTaskProposalRepository,
    private val starterWorkPoolReconciler: StarterWorkPoolReconciler,
    private val userApi: UserApi,
    private val eventPublisher: ApplicationEventPublisher,
) {
    /**
     * Claims a live starter-work task as this hire's goal for [projectId], replacing any goal they
     * had claimed there before.
     *
     * Publishes [GoalClaimedEvent] inside the claim's transaction.
     *
     * @param by Who is claiming: the hire by hand, or the buddy behind a confirm the hire pressed.
     * @throws ResponseStatusException 404 if the proposal doesn't exist; 409 if it is not `LIVE`
     * (a rejected task is not something a hire may commit to).
     */
    @Transactional
    fun claimForMe(
        authId: String,
        projectId: UUID,
        proposalId: UUID,
        by: GoalClaimant = GoalClaimant.HIRE,
    ): GoalView {
        val userId = resolveUserId(authId)
        val proposal = starterWorkTaskProposalRepository.findById(proposalId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "No starter-work task found with id: $proposalId")
        }
        // Checked against the source first: reconciliation runs on a clock, and claiming is the one moment a
        // stale answer costs somebody real work.
        starterWorkPoolReconciler.reconcileOne(proposal)
        if (proposal.status != ProposalStatus.LIVE) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Starter-work task $proposalId is ${proposal.status}; only a live task can be claimed as a goal",
            )
        }

        val existing = userGoalRepository.findByUserIdAndProjectId(userId, projectId)
        val goal = existing?.apply {
            sourceProposalId = proposal.id
            claimedAt = Instant.now()
        } ?: UserGoal(
            userId = userId,
            projectId = projectId,
            sourceProposalId = proposal.id,
        )
        userGoalRepository.save(goal)
        eventPublisher.publishEvent(GoalClaimedEvent(UserId(userId), ProjectId(projectId), by))

        return GoalView(
            proposalId = proposal.id,
            title = proposal.title,
            summary = proposal.summary,
            sourceUrl = proposal.sourceUrl,
        )
    }

    /** Drops this hire's goal for [projectId]. */
    @Transactional
    fun clearForMe(authId: String, projectId: UUID) {
        userGoalRepository.deleteByUserIdAndProjectId(resolveUserId(authId), projectId)
    }

    /**
     * This hire's claimed goal for [projectId], or `null` when they haven't picked one.
     *
     * Resolved against the live proposal, so a goal whose task has since been removed reads as "no
     * goal" rather than pointing at something that is no longer there.
     */
    @Transactional(readOnly = true)
    fun findForUser(userId: UUID, projectId: UUID): GoalView? {
        val goal = userGoalRepository.findByUserIdAndProjectId(userId, projectId) ?: return null
        val proposal = starterWorkTaskProposalRepository.findById(goal.sourceProposalId).orElse(null)
            ?: return null
        return GoalView(
            proposalId = proposal.id,
            title = proposal.title,
            summary = proposal.summary,
            sourceUrl = proposal.sourceUrl,
        )
    }

    /** When [userId] claimed their goal on [projectId], or null when they have none there. */
    @Transactional(readOnly = true)
    fun claimedAt(userId: UserId, projectId: ProjectId): Instant? =
        userGoalRepository.findByUserIdAndProjectId(userId.value, projectId.value)?.claimedAt

    private fun resolveUserId(authId: String): UUID =
        userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: $authId") }
}
