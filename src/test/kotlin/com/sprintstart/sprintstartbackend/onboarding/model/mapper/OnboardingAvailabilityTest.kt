package com.sprintstart.sprintstartbackend.onboarding.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.external.enums.QuestionStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckQuestion
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingAvailabilityTest {
    private fun path(vararg phases: OnboardingPhase): OnboardingPath {
        val p = OnboardingPath(userId = UUID.randomUUID())
        phases.forEach { p.phases += it }
        return p
    }

    private fun phase(position: Int, path: OnboardingPath): OnboardingPhase {
        val ph = OnboardingPhase(path = path, position = position, title = "P$position", description = "d")
        path.phases += ph
        return ph
    }

    private fun step(phase: OnboardingPhase, position: Int, status: StepStatus = StepStatus.WAITING): OnboardingStep {
        val s = OnboardingStep(
            phase = phase,
            position = position,
            title = "s$position",
            description = "d",
            type = com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType.TASK,
            estimatedMinutes = 1,
            expectedOutcome = "o",
            status = status,
        )
        phase.steps += s
        return s
    }

    private fun question(phase: OnboardingPhase, position: Int): PhaseCheckQuestion {
        val q = PhaseCheckQuestion(
            phase = phase,
            position = position,
            type = com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType.MULTIPLE_CHOICE,
            question = "q$position",
        )
        phase.checkQuestions += q
        return q
    }

    @Test
    fun `no blockers unlock every phase`() {
        val p = path()
        val ph1 = phase(0, p)
        val ph2 = phase(1, p)
        val locked = computePhaseLockedState(p.phases, emptySet())
        assertFalse(locked.getValue(ph1.id))
        assertFalse(locked.getValue(ph2.id))
    }

    @Test
    fun `a linear chain advances one phase at a time`() {
        val p = path()
        val ph1 = phase(0, p)
        val ph2 = phase(1, p)
        val ph3 = phase(2, p)
        ph2.blockedBy += ph1
        ph3.blockedBy += ph2

        // ph1 complete, ph2 has an open step. ph2's only blocker (ph1) is complete, so ph2 is
        // unlocked and workable; ph3 waits on ph2 being complete.
        step(ph1, 0, StepStatus.FINISHED)
        step(ph2, 0)
        val locked = computePhaseLockedState(p.phases, emptySet())
        assertFalse(locked.getValue(ph1.id))
        assertFalse(locked.getValue(ph2.id))
        assertTrue(locked.getValue(ph3.id))
    }

    @Test
    fun `a phase with no blockers stays unlocked even when it is later in position`() {
        val p = path()
        val ph1 = phase(0, p)
        val ph2 = phase(1, p)
        val ph3 = phase(2, p)
        ph3.blockedBy += ph1
        step(ph1, 0)
        step(ph2, 0)

        // ph1 and ph2 have no blockers, so neither is locked — ph1 is incomplete but open.
        val locked = computePhaseLockedState(p.phases, emptySet())
        assertFalse(locked.getValue(ph1.id))
        assertFalse(locked.getValue(ph2.id))
        assertTrue(locked.getValue(ph3.id))
    }

    @Test
    fun `a diamond unlocks independent branches separately`() {
        val p = path()
        val root = phase(0, p)
        val branchA = phase(1, p)
        val branchB = phase(2, p)
        val leaf = phase(3, p)
        branchA.blockedBy += root
        branchB.blockedBy += root
        leaf.blockedBy += branchA
        leaf.blockedBy += branchB

        step(root, 0, StepStatus.FINISHED)
        step(branchA, 0, StepStatus.FINISHED)
        step(branchB, 0)
        val locked = computePhaseLockedState(p.phases, emptySet())
        assertFalse(locked.getValue(root.id))
        assertFalse(locked.getValue(branchA.id))
        // branchB's only blocker (root) is complete, so it is unlocked despite its own open step.
        assertFalse(locked.getValue(branchB.id))
        assertTrue(locked.getValue(leaf.id))
    }

    @Test
    fun `a phase with all steps done but an unpassed question is incomplete`() {
        val p = path()
        val ph1 = phase(0, p)
        val ph2 = phase(1, p)
        ph2.blockedBy += ph1
        step(ph1, 0, StepStatus.FINISHED)
        val q = question(ph1, 0)

        val locked = computePhaseLockedState(p.phases, emptySet())
        assertTrue(locked.getValue(ph2.id))

        val lockedAfterPass = computePhaseLockedState(p.phases, setOf(q.id))
        assertFalse(lockedAfterPass.getValue(ph2.id))
    }

    @Test
    fun `a cycle is tolerated without hanging`() {
        val p = path()
        val ph1 = phase(0, p)
        val ph2 = phase(1, p)
        ph1.blockedBy += ph2
        ph2.blockedBy += ph1
        step(ph1, 0)
        step(ph2, 0)

        val locked = computePhaseLockedState(p.phases, emptySet())
        // Both are locked: each depends on the other, neither can complete first.
        assertTrue(locked.getValue(ph1.id))
        assertTrue(locked.getValue(ph2.id))
    }

    @Test
    fun `a node is locked while its phase is locked`() {
        val p = path()
        val ph1 = phase(0, p)
        val ph2 = phase(1, p)
        ph2.blockedBy += ph1
        // ph1 stays incomplete (its step is waiting), so ph2 is locked.
        step(ph1, 0, StepStatus.WAITING)
        val s2 = step(ph2, 0)
        val q = question(ph2, 0)

        val locked = computePhaseLockedState(p.phases, emptySet())
        assertTrue(locked.getValue(ph2.id))
        assertTrue(s2.isLockedIn(locked.getValue(ph2.id), emptySet()))
        assertEquals(
            QuestionStatus.LOCKED,
            q.questionStatus(locked.getValue(ph2.id), emptySet(), emptySet()),
        )
    }

    @Test
    fun `a node is locked while one of its in-phase blockers is incomplete`() {
        val p = path()
        val ph1 = phase(0, p)
        val s1 = step(ph1, 0, StepStatus.FINISHED)
        val s2 = step(ph1, 1)
        val q = question(ph1, 0)
        s2.blockedBy += s1
        s2.blockedBy += q

        val locked = computePhaseLockedState(p.phases, emptySet())
        assertFalse(s1.isLockedIn(locked.getValue(ph1.id), emptySet()))
        assertTrue(s2.isLockedIn(locked.getValue(ph1.id), emptySet()))

        val passed = setOf(q.id)
        assertFalse(s2.isLockedIn(locked.getValue(ph1.id), passed))
    }

    @Test
    fun `a question reports retry after an attempt that was never correct`() {
        val p = path()
        val ph1 = phase(0, p)
        val q = question(ph1, 0)

        val status = q.questionStatus(false, emptySet(), setOf(q.id))
        assertEquals(QuestionStatus.RETRY, status)
    }

    @Test
    fun `a question reports passed once answered correctly`() {
        val p = path()
        val ph1 = phase(0, p)
        val q = question(ph1, 0)

        val status = q.questionStatus(false, setOf(q.id), setOf(q.id))
        assertEquals(QuestionStatus.PASSED, status)
    }
}
