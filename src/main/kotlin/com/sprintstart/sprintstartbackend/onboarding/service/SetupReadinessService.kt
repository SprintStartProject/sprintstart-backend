package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.StarterWorkTaskProposal
import com.sprintstart.sprintstartbackend.onboarding.model.response.setup.RungState
import com.sprintstart.sprintstartbackend.onboarding.model.response.setup.SetupReadinessResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.setup.SetupRungResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.StarterWorkTaskProposalRepository
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * "Is this project ready to onboard someone?" answered as a ladder of the three setup stages the
 * onboarding module owns: a vocabulary, a stocked pool of starter tasks, and roles that say which
 * track their people onboard on. (The corpus stage lives in the ingestion module; see
 * [SetupReadinessResponse].)
 *
 * Every number is composed on read from the same rows each stage's own page reads, so this can
 * never disagree with those pages.
 *
 * ⚠️ A rung reports an outcome, never a chore. Nothing here is waiting on the PM -- adding a rung
 * that asks somebody for something nothing reads makes the ladder look like progress while they
 * do it.
 */
@Service
class SetupReadinessService(
    private val competencyGraphAuthoringService: CompetencyGraphAuthoringService,
    private val starterWorkTaskProposalRepository: StarterWorkTaskProposalRepository,
    private val projectMembershipApi: ProjectMembershipApi,
    private val trackService: TrackService,
) {
    @Transactional(readOnly = true)
    fun getReadiness(projectId: UUID): SetupReadinessResponse {
        val approvedCompetencies = competencyGraphAuthoringService.getGraph().competencies.size
        val rungs = listOf(
            vocabularyRung(approvedCompetencies),
            starterTasksRung(),
            tracksRung(projectId),
        )
        return SetupReadinessResponse(
            projectId = projectId,
            rungs = rungs,
            ready = rungs.all { it.state == RungState.OK },
        )
    }

    /**
     * Whether a vocabulary exists to teach and measure against.
     *
     * No longer counts anything "waiting for your review": competency proposals are gone, because
     * generation writes live rows and a human corrects them rather than gating them. So this rung
     * reports what exists rather than what somebody owes.
     */
    private fun vocabularyRung(competencies: Int): SetupRungResponse =
        if (competencies == 0) {
            rung(
                SKILL_MAP,
                RungState.WARN,
                0,
                "No competencies yet. They are generated from the ingested corpus — connect a " +
                    "repository, or add one by hand.",
            )
        } else {
            rung(SKILL_MAP, RungState.OK, competencies, "$competencies ${competencyWord(competencies)}.")
        }

    /**
     * Whether there is starter work to claim — and whether it covers every track in use.
     *
     * A count alone hid a real failure: a project can have plenty of approved tasks and still have
     * none a delivery lead could do, because every one of them was a mined GitHub issue. A hire in
     * that position sees an empty suggestion list on a project the ladder called ready.
     *
     * Unscoped tasks count toward every track: null means "suits any role", so they are coverage
     * for everybody rather than for nobody.
     */
    private fun starterTasksRung(): SetupRungResponse {
        val liveTasks = starterWorkTaskProposalRepository.findAllByStatus(ProposalStatus.LIVE)
        val live = liveTasks.size
        val unreviewed = liveTasks.count { !it.reviewed }
        val uncovered = uncoveredTracks(liveTasks)

        // ⚠️ Unreviewed tasks are *not* a chore this rung nags about. They are claimable; review
        // only lifts the matcher's demotion. Saying "N waiting for your review" makes this a queue
        // again, and a rung nobody can clear by doing something real is how a ladder stops being
        // read.
        val reviewNote = if (unreviewed > 0) {
            " $unreviewed of them nobody has looked at yet — claimable, just ranked lower."
        } else {
            ""
        }

        return when {
            live > 0 && uncovered.isNotEmpty() -> rung(
                STARTER_TASKS,
                RungState.WARN,
                live,
                "$live starter ${taskWord(live)} ready to claim, but nothing a hire on " +
                    "${uncovered.joinToString(" or ")} could pick up.$reviewNote",
            )
            live > 0 -> rung(
                STARTER_TASKS,
                RungState.OK,
                live,
                "$live starter ${taskWord(live)} ready to claim.$reviewNote",
            )
            else -> rung(
                STARTER_TASKS,
                RungState.WARN,
                0,
                "No starter tasks yet. They are mined from the corpus when a crawl finishes.",
            )
        }
    }

    /**
     * Whether the roles on this project say which track their people onboard on.
     *
     * Track resolution is total by design — a role that declares nothing lands on the default rather
     * than blocking (see [TrackService]). That is the right behaviour and the wrong silence: a
     * delivery lead whose role declares no track is onboarded, measured and *spoken to* in
     * engineering words, and nothing anywhere says so. It is the invisible-hire failure this
     * initiative exists to kill, one level up — the hire is not broken, they are quietly mis-served.
     *
     * The warning fires only when **some** role here declares a track and others do not. That is the
     * case where the information to act on exists: somebody adopted tracks and these roles were left
     * behind. A project where *no* role declares one has not adopted tracks at all, and nothing
     * distinguishes it from a team that really is all engineers — so it reads OK and simply states
     * the wording everyone gets, which is what a PM with a designer needs to notice. Warning there
     * instead would fire on every project that predates tracks, and a rung that cannot be cleared
     * without changing nothing real is how a ladder stops being read.
     */
    private fun tracksRung(projectId: UUID): SetupRungResponse {
        val members = projectMembershipApi.getProjectMembers(projectId)
        val declared = members.count { it.onboardingTrackKey != null }
        val undeclared = members.size - declared
        val defaultLabel = trackService.default().label
        return when {
            members.isEmpty() -> rung(TRACKS, RungState.OK, 0, "No roles to set a track on yet.")
            undeclared == 0 -> rung(
                TRACKS,
                RungState.OK,
                declared,
                "Every role here says which track its people onboard on.",
            )
            declared > 0 -> rung(
                TRACKS,
                RungState.WARN,
                declared,
                "$undeclared of ${members.size} ${personWord(members.size)} here have no track on " +
                    "their role, so they onboard in $defaultLabel wording while the rest do not. " +
                    "Set one on each role if that is not the work they do.",
            )
            else -> rung(
                TRACKS,
                RungState.OK,
                0,
                "No role here sets a track, so everyone onboards in $defaultLabel wording.",
            )
        }
    }

    /**
     * Tracks that some role points at but no claimable task serves.
     *
     * Read from the tracks roles actually use rather than every track that exists: warning about a
     * track nobody is on is noise a PM cannot act on.
     */
    private fun uncoveredTracks(approvedTasks: List<StarterWorkTaskProposal>): List<String> {
        if (approvedTasks.any { it.onboardingTrackKey == null }) {
            return emptyList()
        }
        val covered = approvedTasks.mapNotNull { it.onboardingTrackKey }.toSet()
        return trackService
            .tracksInUse()
            .filter { it.key !in covered }
            .map { it.label }
    }

    private fun rung(key: String, state: RungState, count: Int, detail: String) =
        SetupRungResponse(key = key, state = state, count = count, detail = detail)

    private fun competencyWord(n: Int) = if (n == 1) "competency" else "competencies"

    private fun taskWord(n: Int) = if (n == 1) "task" else "tasks"

    private fun personWord(n: Int) = if (n == 1) "person" else "people"

    private companion object {
        const val SKILL_MAP = "skill-map"
        const val STARTER_TASKS = "starter-tasks"
        const val TRACKS = "tracks"
    }
}
