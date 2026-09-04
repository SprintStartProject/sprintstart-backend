package com.sprintstart.sprintstartbackend.onboarding

import com.sprintstart.sprintstartbackend.onboarding.service.StarterWorkPoolReconciler
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduledExecutor
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Runs the onboarding module's periodic jobs.
 *
 * Only one so far: bringing the starter-work pool back in line with its trackers.
 */
@Component
class StarterWorkScheduledExecutor(
    private val scheduledExecutor: ScheduledExecutor,
    private val starterWorkPoolReconciler: StarterWorkPoolReconciler,
) {
    /**
     * Reconciles the starter-work pool against the ingested corpus.
     *
     * This is the safety net, not the mechanism. The pool is normally corrected the moment a
     * tracker fetch completes (`StarterWorkCorpusListener`), and a row a hire is about to commit to
     * is checked individually at that moment (`UserGoalService.claimForMe`). What is left for a
     * clock is everything those two miss: a corpus changed by something that published no event, a
     * listener that failed, an instance that was down when the fetch happened.
     *
     * Hourly is therefore about right — often enough that a gap closes on its own within a working
     * session, rare enough to be nearly free, given a pass reads only already-ingested rows.
     *
     * The first run is delayed so it does not compete with startup.
     */
    @Scheduled(initialDelay = STARTUP_DELAY_MS, fixedRate = RECONCILE_INTERVAL_MS)
    fun reconcileStarterWorkPool() {
        scheduledExecutor.launch("Reconciling the starter-work pool against its sources") {
            starterWorkPoolReconciler.reconcile()
        }
    }

    private companion object {
        const val STARTUP_DELAY_MS = 120_000L
        const val RECONCILE_INTERVAL_MS = 3_600_000L
    }
}
