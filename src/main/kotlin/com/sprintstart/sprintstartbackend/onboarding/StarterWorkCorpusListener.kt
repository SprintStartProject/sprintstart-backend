package com.sprintstart.sprintstartbackend.onboarding

import com.sprintstart.sprintstartbackend.connectors.github.external.events.issues.GithubIssuesFetchCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.jira.external.events.issues.JiraResourceFetchingCompleteEvent
import com.sprintstart.sprintstartbackend.onboarding.service.StarterWorkPoolReconciler
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduledExecutor
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
 */
@Component
class StarterWorkCorpusListener(
    private val scheduledExecutor: ScheduledExecutor,
    private val starterWorkPoolReconciler: StarterWorkPoolReconciler,
) {
    /**
     * Handed to [ScheduledExecutor] rather than run inline, deliberately.
     *
     * A fetch has just finished and the connector is free to move on; making it wait on another
     * module's bookkeeping would put onboarding's work inside ingestion's critical path, and a
     * failure here would then surface as an ingestion failure. The executor also swallows and logs,
     * which is the right shape for work nobody is waiting on.
     *
     * `@Async` would have been the obvious annotation and is deliberately not used: this
     * application does not enable async proxying, so it would be an annotation that reads as
     * off-thread while running inline.
     */
    @EventListener
    fun onGithubIssuesFetched(event: GithubIssuesFetchCompletedEvent) {
        reconcileInBackground("GitHub issues fetched for ${event.repositoryOwner}/${event.repositoryName}")
    }

    @EventListener
    fun onJiraResourcesFetched(event: JiraResourceFetchingCompleteEvent) {
        reconcileInBackground("Jira resources fetched (transaction ${event.transactionId})")
    }

    private fun reconcileInBackground(trigger: String) {
        scheduledExecutor.launch("Reconciling the starter-work pool — $trigger") {
            starterWorkPoolReconciler.reconcile()
        }
    }
}
