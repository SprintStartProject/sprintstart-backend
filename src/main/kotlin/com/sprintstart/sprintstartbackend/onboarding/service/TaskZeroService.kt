package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.StarterWorkTaskProposal
import com.sprintstart.sprintstartbackend.onboarding.model.entity.TaskZeroAssignment
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.starterwork.MyTaskZeroResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.starterwork.StarterWorkTaskProposalResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.StarterWorkTaskProposalRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.TaskZeroAssignmentRepository
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Task 0: the trivial first task that proves the branch → PR → review → merge loop.
 *
 * Assignment is automatic on the hire's first read — a first day should never end with "pick
 * something" — and undoable. Deliberately **not** gated on any environment-readiness signal:
 * getting the project running is *part of* Task 0, not a wall in front of it, and gating the first
 * task behind a setup check we can't reliably detect only strands a fresh hire (it also runs against
 * the "no gates" rule). The task comes from the same live pool as every other,
 * flagged as Task-0-suitable by a PM, because a task nobody wanted is not a
 * contribution and hires can tell.
 *
 * Completing it proves the loop and nothing else: there is **no** `UserCompetencyState` write
 * anywhere here, and this service does not depend on the ledger. "Loop proven" is derived — the
 * hire has merged a pull request — never stored as earned competence.
 */
@Service
class TaskZeroService(
    private val starterWorkTaskProposalRepository: StarterWorkTaskProposalRepository,
    private val taskZeroAssignmentRepository: TaskZeroAssignmentRepository,
    private val projectMembershipApi: ProjectMembershipApi,
    private val contributionService: ContributionService,
    private val clock: Clock = Clock.systemUTC(),
) {
    /**
     * Sets whether a live starter-work task is a Task 0 candidate.
     *
     * @throws ResponseStatusException 404 when no such proposal exists; 409 when it is not `LIVE`
     * (Task-0 candidacy is a property of the pool, not of something somebody removed).
     */
    @Transactional
    fun setEligibility(proposalId: UUID, eligible: Boolean): StarterWorkTaskProposalResponse {
        val proposal = starterWorkTaskProposalRepository.findById(proposalId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "No starter-work task found with id $proposalId")
        }
        if (proposal.status != ProposalStatus.LIVE) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Only a live task can be flagged for Task 0",
            )
        }
        proposal.taskZeroEligible = eligible
        return starterWorkTaskProposalRepository.save(proposal).toResponse()
    }

    /**
     * The hire's Task 0, assigning one on read if none is assigned yet.
     *
     * The assignment happens lazily here rather than on a background trigger. It is available from
     * day one — no environment-readiness precondition — so `noneAvailable` (no PM has flagged a
     * Task-0-suitable task yet) is the only "no task" state.
     *
     * @throws ResponseStatusException 404 when the hire is not a member of the project.
     */
    @Transactional
    fun getForHire(hireId: UUID, projectId: UUID): MyTaskZeroResponse {
        val member = requireMember(hireId, projectId)
        val loopProven = hasAcceptedContribution(member, projectId)

        taskZeroAssignmentRepository.findByHireIdAndProjectId(hireId, projectId)?.let { existing ->
            return existing.toResponse(loopProven)
        }

        val candidate = nextEligibleTask()
            ?: return MyTaskZeroResponse(
                task = null,
                assignedAt = null,
                noneAvailable = true,
                loopProven = loopProven,
            )

        val saved = taskZeroAssignmentRepository.save(
            TaskZeroAssignment(
                hireId = hireId,
                projectId = projectId,
                proposalId = candidate.id,
                assignedAt = clock.instant(),
            ),
        )
        return MyTaskZeroResponse(
            task = candidate.toResponse(),
            assignedAt = saved.assignedAt,
            noneAvailable = false,
            loopProven = loopProven,
        )
    }

    /**
     * Undoes a hire's Task 0 assignment, freeing the task for someone else. A no-op when nothing is
     * assigned. Touches no earned progress — there is none to un-earn.
     */
    @Transactional
    fun unassign(hireId: UUID, projectId: UUID) {
        taskZeroAssignmentRepository.deleteByHireIdAndProjectId(hireId, projectId)
    }

    /** When Task 0 was assigned, for the metrics timeline. Read-only, never assigns as a side effect. */
    @Transactional(readOnly = true)
    fun assignedAtFor(hireId: UUID, projectId: UUID): Instant? =
        taskZeroAssignmentRepository.findByHireIdAndProjectId(hireId, projectId)?.assignedAt

    private fun nextEligibleTask(): StarterWorkTaskProposal? {
        val taken = taskZeroAssignmentRepository.findAllAssignedProposalIds().toSet()
        return starterWorkTaskProposalRepository
            .findAllByStatusAndTaskZeroEligibleTrue(ProposalStatus.LIVE)
            .filter { it.id !in taken }
            .minByOrNull { it.createdAt }
    }

    /**
     * Whether anything this hire produced has been accepted here yet.
     *
     * This is the same question the ramp asks to decide somebody has left stage zero, so it reads
     * the same contribution stream rather than re-deriving it from pull requests -- two answers to
     * "have they completed anything" that could disagree is exactly the kind of drift that put
     * "writes code" into the definition of progress in the first place.
     */
    private fun hasAcceptedContribution(member: ProjectMember, projectId: UUID): Boolean {
        return contributionService.forHire(member, projectId).any { it.isAccepted }
    }

    private fun requireMember(hireId: UUID, projectId: UUID): ProjectMember =
        projectMembershipApi.getProjectMembers(projectId).firstOrNull { it.userId == hireId }
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "User $hireId is not a member of project $projectId",
            )

    private fun TaskZeroAssignment.toResponse(loopProven: Boolean): MyTaskZeroResponse {
        val proposal = starterWorkTaskProposalRepository.findById(proposalId).orElse(null)
        return MyTaskZeroResponse(
            task = proposal?.toResponse(),
            assignedAt = assignedAt,
            noneAvailable = false,
            loopProven = loopProven,
        )
    }
}
