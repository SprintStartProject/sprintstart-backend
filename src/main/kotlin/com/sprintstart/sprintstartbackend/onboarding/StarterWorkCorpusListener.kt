package com.sprintstart.sprintstartbackend.onboarding

import com.sprintstart.sprintstartbackend.connectors.github.external.events.issues.GithubIssuesFetchCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.jira.external.events.issues.JiraResourceFetchingCompleteEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Reconciles the starter-work pool when the corpus it is drawn from has just changed.
 *
 * The scheduled pass is a clock, and a clock is the wrong instrument for this: the pool only goes
 * out of date when a tracker says something new, and that moment is already announced. Listening
 * for it means the pool is corrected within seconds of an issue closing rather than within the
 * hour, and that the periodic pass becomes a safety net rather than the mechanism.
 *
 * Both fetches are listened to because the pool is tracker-agnostic — a mined task is a GitHub
 * issue or a Jira one, and either can close.
 *
 * These events are published per repository and per Jira instance, so they arrive in bursts —
 * connecting a source announces one completion per repository, in parallel. Requests therefore go
 * through [StarterWorkReconciliationTrigger], which collapses a burst into one pass and one
 * follow-up rather than running the whole pool once per event.
 */
@Component
class StarterWorkCorpusListener(
    private val starterWorkReconciliationTrigger: StarterWorkReconciliationTrigger,
) {
    /**
     * Requested rather than run inline, deliberately.
     *
     * A fetch has just finished and the connector is free to move on; making it wait on another
     * module's bookkeeping would put onboarding's work inside ingestion's critical path, and a
     * failure here would then surface as an ingestion failure.
     *
     * `@Async` would have been the obvious annotation and is deliberately not used: this
     * application does not enable async proxying, so it would be an annotation that reads as
     * off-thread while running inline.
     *
     * Note what the GitHub event does and does not mean: it is published even in check-only mode
     * (`performUpdate = false`), which ingests nothing. Reconciling then costs one corpus read and
     * changes nothing, which is the cheaper of the two ways to be wrong about whether the corpus
     * moved.
     */
    @EventListener
    fun onGithubIssuesFetched(event: GithubIssuesFetchCompletedEvent) {
        starterWorkReconciliationTrigger.request(
            "GitHub issues fetched for ${event.repositoryOwner}/${event.repositoryName}",
        )
    }

    @EventListener
    fun onJiraResourcesFetched(event: JiraResourceFetchingCompleteEvent) {
        starterWorkReconciliationTrigger.request("Jira resources fetched (transaction ${event.transactionId})")
    }
}
