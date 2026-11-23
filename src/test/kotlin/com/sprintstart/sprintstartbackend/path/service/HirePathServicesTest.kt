package com.sprintstart.sprintstartbackend.path.service

import com.sprintstart.sprintstartbackend.path.external.enums.StepOrigin
import com.sprintstart.sprintstartbackend.path.external.model.NewConnectedStep
import com.sprintstart.sprintstartbackend.path.model.request.question.SubmitQuestionAttemptRequest
import com.sprintstart.sprintstartbackend.path.model.request.skip.CreateOnboardingSkipRequest
import com.sprintstart.sprintstartbackend.path.model.request.step.CreateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.path.model.request.task.UpdateOnboardingTaskRequest
import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import com.sprintstart.sprintstartbackend.shared.ids.CheckQuestionId
import com.sprintstart.sprintstartbackend.shared.ids.OnboardingTaskId
import com.sprintstart.sprintstartbackend.shared.ids.PhaseId
import com.sprintstart.sprintstartbackend.shared.ids.StepId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** The hire-facing facades only unwrap ids and hand each call to the service that owns it. */
class HirePathServicesTest {
    private val onboardingPathService: OnboardingPathService = mockk()
    private val onboardingTaskService: OnboardingTaskService = mockk()
    private val pathStepReader: PathStepReader = mockk()
    private val questionAttemptReader: QuestionAttemptReader = mockk()
    private val onboardingStepService: OnboardingStepService = mockk()
    private val onboardingStepPlacementService: OnboardingStepPlacementService = mockk()
    private val onboardingSkipService: OnboardingSkipService = mockk()
    private val questionAttemptService: QuestionAttemptService = mockk()

    private val reads =
        HirePathService(onboardingPathService, onboardingTaskService, pathStepReader, questionAttemptReader)
    private val actions = HirePathActionsService(
        onboardingStepService,
        onboardingStepPlacementService,
        onboardingTaskService,
        onboardingSkipService,
        questionAttemptService,
    )

    private val userId = UUID.randomUUID()
    private val id = UUID.randomUUID()
    private val authId = "auth|hire"

    @Test
    fun `reads answer with what the owning service answers`() {
        every { onboardingPathService.hasPath(userId) } returns true
        every { onboardingPathService.findPathForUserId(userId) } returns mockk()
        every { onboardingPathService.findHiddenPhaseForUserId(userId, id) } returns mockk()
        every { pathStepReader.titlesFor(userId) } returns listOf("Set up your laptop")
        every { questionAttemptReader.lastAttempt(CheckQuestionId(id), UserId(userId)) } returns mockk()
        every { onboardingTaskService.getOnboardingTaskById(id) } returns mockk()
        every { onboardingTaskService.getOnboardingTasksByStepId(id) } returns listOf(mockk())
        every { onboardingTaskService.getOnboardingTaskForMe(authId, id) } returns mockk()

        assertEquals(true, reads.hasPath(UserId(userId)))
        assertSame(onboardingPathService.findPathForUserId(userId), reads.pathOf(UserId(userId)))
        assertSame(
            onboardingPathService.findHiddenPhaseForUserId(userId, id),
            reads.hiddenPhaseOf(UserId(userId), PhaseId(id)),
        )
        assertEquals(listOf("Set up your laptop"), reads.stepTitlesOf(UserId(userId)))
        assertSame(
            questionAttemptReader.lastAttempt(CheckQuestionId(id), UserId(userId)),
            reads.lastAttempt(CheckQuestionId(id), UserId(userId)),
        )
        assertSame(onboardingTaskService.getOnboardingTaskById(id), reads.task(OnboardingTaskId(id)))
        assertSame(onboardingTaskService.getOnboardingTasksByStepId(id), reads.tasksOf(StepId(id)))
        assertSame(
            onboardingTaskService.getOnboardingTaskForMe(authId, id),
            reads.myTask(AuthId(authId), OnboardingTaskId(id)),
        )
    }

    @Test
    fun `actions run as the hire through the owning service`() {
        val stepRequest: CreateOnboardingStepRequest = mockk()
        val waitsOn = setOf(UUID.randomUUID())
        val unlocks = setOf(UUID.randomUUID())
        val taskRequest: UpdateOnboardingTaskRequest = mockk()
        val skipRequest: CreateOnboardingSkipRequest = mockk()
        val answer: SubmitQuestionAttemptRequest = mockk()
        every { onboardingStepService.completeOnboardingStepForMe(authId, id) } returns mockk()
        every {
            onboardingStepPlacementService.createConnectedStepForMe(
                authId,
                id,
                stepRequest,
                StepOrigin.BUDDY,
                waitsOn,
                unlocks,
            )
        } returns mockk()
        every { onboardingTaskService.updateOnboardingTaskForMe(authId, id, taskRequest) } returns mockk()
        every { onboardingSkipService.createOnboardingSkipForMe(authId, id, skipRequest) } returns mockk()
        every { questionAttemptService.submitQuestionAttemptForMe(authId, id, answer) } returns mockk()

        assertSame(
            onboardingStepService.completeOnboardingStepForMe(authId, id),
            actions.completeMyStep(AuthId(authId), StepId(id)),
        )
        assertSame(
            onboardingStepPlacementService.createConnectedStepForMe(
                authId,
                id,
                stepRequest,
                StepOrigin.BUDDY,
                waitsOn,
                unlocks,
            ),
            actions.placeMyStep(
                AuthId(authId),
                PhaseId(id),
                NewConnectedStep(stepRequest, StepOrigin.BUDDY, waitsOn, unlocks),
            ),
        )
        assertSame(
            onboardingTaskService.updateOnboardingTaskForMe(authId, id, taskRequest),
            actions.updateMyTask(AuthId(authId), OnboardingTaskId(id), taskRequest),
        )
        assertSame(
            onboardingSkipService.createOnboardingSkipForMe(authId, id, skipRequest),
            actions.requestSkip(AuthId(authId), StepId(id), skipRequest),
        )
        assertSame(
            questionAttemptService.submitQuestionAttemptForMe(authId, id, answer),
            actions.answerCheck(AuthId(authId), CheckQuestionId(id), answer),
        )
    }
}
