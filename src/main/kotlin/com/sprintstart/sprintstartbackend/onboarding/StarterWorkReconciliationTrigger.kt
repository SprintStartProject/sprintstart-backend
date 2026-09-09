package com.sprintstart.sprintstartbackend.onboarding

import com.sprintstart.sprintstartbackend.onboarding.service.StarterWorkPoolReconciler
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduledExecutor
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The one way a starter-work reconciliation pass is asked for, so that asking twice does not run
 * it twice.
 *
 * Every caller wants the same thing — the pool brought in line with the corpus *as it is now* —
 * and a pass derives all of it from current state. Two passes over the same corpus therefore do
 * identical work, and the second is pure waste. That matters because the triggers are not rare:
 * `GithubIssuesFetchCompletedEvent` is published per repository and its Jira counterpart per
 * instance, so connecting a source with twenty repositories announces twenty completions in
 * parallel — twenty full passes over the whole pool, on top of the hourly one.
 *
 * So a request while a pass is running does not queue another pass; it records that the corpus
 * moved again, and the pass in flight is followed by exactly one more. However many requests
 * arrive during a pass, at most one follow-up runs, and no request is dropped without a pass
 * having run after it.
 */
@Component
class StarterWorkReconciliationTrigger(
    private val scheduledExecutor: ScheduledExecutor,
    private val starterWorkPoolReconciler: StarterWorkPoolReconciler,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** Set by every request, cleared by the pass that serves it. */
    private val requested = AtomicBoolean(false)

    /** Held for as long as a pass is in flight, so a second request coalesces instead of starting one. */
    private val running = AtomicBoolean(false)

    /**
     * Asks for the pool to be reconciled, running a pass unless one is already under way.
     *
     * Returns immediately either way. The work is handed to [ScheduledExecutor] rather than run
     * inline because the caller is usually a connector that has just finished fetching and is free
     * to move on: making it wait on another module's bookkeeping would put onboarding's work inside
     * ingestion's critical path, and a failure here would surface as an ingestion failure.
     *
     * @param trigger What asked, for the log line — the reason a pass ran is worth being able to
     *   read back when the pool turns out to have been wrong.
     */
    fun request(trigger: String) {
        // Set before the claim below, never after: a request that lands while the running pass is
        // finishing must be visible to it, and ordering it the other way is the one interleaving
        // that could drop a request entirely.
        requested.set(true)
        startIfIdle(trigger)
    }

    private fun startIfIdle(trigger: String) {
        if (!running.compareAndSet(false, true)) {
            logger.debug("Starter-work reconciliation already running; coalescing request: {}", trigger)
            return
        }
        scheduledExecutor.launch("Reconciling the starter-work pool — $trigger") {
            try {
                // Drains rather than runs once: requests that arrive mid-pass are served by one
                // further iteration, however many of them there were.
                while (requested.compareAndSet(true, false)) {
                    starterWorkPoolReconciler.reconcile()
                }
            } finally {
                running.set(false)
            }
            // A request that landed between the loop's last check and the release above holds no
            // pass of its own, so it is picked up here. If the pass threw, this line is skipped and
            // the flag stays set: the next event, or the hourly pass, serves it.
            if (requested.get()) startIfIdle("$trigger (follow-up)")
        }
    }
}
