package com.sprintstart.sprintstartbackend.path.service

import com.sprintstart.sprintstartbackend.path.external.PhaseAuthoringApi
import com.sprintstart.sprintstartbackend.path.external.StepAuthoringApi
import com.sprintstart.sprintstartbackend.path.external.TaskAuthoringApi
import com.sprintstart.sprintstartbackend.path.model.request.phase.CreateOnboardingPhaseRequest
import com.sprintstart.sprintstartbackend.path.model.request.phase.UpdateOnboardingPhaseRequest
import com.sprintstart.sprintstartbackend.path.model.request.question.UpdatePhaseQuestionsRequest
import com.sprintstart.sprintstartbackend.path.model.request.resource.CreateOnboardingResourceRequest
import com.sprintstart.sprintstartbackend.path.model.request.resource.UpdateOnboardingResourceRequest
import com.sprintstart.sprintstartbackend.path.model.request.step.CreateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.path.model.request.step.UpdateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.path.model.request.task.CreateOnboardingTaskRequest
import com.sprintstart.sprintstartbackend.path.model.request.task.UpdateOnboardingTaskRequest
import com.sprintstart.sprintstartbackend.path.model.response.phase.CreateOnboardingPhaseResponse
import com.sprintstart.sprintstartbackend.path.model.response.phase.GetOnboardingPhaseResponse
import com.sprintstart.sprintstartbackend.path.model.response.phase.GetOnboardingPhasesResponse
import com.sprintstart.sprintstartbackend.path.model.response.phase.UpdateOnboardingPhaseResponse
import com.sprintstart.sprintstartbackend.path.model.response.question.GetPhaseQuestionsResponse
import com.sprintstart.sprintstartbackend.path.model.response.resource.CreateOnboardingResourceResponse
import com.sprintstart.sprintstartbackend.path.model.response.resource.GetOnboardingResourceResponse
import com.sprintstart.sprintstartbackend.path.model.response.resource.UpdateOnboardingResourceResponse
import com.sprintstart.sprintstartbackend.path.model.response.step.CreateOnboardingStepResponse
import com.sprintstart.sprintstartbackend.path.model.response.step.GetOnboardingStepResponse
import com.sprintstart.sprintstartbackend.path.model.response.step.UpdateOnboardingStepResponse
import com.sprintstart.sprintstartbackend.path.model.response.task.CreateOnboardingTaskResponse
import com.sprintstart.sprintstartbackend.path.model.response.task.GetOnboardingTaskResponse
import com.sprintstart.sprintstartbackend.path.model.response.task.UpdateOnboardingTaskResponse
import com.sprintstart.sprintstartbackend.shared.ids.OnboardingTaskId
import com.sprintstart.sprintstartbackend.shared.ids.PhaseId
import com.sprintstart.sprintstartbackend.shared.ids.ResourceId
import com.sprintstart.sprintstartbackend.shared.ids.StepId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import org.springframework.stereotype.Service

/** [PhaseAuthoringApi] over the phase service and the knowledge checks. */
@Service
class PhaseAuthoringService(
    private val onboardingPhaseService: OnboardingPhaseService,
    private val questionAttemptService: QuestionAttemptService,
) : PhaseAuthoringApi {
    override fun createPhase(userId: UserId, request: CreateOnboardingPhaseRequest): CreateOnboardingPhaseResponse =
        onboardingPhaseService.createOnboardingPhaseForUserId(userId.value, request)

    override fun phase(phaseId: PhaseId): GetOnboardingPhaseResponse =
        onboardingPhaseService.getOnboardingPhaseById(phaseId.value)

    override fun phasesOf(userId: UserId): List<GetOnboardingPhasesResponse> =
        onboardingPhaseService.getOnboardingPhasesForUser(userId.value)

    override fun updatePhase(phaseId: PhaseId, request: UpdateOnboardingPhaseRequest): UpdateOnboardingPhaseResponse =
        onboardingPhaseService.updateOnboardingPhaseById(phaseId.value, request)

    override fun deletePhase(phaseId: PhaseId) = onboardingPhaseService.deleteOnboardingPhaseById(phaseId.value)

    override fun checksOf(phaseId: PhaseId): GetPhaseQuestionsResponse =
        questionAttemptService.getPhaseQuestions(phaseId.value)

    override fun replaceChecks(phaseId: PhaseId, request: UpdatePhaseQuestionsRequest): GetPhaseQuestionsResponse =
        questionAttemptService.replacePhaseQuestions(phaseId.value, request)
}

/** [StepAuthoringApi] over the step and resource services. */
@Service
class StepAuthoringService(
    private val onboardingStepService: OnboardingStepService,
    private val onboardingResourceService: OnboardingResourceService,
) : StepAuthoringApi {
    override fun createStep(phaseId: PhaseId, request: CreateOnboardingStepRequest): CreateOnboardingStepResponse =
        onboardingStepService.createOnboardingStepForPhaseId(phaseId.value, request)

    override fun step(stepId: StepId): GetOnboardingStepResponse = onboardingStepService.getOnboardingStepById(
        stepId.value,
    )

    override fun stepsOf(phaseId: PhaseId): List<GetOnboardingStepResponse> =
        onboardingStepService.getOnboardingStepsByPhaseId(phaseId.value)

    override fun updateStep(stepId: StepId, request: UpdateOnboardingStepRequest): UpdateOnboardingStepResponse =
        onboardingStepService.updateOnboardingStepById(stepId.value, request)

    override fun deleteStep(stepId: StepId) = onboardingStepService.deleteOnboardingStepById(stepId.value)

    override fun createResource(
        stepId: StepId,
        request: CreateOnboardingResourceRequest,
    ): CreateOnboardingResourceResponse = onboardingResourceService.createOnboardingResourceForStepId(
        stepId.value,
        request,
    )

    override fun resource(resourceId: ResourceId): GetOnboardingResourceResponse =
        onboardingResourceService.getOnboardingResourceById(resourceId.value)

    override fun updateResource(
        resourceId: ResourceId,
        request: UpdateOnboardingResourceRequest,
    ): UpdateOnboardingResourceResponse = onboardingResourceService.updateOnboardingResourceById(
        resourceId.value,
        request,
    )

    override fun deleteResource(resourceId: ResourceId) =
        onboardingResourceService.deleteOnboardingResourceById(resourceId.value)
}

/** [TaskAuthoringApi] over the task service. */
@Service
class TaskAuthoringService(
    private val onboardingTaskService: OnboardingTaskService,
) : TaskAuthoringApi {
    override fun createTask(stepId: StepId, request: CreateOnboardingTaskRequest): CreateOnboardingTaskResponse =
        onboardingTaskService.createOnboardingTaskForStepId(stepId.value, request)

    override fun task(taskId: OnboardingTaskId): GetOnboardingTaskResponse =
        onboardingTaskService.getOnboardingTaskById(taskId.value)

    override fun tasksOf(stepId: StepId): List<GetOnboardingTaskResponse> =
        onboardingTaskService.getOnboardingTasksByStepId(stepId.value)

    override fun updateTask(
        taskId: OnboardingTaskId,
        request: UpdateOnboardingTaskRequest,
    ): UpdateOnboardingTaskResponse = onboardingTaskService.updateOnboardingTaskById(taskId.value, request)

    override fun deleteTask(taskId: OnboardingTaskId) = onboardingTaskService.deleteOnboardingTaskById(taskId.value)
}
