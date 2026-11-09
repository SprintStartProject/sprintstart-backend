package com.sprintstart.sprintstartbackend.path.service

import com.sprintstart.sprintstartbackend.path.external.model.PathElementKind
import com.sprintstart.sprintstartbackend.shared.ids.FeedbackId
import com.sprintstart.sprintstartbackend.shared.ids.OnboardingTaskId
import com.sprintstart.sprintstartbackend.shared.ids.PhaseId
import com.sprintstart.sprintstartbackend.shared.ids.ResourceId
import com.sprintstart.sprintstartbackend.shared.ids.SkipId
import com.sprintstart.sprintstartbackend.shared.ids.StepId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertSame

/** Team mode's path facades only unwrap ids and hand each call to the service that owns it. */
class PathAuthoringServicesTest {
    private val pathService: OnboardingPathService = mockk()
    private val pathElements: PathElements = mockk()
    private val skipService: OnboardingSkipService = mockk()
    private val feedbackService: OnboardingFeedbackService = mockk()
    private val phaseService: OnboardingPhaseService = mockk()
    private val questionService: QuestionAttemptService = mockk()
    private val stepService: OnboardingStepService = mockk()
    private val resourceService: OnboardingResourceService = mockk()
    private val taskService: OnboardingTaskService = mockk()

    private val review = PathReviewService(pathService, pathElements, skipService, feedbackService)
    private val phases = PhaseAuthoringService(phaseService, questionService)
    private val steps = StepAuthoringService(stepService, resourceService)
    private val tasks = TaskAuthoringService(taskService)

    private val id = UUID.randomUUID()

    @Test
    fun `review answers with what the owning service answers`() {
        every { pathService.getOnboardingPathByUserId(id) } returns mockk()
        every { pathElements.pathOf(id) } returns mockk()
        every { pathElements.find(PathElementKind.STEP, id) } returns mockk()
        every { pathService.deleteOnboardingPathByUserId(id) } just runs
        every { skipService.getAllSkipsByUserId(id) } returns listOf(mockk())
        every { skipService.getSkipById(id) } returns mockk()
        every { skipService.acceptSkipById(id, any()) } returns mockk()
        every { skipService.denySkipById(id, any()) } returns mockk()
        every { skipService.deleteSkipById(id) } just runs
        every { feedbackService.getAllFeedbackByUserId(id) } returns listOf(mockk())
        every { feedbackService.markFeedbackAsRead(id) } returns mockk()

        assertSame(pathService.getOnboardingPathByUserId(id), review.pathOf(UserId(id)))
        assertSame(pathElements.pathOf(id), review.summaryOf(UserId(id)))
        assertSame(pathElements.find(PathElementKind.STEP, id), review.element(PathElementKind.STEP, id))
        review.resetPath(UserId(id))
        assertSame(skipService.getAllSkipsByUserId(id), review.skipsOf(UserId(id)))
        assertSame(skipService.getSkipById(id), review.skip(SkipId(id)))
        assertSame(skipService.acceptSkipById(id, mockk()), review.acceptSkip(SkipId(id), mockk()))
        assertSame(skipService.denySkipById(id, mockk()), review.denySkip(SkipId(id), mockk()))
        review.deleteSkip(SkipId(id))
        assertSame(feedbackService.getAllFeedbackByUserId(id), review.feedbackOf(UserId(id)))
        assertSame(feedbackService.markFeedbackAsRead(id), review.markFeedbackRead(FeedbackId(id)))

        verify { pathService.deleteOnboardingPathByUserId(id) }
        verify { skipService.deleteSkipById(id) }
    }

