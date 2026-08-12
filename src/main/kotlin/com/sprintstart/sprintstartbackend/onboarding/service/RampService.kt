package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencySource
import com.sprintstart.sprintstartbackend.onboarding.external.enums.RampStage
import com.sprintstart.sprintstartbackend.onboarding.model.entity.AutonomyMilestone
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Competency
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingTrack
import com.sprintstart.sprintstartbackend.onboarding.model.entity.UserCompetencyState
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.ramp.AutonomyResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.ramp.MyRampResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.AutonomyMilestoneRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.KnowledgeRequestRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.StarterWorkTaskProposalRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.TaskZeroAssignmentRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.UserCompetencyStateRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.UserGoalRepository
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.util.UUID

/**
 * The ramp of real tasks, and the exit from onboarding.
 *
 * Three decisions shape this service.
 *
 * **The ledger is written by accepted work, not by chat.** An accepted [Contribution] credits the
 * competencies of the task it was claimed against, at that competency's own target level, with
 * [CompetencySource.VERIFIED] and the same monotonic rule as every other ledger writer — it
 * never lowers what somebody already showed. Chat placement stays a weak prior that accepted work
 * outranks, never the other way round. What counts as accepted work is [ContributionService]'s
 * question, not this service's: today every contribution is a merged pull request, and this
 * service reads none of that detail.
 *
 * **Task 0 credits nothing, by construction rather than by convention.** Credit is derived from the
 * *claimed goal*, and Task 0 is an assignment, not a goal — so there is no code path that could
 * credit it. That is deliberate: its job is confidence and mechanics, and a ledger entry for
 * "opened a pull request once" would be a lie about competence.
 *
 * **Autonomy is an event, not a state.** The exit condition is a task completed with no help from
 * a person (an escalation to the PM, the surviving human channel) and no rework, which is
 * the honest operational definition of "can be left alone here" — not "all nodes mastered". The
 * moment is recorded once ([AutonomyMilestone]) so it can be announced and dated; recomputing it
 * would only ever yield a boolean, and a boolean cannot be announced.
 */
