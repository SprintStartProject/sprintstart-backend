package com.sprintstart.sprintstartbackend.onboarding.model.response.metrics

import java.time.Instant
import java.util.UUID

/**
 * One hire's way to their first accepted work, as a sequence of moments and the gaps between them.
 *
 * Every timestamp is nullable and every gap is nullable, because "has not happened yet" is the
 * normal state of a hire early on and is a different thing from zero. A dashboard that
 * renders an unreached milestone as `0 days` reports success where there is none.
 *
 * The fields say "contribution", never "pull request". They are composed from
 * [com.sprintstart.sprintstartbackend.onboarding.service.Contribution] — a merged pull request, a
 * colleague's attestation and a tracked issue somebody else accepted all land here — so naming
 * them after one source would quietly tell a PM their Scrum Master had opened nothing. The
 * vocabulary is `Contribution`'s: opened, first answered, accepted, returned.
 */
data class HireTimelineResponse(
    val userId: UUID,
    val displayName: String,
    /** Null when this person has declared no GitHub login, so none of their pull requests can be attributed. */
    val githubLogin: String?,
    /** Null for assignments made before joining was recorded — "clock unknown", not "joined now". */
    val joinedAt: Instant?,
    val firstTaskClaimedAt: Instant?,
    /** When they first put work up for somebody else to look at, whatever kind of work it is. */
    val firstContributionOpenedAt: Instant?,
    val firstResponseAt: Instant?,
    /** When their first piece of work was accepted through the team's normal quality bar. */
    val firstContributionAcceptedAt: Instant?,
    /** Joined → first accepted contribution. The north star, per hire. */
    val hoursToFirstAcceptedContribution: Long?,
    /** Opened → first response on their first contribution. */
    val hoursToFirstResponse: Long?,
    val acceptedContributionCount: Int,
    /** Submitted and still waiting on somebody else. */
    val openContributionCount: Int,
    /**
     * Their longest contribution currently waiting on anyone, in hours.
     *
     * Measured against now, not against a close that never came: work nobody has answered for three
     * weeks should read as three weeks, and it only stops growing when somebody replies.
     */
    val longestOpenWaitHours: Long?,
    val stalled: Boolean,
    /** What the stall is attributed to, in plain words; null when not stalled. */
    val stalledReason: String?,
    /**
     * How much of this hire's work was sent back for changes.
     *
     * The counterpart to the accepted count: shipping five things that each needed three rounds is
     * a different story from shipping five clean ones, and only one of those two numbers was
     * visible before.
     */
    val returnedContributionCount: Int,
)

/**
 * How a project's people are getting their work in.
 *
 * Medians rather than means throughout: one hire who took four months to their first accepted piece
 * of work should not be able to make the cohort look slow, and one who finished on day one should
 * not hide the rest.
 */
data class ProjectOnboardingMetricsResponse(
    val projectId: UUID,
    val memberCount: Int,
    /**
     * Members with no declared GitHub login — their pull requests cannot be attributed, so their
     * timelines are necessarily incomplete.
     *
     * Still counted on the GitHub login alone, deliberately, and this is now narrower than
     * the name suggests: a hire who declared a tracker name but no GitHub login has attributable
     * work and is counted here anyway. Widening it is a behaviour change, not a rename, so it is
     * left for whoever decides what "attributable" should mean once a project can have two
     * identities per person.
     */
    val unattributableMemberCount: Int,
    val hiresWithAcceptedContribution: Int,
    val medianHoursToFirstAcceptedContribution: Long?,
    val medianHoursToFirstResponse: Long?,
    /** The slow tail of review latency, where the barrier actually bites. */
    val p90HoursToFirstResponse: Long?,
    val stalledCount: Int,
    val waitingOnResponseCount: Int,
    val hires: List<HireTimelineResponse>,
)