    @Test
    fun `phases and their checks go to the phase and check services`() {
        every { phaseService.createOnboardingPhaseForUserId(id, any()) } returns mockk()
        every { phaseService.getOnboardingPhaseById(id) } returns mockk()
        every { phaseService.getOnboardingPhasesForUser(id) } returns listOf(mockk())
        every { phaseService.updateOnboardingPhaseById(id, any()) } returns mockk()
        every { phaseService.deleteOnboardingPhaseById(id) } just runs
        every { questionService.getPhaseQuestions(id) } returns mockk()
        every { questionService.replacePhaseQuestions(id, any()) } returns mockk()

        assertSame(phaseService.createOnboardingPhaseForUserId(id, mockk()), phases.createPhase(UserId(id), mockk()))
        assertSame(phaseService.getOnboardingPhaseById(id), phases.phase(PhaseId(id)))
        assertSame(phaseService.getOnboardingPhasesForUser(id), phases.phasesOf(UserId(id)))
        assertSame(phaseService.updateOnboardingPhaseById(id, mockk()), phases.updatePhase(PhaseId(id), mockk()))
        phases.deletePhase(PhaseId(id))
        assertSame(questionService.getPhaseQuestions(id), phases.checksOf(PhaseId(id)))
        assertSame(questionService.replacePhaseQuestions(id, mockk()), phases.replaceChecks(PhaseId(id), mockk()))

        verify { phaseService.deleteOnboardingPhaseById(id) }
    }

    @Test
    fun `steps and resources go to the step and resource services`() {
        every { stepService.createOnboardingStepForPhaseId(id, any()) } returns mockk()
        every { stepService.getOnboardingStepById(id) } returns mockk()
        every { stepService.getOnboardingStepsByPhaseId(id) } returns listOf(mockk())
        every { stepService.updateOnboardingStepById(id, any()) } returns mockk()
        every { stepService.deleteOnboardingStepById(id) } just runs
        every { resourceService.createOnboardingResourceForStepId(id, any()) } returns mockk()
        every { resourceService.getOnboardingResourceById(id) } returns mockk()
        every { resourceService.updateOnboardingResourceById(id, any()) } returns mockk()
        every { resourceService.deleteOnboardingResourceById(id) } just runs

        assertSame(stepService.createOnboardingStepForPhaseId(id, mockk()), steps.createStep(PhaseId(id), mockk()))
        assertSame(stepService.getOnboardingStepById(id), steps.step(StepId(id)))
        assertSame(stepService.getOnboardingStepsByPhaseId(id), steps.stepsOf(PhaseId(id)))
        assertSame(stepService.updateOnboardingStepById(id, mockk()), steps.updateStep(StepId(id), mockk()))
        steps.deleteStep(StepId(id))
        assertSame(
            resourceService.createOnboardingResourceForStepId(id, mockk()),
            steps.createResource(StepId(id), mockk()),
        )
        assertSame(resourceService.getOnboardingResourceById(id), steps.resource(ResourceId(id)))
        assertSame(
            resourceService.updateOnboardingResourceById(id, mockk()),
            steps.updateResource(ResourceId(id), mockk()),
        )
        steps.deleteResource(ResourceId(id))

        verify { stepService.deleteOnboardingStepById(id) }
        verify { resourceService.deleteOnboardingResourceById(id) }
    }

    @Test
    fun `tasks go to the task service`() {
        every { taskService.createOnboardingTaskForStepId(id, any()) } returns mockk()
        every { taskService.getOnboardingTaskById(id) } returns mockk()
        every { taskService.getOnboardingTasksByStepId(id) } returns listOf(mockk())
        every { taskService.updateOnboardingTaskById(id, any()) } returns mockk()
        every { taskService.deleteOnboardingTaskById(id) } just runs

        assertSame(taskService.createOnboardingTaskForStepId(id, mockk()), tasks.createTask(StepId(id), mockk()))
        assertSame(taskService.getOnboardingTaskById(id), tasks.task(OnboardingTaskId(id)))
        assertSame(taskService.getOnboardingTasksByStepId(id), tasks.tasksOf(StepId(id)))
        assertSame(taskService.updateOnboardingTaskById(id, mockk()), tasks.updateTask(OnboardingTaskId(id), mockk()))
        tasks.deleteTask(OnboardingTaskId(id))

        verify { taskService.deleteOnboardingTaskById(id) }
    }
}
