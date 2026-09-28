package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPathRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPhaseRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingStepRepository
import com.sprintstart.sprintstartbackend.shared.crypto.CryptoConfiguration
import io.mockk.mockk
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@DataJpaTest
@ActiveProfiles("test")
@Import(CryptoConfiguration::class)
class OnboardingPathPersistenceTest {
    @Autowired
    private lateinit var onboardingPathRepository: OnboardingPathRepository

    @Autowired
    private lateinit var onboardingPhaseRepository: OnboardingPhaseRepository

    @Autowired
    private lateinit var onboardingStepRepository: OnboardingStepRepository

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    @Test
    fun `a deleted step is really gone, and what waited on it waits on its blockers`() {
        // The delete used to report success while the phase's cascading collection kept the step.
        val userId = UUID.randomUUID()
        val path = OnboardingPath(userId = userId)
        val phase = OnboardingPhase(path = path, position = 0, title = "Setup", description = "")
        val first = step(phase, position = 0, title = "A")
        val middle = step(phase, position = 1, title = "X").also { it.blockedBy += first }
        val last = step(phase, position = 2, title = "B").also { it.blockedBy += middle }
        phase.steps += listOf(first, middle, last)
        path.phases += phase
        entityManager.persist(path)
        entityManager.flush()
        entityManager.clear()

        val service = OnboardingStepService(
            onboardingPhaseRepository = onboardingPhaseRepository,
            onboardingStepRepository = onboardingStepRepository,
            onboardingCompletionService = mockk(relaxed = true),
            userApi = mockk(relaxed = true),
        )
        service.deleteOnboardingStepById(middle.id)
        entityManager.flush()
        entityManager.clear()

        assertFalse(onboardingStepRepository.existsById(middle.id))
        val remaining = onboardingStepRepository.findById(last.id).orElseThrow()
        assertEquals(setOf(first.id), remaining.blockedBy.map { it.id }.toSet())
    }

    @Test
    fun `replaces a path containing dependencies between newly created nodes`() {
        val userId = UUID.randomUUID()
        entityManager.persist(OnboardingPath(userId = userId))
        entityManager.flush()
        entityManager.clear()

        val replacement = pathWithNodeDependency(userId)

        onboardingPathRepository.deleteByUserId(userId)
        onboardingPathRepository.flush()
        entityManager.persist(replacement)
        entityManager.flush()
        entityManager.clear()

        val persisted = onboardingPathRepository.findByUserId(userId).orElseThrow()
        val persistedSteps = persisted.phases.single().steps

        assertEquals(2, persistedSteps.size)
        assertEquals(
            setOf(persistedSteps.first().id),
            persistedSteps
                .last()
                .blockedBy
                .map { it.id }
                .toSet(),
        )
    }

    private fun pathWithNodeDependency(userId: UUID): OnboardingPath {
        val path = OnboardingPath(userId = userId)
        val phase = OnboardingPhase(
            path = path,
            position = 0,
            title = "Setup",
            description = "Prepare the project",
        )
        val firstStep = step(phase, position = 0, title = "Clone repository")
        val secondStep = step(phase, position = 1, title = "Run application")
        secondStep.blockedBy += firstStep
        phase.steps += listOf(firstStep, secondStep)
        path.phases += phase
        return path
    }

    private fun step(
        phase: OnboardingPhase,
        position: Int,
        title: String,
    ): OnboardingStep = OnboardingStep(
        phase = phase,
        position = position,
        title = title,
        description = "",
        type = StepType.TASK,
        aiAssisted = false,
        estimatedMinutes = 5,
        expectedOutcome = "Done",
        status = StepStatus.WAITING,
    )
}
