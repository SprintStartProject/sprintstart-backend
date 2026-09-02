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
     * Hourly rather than nightly, and deliberately not tied to an ingestion run. A pass reads only
     * already-ingested rows, so it is cheap enough to run often, and running on its own clock means
     * a project whose ingestion is failing still has its pool corrected from whatever was captured
     * last — rather than freezing exactly when it is most likely to be wrong.
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
