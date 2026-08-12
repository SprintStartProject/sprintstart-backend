package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.ingestion.external.AuthoredPullRequest
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * The hire's own still-open pull requests, in the order that matters.
 *
 * Extracted because there are now two things that show them — the buddy's `get_my_open_pull_requests`
 * tool, in prose, and the board's open-work card, as a list — and the design's rule for the board is
 * that a card and the tool of the same name must never be able to disagree. Two callers doing their
 * own filtering and sorting is exactly how they would.
 *
 * The judgements live here, not in the callers: what "open" means, which one leads, and when a wait
 * is still running.
 */
@Component
class OpenPullRequestReader(
    private val artifactIngestionApi: ArtifactIngestionApi,
    private val clock: Clock = Clock.systemUTC(),
) {
    /**
     * [login]'s open pull requests on [projectId], longest-waiting first.
     *
     * "Open" means genuinely open rather than merely unmerged, so a pull request closed without
     * merging is excluded — and the list therefore matches the open count the metrics report.
     *
     * @param projectId The project to look in.
     * @param login The hire's declared GitHub login; blank or null yields nothing, because without
     * it no pull request can be attributed to them.
     * @return Their open pull requests, the one most likely to be the stall first.
     */
    fun openFor(projectId: UUID, login: String?): List<AuthoredPullRequest> {
        if (login.isNullOrBlank()) return emptyList()
        val now = clock.instant()
        return artifactIngestionApi
            .getAuthoredPullRequests(projectId, login)
            .filter { it.isOpen }
            // Longest-waiting first: the one most likely to be the stall leads the list. A pull
            // request with no opened-at sorts last rather than first — an unknown wait is not a
            // long one.
            .sortedByDescending { waitHours(it.openedAt, now) ?: -1 }
    }

    /**
     * How long [pullRequest] has been waiting on a first response, or null when it is not waiting.
     *
     * Only an unanswered pull request is waiting: once somebody has responded, the clock the hire
     * cares about has stopped, and reporting the elapsed time as a wait would be a complaint about
     * a review that already happened.
     */
    fun waitingHours(pullRequest: AuthoredPullRequest): Long? {
        if (pullRequest.firstResponseAt != null) return null
        return waitHours(pullRequest.openedAt, clock.instant())
    }

    private fun waitHours(from: Instant?, to: Instant): Long? {
        if (from == null || to.isBefore(from)) return null
        return Duration.between(from, to).toHours()
    }
}
