package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.model.ContributionWording
import com.sprintstart.sprintstartbackend.onboarding.model.response.metrics.HireTimelineResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.metrics.ProjectOnboardingMetricsResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.UserGoalRepository
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * How long onboarding is actually taking, derived from what the system already records.
 *
 * Derived on read, never emitted. There is no onboarding event table: every fact here
 * already exists somewhere durable, and a second log would be a version of the same truth that
 * drifts.
 *
 * Nothing here reports a percentage of anything completed. The measure is
 * time-to-first-accepted-contribution and time-to-autonomy.
 */
@Service
class OnboardingMetricsService(
    private val projectMembershipApi: ProjectMembershipApi,
    private val contributionService: ContributionService,
    private val userGoalRepository: UserGoalRepository,
    private val taskZeroService: TaskZeroService,
    private val rampService: RampService,
    private val clock: Clock = Clock.systemUTC(),
) {
    /**
     * Every member of a project with their timeline, plus the aggregates a PM acts on.
     *
     * @param projectId The project to report on.
     */
    @Transactional(readOnly = true)
    fun getProjectMetrics(projectId: UUID): ProjectOnboardingMetricsResponse {
        val members = projectMembershipApi.getProjectMembers(projectId)
        val hires = members.map { timelineFor(it, projectId) }

        val timesToMerge = hires.mapNotNull { it.hoursToFirstAcceptedContribution }
        val timesToResponse = hires.mapNotNull { it.hoursToFirstResponse }

        return ProjectOnboardingMetricsResponse(
            projectId = projectId,
            memberCount = members.size,
            unattributableMemberCount = members.count { it.githubLogin.isNullOrBlank() },
            hiresWithAcceptedContribution = hires.count { it.acceptedContributionCount > 0 },
            medianHoursToFirstAcceptedContribution = median(timesToMerge),
            medianHoursToFirstResponse = median(timesToResponse),
            p90HoursToFirstResponse = percentile(timesToResponse, P90),
            stalledCount = hires.count { it.stalled },
            waitingOnResponseCount = hires.count { it.longestOpenWaitHours != null },
            hires = hires,
        )
    }

    /**
     * One hire's timeline in one project.
     *
     * @throws NoSuchElementException never — an unknown user simply has no membership and is
     * reported as absent by the caller.
     */
    @Transactional(readOnly = true)
    fun getHireTimeline(userId: UUID, projectId: UUID): HireTimelineResponse? {
        val member = projectMembershipApi.getProjectMembers(projectId).firstOrNull { it.userId == userId }
            ?: return null
        return timelineFor(member, projectId)
    }

    private fun timelineFor(member: ProjectMember, projectId: UUID): HireTimelineResponse {
        val now = clock.instant()
        val login = member.githubLogin

        // No attributable identity means no contributions can be found — which is not the
        // same as having done nothing.
        val contributions = contributionService.forHire(member, projectId)

        val opened = contributions.mapNotNull { it.openedAt }.minOrNull()
        val accepted = contributions.mapNotNull { it.acceptedAt }.minOrNull()
        val firstContribution = contributions
            .filter { it.openedAt != null }
            .minByOrNull { it.openedAt as Instant }

        val goalClaimedAt = userGoalRepository.findByUserIdAndProjectId(member.userId, projectId)?.claimedAt

        val longestOpenWait = contributions
            // Only a contribution still in flight is "waiting on somebody": an abandoned one is
            // done, not stuck, even though it was never accepted.
            .filter { it.firstResponseAt == null && it.isInFlight && it.openedAt != null }
            .mapNotNull { it.openedAt }
            .minOrNull()
            ?.let { hoursBetween(it, now) }

        val stalledReason = stalledReason(member, contributions, goalClaimedAt, accepted, now)

        return HireTimelineResponse(
            userId = member.userId,
            displayName = member.displayName,
            githubLogin = login,
            joinedAt = member.joinedAt,
            taskZeroAssignedAt = taskZeroService.assignedAtFor(member.userId, projectId),
            firstTaskClaimedAt = goalClaimedAt,
            firstContributionOpenedAt = opened,
            firstResponseAt = firstContribution?.firstResponseAt,
            firstContributionAcceptedAt = accepted,
            hoursToFirstAcceptedContribution = hoursBetween(member.joinedAt, accepted),
            hoursToFirstResponse = hoursBetween(firstContribution?.openedAt, firstContribution?.firstResponseAt),
            acceptedContributionCount = contributions.count { it.isAccepted },
            // In flight, not merely unaccepted: a contribution closed without acceptance is neither
            // in flight nor accepted and must not inflate the open count.
            openContributionCount = contributions.count { it.isInFlight },
            longestOpenWaitHours = longestOpenWait,
            stalled = stalledReason != null,
            stalledReason = stalledReason,
            // The end of onboarding belongs next to the other numbers about how onboarding is
            // going. Read-only here: a PM opening the dashboard must never be what grants it.
            autonomyReachedAt = rampService.autonomyReachedAtFor(member.userId, projectId),
            // R7's own measure, on our data: whether a suggested task was claimed, and whether it
            // came back sent-for-rework. Both derived, so history is covered without a backfill.
            returnedContributionCount = contributions.count { it.returnedCount > 0 },
        )
    }

    /**
     * Why this hire is stuck, in the words a PM would use — or null if they are not.
     *
     * The reasons are ordered by what a PM should do about them, not by severity.
     */
    private fun stalledReason(
        member: ProjectMember,
        contributions: List<Contribution>,
        goalClaimedAt: Instant?,
        firstAcceptedAt: Instant?,
        now: Instant,
    ): String? {
        // A missing GitHub login is not a reason to skip. Attested evidence is attributed by
        // identity rather than by a git handle, so such a hire can still be judged -- and for them
        // an empty contribution list is a real answer, not a blind spot. Skipping on the login
        // alone would make them *invisible*: never stalled, so nobody is ever told.

        val waitingSince = contributions
            // An abandoned contribution is not waiting on anyone, so it cannot be the stall.
            .filter { it.firstResponseAt == null && it.isInFlight }
            .mapNotNull { it.openedAt }
            .minOrNull()
        if (waitingSince != null && hoursBetween(waitingSince, now)!! >= RESPONSE_SLA_HOURS) {
            val days = hoursBetween(waitingSince, now)!! / HOURS_PER_DAY
            return "A ${ContributionWording.NOUN} has been waiting $days days for a first response"
        }

        // Something accepted already: onboarding is moving, whatever else is open.
        if (firstAcceptedAt != null) {
            return null
        }

        val since = listOfNotNull(goalClaimedAt, member.joinedAt).maxOrNull() ?: return null
        val quietHours = hoursBetween(since, now) ?: return null
        if (contributions.isEmpty() && quietHours >= NO_ACTIVITY_STALL_HOURS) {
            val days = quietHours / HOURS_PER_DAY
            return "No ${ContributionWording.NOUN} started in $days days since joining"
        }

        return null
    }

    private fun hoursBetween(from: Instant?, to: Instant?): Long? {
        if (from == null || to == null || to.isBefore(from)) return null
        return Duration.between(from, to).toHours()
    }

    private fun median(values: List<Long>): Long? = percentile(values, MEDIAN)

    /**
     * Nearest-rank percentile. Deliberately not interpolated: these are small cohorts, and an
     * interpolated value invents a hire whose timeline nobody had.
     */
    private fun percentile(values: List<Long>, fraction: Double): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val rank = Math.ceil(fraction * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    private companion object {
        const val MEDIAN = 0.5
        const val P90 = 0.9
        const val HOURS_PER_DAY = 24

        /** How long a contribution may wait for any response before it is somebody's problem. */
        const val RESPONSE_SLA_HOURS = 48

        /** How long a hire may be quiet after joining or claiming a task before it is flagged. */
        const val NO_ACTIVITY_STALL_HOURS = 14 * HOURS_PER_DAY
    }
}
