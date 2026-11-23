package com.sprintstart.sprintstartbackend.path.service

import com.sprintstart.sprintstartbackend.path.external.HirePathActionsApi
import com.sprintstart.sprintstartbackend.path.external.model.NewConnectedStep
import com.sprintstart.sprintstartbackend.path.model.request.question.SubmitQuestionAttemptRequest
import com.sprintstart.sprintstartbackend.path.model.request.skip.CreateOnboardingSkipRequest
import com.sprintstart.sprintstartbackend.path.model.request.task.UpdateOnboardingTaskRequest
import com.sprintstart.sprintstartbackend.path.model.response.question.SubmitQuestionAttemptResponse
import com.sprintstart.sprintstartbackend.path.model.response.skip.CreateOnboardingSkipResponse
import com.sprintstart.sprintstartbackend.path.model.response.step.CreateOnboardingStepResponse
import com.sprintstart.sprintstartbackend.path.model.response.step.UpdateOnboardingStepResponse
import com.sprintstart.sprintstartbackend.path.model.response.task.UpdateOnboardingTaskResponse
import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import com.sprintstart.sprintstartbackend.shared.ids.CheckQuestionId
import com.sprintstart.sprintstartbackend.shared.ids.OnboardingTaskId
import com.sprintstart.sprintstartbackend.shared.ids.PhaseId
import com.sprintstart.sprintstartbackend.shared.ids.StepId
import org.springframework.stereotype.Service

/** [HirePathActionsApi] over the services that own each change. */
@Service
class HirePathActionsService(
    private val onboardingStepService: OnboardingStepService,
    private val onboardingStepPlacementService: OnboardingStepPlacementService,
    private val onboardingTaskService: OnboardingTaskService,
    private val onboardingSkipService: OnboardingSkipService,
    private val questionAttemptService: QuestionAttemptService,
) : HirePathActionsApi {
    override fun completeMyStep(authId: AuthId, stepId: StepId): UpdateOnboardingStepResponse =
        onboardingStepService.completeOnboardingStepForMe(authId.value, stepId.value)

    override fun placeMyStep(authId: AuthId, phaseId: PhaseId, step: NewConnectedStep): CreateOnboardingStepResponse =
        onboardingStepPlacementService.createConnectedStepForMe(
            authId = authId.value,
            phaseId = phaseId.value,
            request = step.step,
            origin = step.origin,
            waitsOn = step.waitsOn,
            unlocks = step.unlocks,
        )

    override fun updateMyTask(
        authId: AuthId,
        taskId: OnboardingTaskId,
        request: UpdateOnboardingTaskRequest,
    ): UpdateOnboardingTaskResponse = onboardingTaskService.updateOnboardingTaskForMe(
        authId.value,
        taskId.value,
        request,
    )

    override fun requestSkip(
        authId: AuthId,
        stepId: StepId,
        request: CreateOnboardingSkipRequest,
    ): CreateOnboardingSkipResponse = onboardingSkipService.createOnboardingSkipForMe(
        authId.value,
        stepId.value,
        request,
    )

    override fun answerCheck(
        authId: AuthId,
        questionId: CheckQuestionId,
        request: SubmitQuestionAttemptRequest,
    ): SubmitQuestionAttemptResponse =
        questionAttemptService.submitQuestionAttemptForMe(authId.value, questionId.value, request)
}
