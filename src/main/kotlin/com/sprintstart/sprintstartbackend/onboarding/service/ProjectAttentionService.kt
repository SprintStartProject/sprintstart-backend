package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.model.response.metrics.AttentionItemResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.metrics.AttentionSeverity
import com.sprintstart.sprintstartbackend.onboarding.model.response.metrics.HireTimelineResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.metrics.ProjectAttentionResponse
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Who on a project is waiting or stalling today, worst first.
 *
 * Composed from the derived metrics rather than recomputed, so "waiting on a review" means the
 * same thing here as everywhere else in the product. What this list deliberately is not: a
 * judgment of the hire. A pull request kept waiting is somebody else's move (BLOCKED), and the
 * reason says so — attention pointed at the one person who cannot fix it is attention wasted.
 *
 * Escalation to a person runs through flag-to-PM and the PM's knowledge-request inbox; there is
 * no assigned peer to route it to.
 */
@Service
class ProjectAttentionService(
    private val projectMembershipApi: ProjectMembershipApi,
    private val onboardingMetricsService: OnboardingMetricsService,
    private val clock: Clock = Clock.systemUTC(),
) {
    /** The project's attention list: blocked before drifting, longest-waiting first. */
    @Transactional(readOnly = true)
    fun getAttention(projectId: UUID): ProjectAttentionResponse {
        val now = clock.instant()
        val members = projectMembershipApi.getProjectMembers(projectId).associateBy { it.userId }

        val items = onboardingMetricsService.getProjectMetrics(projectId).hires.flatMap { hire ->
            val member = members[hire.userId] ?: return@flatMap emptyList()
            attentionFor(hire, member.displayName, member.joinedAt, now)
        }

        return ProjectAttentionResponse(
            projectId = projectId,
            memberCount = members.size,
            // Blocked before drifting, then longest-waiting first: the order a person would work
            // the list in.
            items = items.sortedWith(compareBy({ it.severity }, { -it.days })),
        )
    }

    private fun attentionFor(
        hire: HireTimelineResponse,
        hireName: String,
        joinedAt: Instant?,
        now: Instant,
    ): List<AttentionItemResponse> {
        val items = mutableListOf<AttentionItemResponse>()
        val sinceJoined = daysBetween(joinedAt ?: now, now)

        fun item(reason: String, severity: AttentionSeverity, days: Long) =
            AttentionItemResponse(
                hireId = hire.userId,
                hireName = hireName,
                reason = reason,
                severity = severity,
                days = days,
            )

        // Waiting on a person comes first: it is the barrier with the most evidence behind it, and
        // unlike everything else on this list it is somebody else's move, not the hire's.
        hire.longestOpenWaitHours?.takeIf { it >= WAITING_ATTENTION_HOURS }?.let { hours ->
            val days = hours / HOURS_PER_DAY
            items += item(
                "A pull request has been waiting $days days for a response",
                AttentionSeverity.BLOCKED,
                days = days,
            )
        }

        // A stall the metrics found that the row above does not already cover: no attributable
        // identity, or silence since joining.
        if (hire.stalled && hire.longestOpenWaitHours == null) {
            items += item(
                hire.stalledReason ?: "Stalled",
                AttentionSeverity.DRIFTING,
                days = sinceJoined,
            )
        }

        return items
    }

    private fun daysBetween(from: Instant, to: Instant): Long =
        if (to.isBefore(from)) 0 else Duration.between(from, to).toDays()

    private companion object {
        const val HOURS_PER_DAY = 24

        /** Matches the metrics' response window, so the two numbers cannot contradict each other. */
        const val WAITING_ATTENTION_HOURS = 48
    }
}
