package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.UserGoal
import com.sprintstart.sprintstartbackend.onboarding.model.response.goal.GoalView
import com.sprintstart.sprintstartbackend.onboarding.repository.StarterWorkTaskProposalRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.UserGoalRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
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
    private val userApi: UserApi,
) {
    /**
     * Claims a live starter-work task as this hire's goal for [projectId], replacing any goal they
     * had claimed there before.
     *
     * @throws ResponseStatusException 404 if the proposal doesn't exist; 409 if it is not `LIVE`
     * (a rejected task is not something a hire may commit to).
     */
    @Transactional
    fun claimForMe(authId: String, projectId: UUID, proposalId: UUID): GoalView {
        val userId = resolveUserId(authId)
        val proposal = starterWorkTaskProposalRepository.findById(proposalId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "No starter-work task found with id: $proposalId")
        }
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

    private fun resolveUserId(authId: String): UUID =
        userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: $authId") }
}
