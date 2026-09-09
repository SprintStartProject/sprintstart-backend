package com.sprintstart.sprintstartbackend.onboarding

import com.sprintstart.sprintstartbackend.onboarding.service.StarterWorkPoolReconciler
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduledExecutor
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * That a burst of fetch-completed events costs one pass rather than one pass per event.
 *
 * The dispatcher is unconfined, so a launched pass runs to completion inside the `request` call
 * that started it. Overlap is therefore expressed the way it actually happens in production —
 * requests arriving *while a pass is running* — by having the pass itself ask for more.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StarterWorkReconciliationTriggerTest {
    private val reconciler = mockk<StarterWorkPoolReconciler>()
    private val scheduledExecutor = ScheduledExecutor(TestScope(UnconfinedTestDispatcher()))
    private val trigger = StarterWorkReconciliationTrigger(scheduledExecutor, reconciler)

    private val outcome = StarterWorkPoolReconciler.Outcome(0, 0, 0, 0)

    /** Counts passes, and runs [duringFirstPass] inside the first one to simulate overlap. */
    private fun countingReconciler(duringFirstPass: () -> Unit = {}): () -> Int {
        var passes = 0
        every { reconciler.reconcile() } answers {
            passes++
            if (passes == 1) duringFirstPass()
            outcome
        }
        return { passes }
    }

    @Test
    fun `a request with nothing in flight runs a pass`() {
        val passes = countingReconciler()

        trigger.request("a fetch completed")

        assertEquals(1, passes())
    }

    /**
     * The event storm this exists for: connecting a source publishes one completion per repository,
     * in parallel, and each one used to launch a full pass over the whole pool.
     */
    @Test
    fun `events arriving during a pass collapse into a single follow-up`() {
        val passes = countingReconciler {
            repeat(20) { trigger.request("repository $it fetched") }
        }

        trigger.request("the first repository fetched")

        assertEquals(2, passes(), "twenty events during a pass are worth one more pass, not twenty")
    }

    /**
     * Coalescing must not lose work: the corpus moved after the running pass had already read it,
     * so a further pass is owed and has to actually happen.
     */
    @Test
    fun `a request made during a pass is still served by one`() {
        val passes = countingReconciler {
            trigger.request("a fetch that landed mid-pass")
        }

        trigger.request("the first fetch")

        assertEquals(2, passes())
    }

    @Test
    fun `requests that do not overlap each run their own pass`() {
        val passes = countingReconciler()

        trigger.request("a fetch completed")
        trigger.request("another fetch completed")

        assertEquals(2, passes(), "the guard is against overlap, not against reconciling twice")
    }

    /**
     * A failed pass releases the guard. Otherwise one exception would wedge the trigger for the
     * lifetime of the process and the pool would stop being reconciled entirely — a worse failure
     * than the double-running this class prevents.
     */
    @Test
    fun `a pass that throws does not wedge the trigger`() {
        every { reconciler.reconcile() } throws IllegalStateException("the corpus read failed")

        trigger.request("a fetch completed")

        var passes = 0
        every { reconciler.reconcile() } answers {
            passes++
            outcome
        }
        trigger.request("a later fetch")

        assertEquals(1, passes)
    }
}