@Service
class RampService(
    private val taskZeroAssignmentRepository: TaskZeroAssignmentRepository,
    private val userGoalRepository: UserGoalRepository,
    private val starterWorkTaskProposalRepository: StarterWorkTaskProposalRepository,
    private val userCompetencyStateRepository: UserCompetencyStateRepository,
    private val competencyRepository: CompetencyRepository,
    private val autonomyMilestoneRepository: AutonomyMilestoneRepository,
    private val knowledgeRequestRepository: KnowledgeRequestRepository,
    private val projectMembershipApi: ProjectMembershipApi,
    private val contributionService: ContributionService,
    private val trackService: TrackService,
    // The board shows the same task on a card; which task somebody is on is not a question two
    // readers should be able to answer differently.
    private val currentTaskReader: CurrentTaskReader,
    private val clock: Clock = Clock.systemUTC(),
) {
    /**
     * A hire's ramp on one project, crediting any merged work not yet credited.
     *
     * Crediting happens lazily on read for the same reason Task 0 assigns lazily: it covers work
     * that merged while nobody was looking, with no scheduler and no backfill. It is idempotent —
     * the ledger write is a monotonic find-or-create, so reading twice credits once.
     *
     * @throws ResponseStatusException 404 when the hire is not a member of the project.
     */
    @Transactional
    fun getForHire(hireId: UUID, projectId: UUID): MyRampResponse {
        val member = requireMember(hireId, projectId)
        val track = trackService.forMember(member)
        val accepted = contributionService.forHire(member, projectId).filter { it.isAccepted }

        val credited = creditAcceptedWork(hireId, projectId, accepted)
        val autonomy = evaluateAutonomy(hireId, projectId, accepted, track)
        val currentTask = currentTaskReader.currentTaskFor(hireId, projectId)

        return MyRampResponse(
            stage = stageOf(accepted.size, autonomy.reached),
            currentTask = currentTask?.toResponse(),
            unlockedBy = unlockedBy(accepted.size, autonomy.reached, track),
            mergedCount = accepted.size,
            creditedCompetencyKeys = credited,
            autonomy = autonomy,
        )
    }

    /** When a hire reached autonomy on a project, for the PM readout. Never writes. */
    @Transactional(readOnly = true)
    fun autonomyReachedAtFor(hireId: UUID, projectId: UUID) =
        autonomyMilestoneRepository.findByHireIdAndProjectId(hireId, projectId)?.reachedAt

    /**
     * The stage a hire is on.
     *
     * Counted in *accepted contributions*, because that is the only unit of progress the ramp
     * recognises. Task 0 is where somebody sits before anything of theirs has been accepted —
     * including a hire with one contribution still in flight, since work nobody has taken has not
     * proven the loop.
     */
    private fun stageOf(acceptedCount: Int, autonomous: Boolean): RampStage = when {
        autonomous -> RampStage.AUTONOMOUS
        acceptedCount == 0 -> RampStage.TASK_ZERO
        acceptedCount == 1 -> RampStage.TASK_ONE
        else -> RampStage.TASK_TWO_PLUS
    }

    /**
     * What the hire has done, in their own track's words.
     *
     * An engineer reads exactly what they read before ("You merged your first change here"); a
     * delivery lead reads about facilitated ceremonies. The sentence structure is fixed and the
     * nouns come from the track, which is the whole reason vocabulary is structured fields rather
     * than free text a track could rewrite.
     */
    private fun unlockedBy(acceptedCount: Int, autonomous: Boolean, track: OnboardingTrack): String = when {
        autonomous -> "You ${track.contributionVerbPast} a ${track.contributionNoun} with no help and no rework"
        acceptedCount == 0 ->
            "You haven't ${track.contributionVerbPast} anything here yet — that's the whole first step"
        acceptedCount == 1 -> "You ${track.contributionVerbPast} your first ${track.contributionNoun} here"
        else -> "You've ${track.contributionVerbPast} $acceptedCount ${track.contributionNounPlural} here"
    }

    /**
     * Writes ledger credit for competencies proven by accepted work.
     *
     * **What acceptance is attributed to.** Nothing links a contribution to the task it was for, so
     * it is attributed to the goal the hire had *claimed at the time* — work accepted after the
     * claim. That is an approximation and worth naming: without a task↔contribution link there is
     * no exact answer, and the claimed goal is the best evidence available. It cannot over-credit
     * an unrelated person's work, because attribution is enforced where contributions are built.
     *
     * @return The competency keys credited, for the hire to see what their work counted for.
     */
    private fun creditAcceptedWork(
        hireId: UUID,
        projectId: UUID,
        accepted: List<Contribution>,
    ): List<String> {
        val goal = userGoalRepository.findByUserIdAndProjectId(hireId, projectId) ?: return emptyList()
        val proposal = goal.sourceProposalId
            ?.let { starterWorkTaskProposalRepository.findById(it).orElse(null) }
            ?: return emptyList()

        val qualifying = accepted.any { it.acceptedAt?.isAfter(goal.claimedAt) == true }
        if (!qualifying) return emptyList()

        val competencies = competencyRepository.findAllByKeyIn(proposal.competencyKeys).associateBy { it.key }
        return proposal.competencyKeys.mapNotNull { key ->
            val competency = competencies[key] ?: return@mapNotNull null
            creditCompetency(hireId, competency)
            key
        }
    }

    /**
     * Monotonic find-or-create, mirroring `VerificationService`'s ledger write.
     *
     * Credit lands at the competency's **own target level**: accepted work is evidence of meeting
     * the bar the project set for that competency, not of some level the work itself implies.
     */
    private fun creditCompetency(hireId: UUID, competency: Competency) {
        val existing = userCompetencyStateRepository.findByUserIdAndCompetencyKey(hireId, competency.key)
        if (existing != null) {
            // Never un-earns: accepted work cannot lower a level already shown, only raise it and
            // upgrade the source to VERIFIED.
            existing.level = maxOf(existing.level, competency.targetLevel)
            existing.source = CompetencySource.VERIFIED
            existing.updatedAt = clock.instant()
        } else {
            userCompetencyStateRepository.save(
                UserCompetencyState(
                    userId = hireId,
                    competencyKey = competency.key,
                    level = competency.targetLevel,
                    source = CompetencySource.VERIFIED,
                ),
            )
        }
    }

    /**
     * Whether a hire has shown they can work here unsupervised, and what is missing if not.
     *
     * The condition is evaluated against the **most recently accepted contribution**, because
     * autonomy is a claim about how somebody works now. Both halves must hold on that one task: it
     * was not sent back, and no person was pulled in between submitting it and its acceptance.
     */
    private fun evaluateAutonomy(
        hireId: UUID,
        projectId: UUID,
        accepted: List<Contribution>,
        track: OnboardingTrack,
    ): AutonomyResponse {
        autonomyMilestoneRepository.findByHireIdAndProjectId(hireId, projectId)?.let {
            return AutonomyResponse(
                reached = true,
                reachedAt = it.reachedAt,
                provenByArtifactId = it.provenByArtifactId,
                blockers = emptyList(),
            )
        }

        // Safe by the Contribution invariant: an ACCEPTED contribution always carries an acceptedAt.
        val latest = accepted.maxByOrNull { it.acceptedAt!! }
            ?: return AutonomyResponse(
                reached = false,
                reachedAt = null,
                provenByArtifactId = null,
                blockers = listOf("No ${track.contributionVerbPast} ${track.contributionNoun} here yet"),
            )

        val blockers = mutableListOf<String>()
        if (latest.returnedCount > 0) {
            blockers += "Your last ${track.contributionVerbPast} ${track.contributionNoun} was sent back for rework"
        }
        if (neededHelp(hireId, projectId, latest)) {
            blockers += "You pulled in a person while you were on your last ${track.contributionNoun}"
        }
        if (blockers.isNotEmpty()) {
            return AutonomyResponse(
                reached = false,
                reachedAt = null,
                provenByArtifactId = null,
                blockers = blockers,
            )
        }

        // Recorded at the acceptance itself, not at the moment we noticed -- the date has to be the
        // one that actually happened, or the announcement is about our polling.
        val milestone = autonomyMilestoneRepository.save(
            AutonomyMilestone(
                hireId = hireId,
                projectId = projectId,
                reachedAt = latest.acceptedAt!!,
                provenByArtifactId = latest.evidenceRef,
            ),
        )
        return AutonomyResponse(
            reached = true,
            reachedAt = milestone.reachedAt,
            provenByArtifactId = milestone.provenByArtifactId,
            blockers = emptyList(),
        )
    }

    /**
     * Whether the hire needed a person while this contribution was in flight.
     *
     * Scoped to the contribution's own window rather than "ever": a hire who needed help in week
     * one and delivered week four's work alone has demonstrated exactly what the exit condition
     * asks about. "Help" is what the surviving human channel records: the assigned-buddy loop is
     * retired, so this is now an escalation to the PM (flag-to-PM) during the window — the only
     * reaching out a hire can still do.
     */
    private fun neededHelp(hireId: UUID, projectId: UUID, contribution: Contribution): Boolean {
        val opened = contribution.openedAt ?: return false
        val accepted = contribution.acceptedAt ?: return false
        return knowledgeRequestRepository
            .findAllByHireIdAndProjectId(hireId, projectId)
            .any { !it.createdAt.isBefore(opened) && !it.createdAt.isAfter(accepted) }
    }

    private fun requireMember(hireId: UUID, projectId: UUID): ProjectMember =
        projectMembershipApi.getProjectMembers(projectId).firstOrNull { it.userId == hireId }
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "User $hireId is not a member of project $projectId",
            )
}
