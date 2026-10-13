package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingFeedback
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingResource
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingSkip
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingTask
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingFeedbackRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPathRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPhaseRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingResourceRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingSkipRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingStepRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingTaskRepository
import com.sprintstart.sprintstartbackend.shared.crypto.CryptoConfiguration
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

/**
 * Whose path an element is on decides whether a manager may touch it, so the walk from each kind
 * up to its owner meets a real database: a mocked repository would only prove the mapping.
 */
@ActiveProfiles("test")
@DataJpaTest
// The same slice configuration as the other repository tests, so it reuses their cached context: one
// more distinct context is one more Spring container held in a test JVM that is already near its heap.
@Import(CryptoConfiguration::class)
class PathElementsTest {
    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var pathRepository: OnboardingPathRepository

    @Autowired
    private lateinit var phaseRepository: OnboardingPhaseRepository

    @Autowired
    private lateinit var stepRepository: OnboardingStepRepository

    @Autowired
    private lateinit var taskRepository: OnboardingTaskRepository

    @Autowired
    private lateinit var resourceRepository: OnboardingResourceRepository

    @Autowired
    private lateinit var skipRepository: OnboardingSkipRepository

    @Autowired
    private lateinit var feedbackRepository: OnboardingFeedbackRepository

    private val pathElements: PathElements by lazy {
        PathElements(
            pathRepository,
            phaseRepository,
            stepRepository,
            taskRepository,
            resourceRepository,
            skipRepository,
            feedbackRepository,
        )
    }

    private val owner = UUID.randomUUID()

    private class Built(
        val path: OnboardingPath,
        val phase: OnboardingPhase,
        val step: OnboardingStep,
        val task: OnboardingTask,
        val resource: OnboardingResource,
        val skip: OnboardingSkip,
        val feedback: OnboardingFeedback,
    )

    /** One phase with one step holding a task, a resource, a skip request and feedback. */
    private fun pathFor(userId: UUID, status: StepStatus = StepStatus.WAITING): Built {
        val path = OnboardingPath(userId = userId)
        val phase = OnboardingPhase(path = path, position = 0, title = "Setup", description = "d")
        val step = OnboardingStep(
            phase = phase,
            position = 0,
            title = "Install",
            description = "d",
            type = StepType.TASK,
            estimatedMinutes = 10,
            expectedOutcome = "it runs",
            status = status,
        )
        val task = OnboardingTask(step = step, position = 0, title = "Clone", description = "d")
        val resource = OnboardingResource(step = step, title = "Docs", description = "d", url = "https://docs")
        val skip = OnboardingSkip(step = step, reason = "already know it")
        val feedback = OnboardingFeedback(userId = userId, step = step, message = "too long")
        step.tasks += task
        step.resources += resource
        step.skips += skip
        step.feedback += feedback
        phase.steps += step
        path.phases += phase
        entityManager.persist(path)
        entityManager.flush()
        entityManager.clear()
        return Built(path, phase, step, task, resource, skip, feedback)
    }

    @Test
    fun `every kind is walked up to the person whose path it is on`() {
        val built = pathFor(owner)

        val found = mapOf(
            PathElementKind.PHASE to built.phase.id,
            PathElementKind.STEP to built.step.id,
            PathElementKind.TASK to built.task.id,
            PathElementKind.RESOURCE to built.resource.id,
            PathElementKind.SKIP to built.skip.id,
            PathElementKind.FEEDBACK to built.feedback.id,
        ).mapValues { (kind, id) -> pathElements.find(kind, id) }

        found.forEach { (kind, element) ->
            assertThat(element).describedAs("$kind").isNotNull
            assertThat(element!!.ownerId).describedAs("$kind").isEqualTo(owner)
        }
    }

    @Test
    fun `an element on somebody else's path is theirs, not the first path's`() {
        val other = UUID.randomUUID()
        val mine = pathFor(owner)
        val theirs = pathFor(other)

        assertThat(pathElements.find(PathElementKind.TASK, mine.task.id)!!.ownerId).isEqualTo(owner)
        assertThat(pathElements.find(PathElementKind.TASK, theirs.task.id)!!.ownerId).isEqualTo(other)
        assertThat(pathElements.find(PathElementKind.SKIP, theirs.skip.id)!!.ownerId).isEqualTo(other)
    }

    @Test
    fun `an id of one kind is not found as another kind`() {
        val built = pathFor(owner)

        assertThat(pathElements.find(PathElementKind.STEP, built.task.id)).isNull()
        assertThat(pathElements.find(PathElementKind.TASK, built.phase.id)).isNull()
        assertThat(pathElements.find(PathElementKind.FEEDBACK, UUID.randomUUID())).isNull()
    }

    @Test
    fun `a phase says what deleting it would take with it`() {
        val built = pathFor(owner, StepStatus.FINISHED)

        val phase = pathElements.find(PathElementKind.PHASE, built.phase.id)!!

        assertThat(phase.contains).isEqualTo(
            "1 step, 1 task, 1 resource, 1 skip request and 1 piece of feedback",
        )
        assertThat(phase.children).isEqualTo(1)
        assertThat(phase.finishedSteps).isEqualTo(1)
    }

    @Test
    fun `a step carries its own status and counts its tasks as its children`() {
        val built = pathFor(owner, StepStatus.IN_PROGRESS)

        val step = pathElements.find(PathElementKind.STEP, built.step.id)!!

        assertThat(step.stepStatus).isEqualTo(StepStatus.IN_PROGRESS)
        assertThat(step.children).isEqualTo(1)
        assertThat(step.contains).doesNotContain("step")
    }

    @Test
    fun `feedback on no step is described as being about the path as a whole`() {
        pathFor(owner)
        val general = OnboardingFeedback(userId = owner, step = null, message = "overall fine")
        entityManager.persist(general)
        entityManager.flush()
        entityManager.clear()

        val found = pathElements.find(PathElementKind.FEEDBACK, general.id)!!

        assertThat(found.title).isEqualTo("the path as a whole")
        assertThat(found.stepStatus).isNull()
    }

    @Test
    fun `a path summary counts finished and skipped steps as got through`() {
        val built = pathFor(owner, StepStatus.SKIPPED)

        val summary = pathElements.pathOf(built.path.userId)!!

        assertThat(summary).isEqualTo(PathSummary(phases = 1, steps = 1, finishedSteps = 1, checks = 0))
    }

    @Test
    fun `somebody with no path has no summary`() {
        assertThat(pathElements.pathOf(UUID.randomUUID())).isNull()
    }
}
