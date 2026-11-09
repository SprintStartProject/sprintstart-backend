package com.sprintstart.sprintstartbackend.path.service

import com.sprintstart.sprintstartbackend.path.external.HirePathApi
import com.sprintstart.sprintstartbackend.path.model.response.path.GetOnboardingPathForUserResponse
import com.sprintstart.sprintstartbackend.path.model.response.phase.GetOnboardingPhaseForUserResponse
import com.sprintstart.sprintstartbackend.path.model.response.question.QuestionAttemptResponse
import com.sprintstart.sprintstartbackend.path.model.response.task.GetOnboardingTaskResponse
import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import com.sprintstart.sprintstartbackend.shared.ids.CheckQuestionId
import com.sprintstart.sprintstartbackend.shared.ids.OnboardingTaskId
import com.sprintstart.sprintstartbackend.shared.ids.PhaseId
import com.sprintstart.sprintstartbackend.shared.ids.StepId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import org.springframework.stereotype.Service

/** [HirePathApi] over the services that own each answer. */
@Service
class HirePathService(
    private val onboardingPathService: OnboardingPathService,
    private val onboardingTaskService: OnboardingTaskService,
    private val pathStepReader: PathStepReader,
    private val questionAttemptReader: QuestionAttemptReader,
) : HirePathApi {
    override fun hasPath(userId: UserId): Boolean = onboardingPathService.hasPath(userId.value)

    override fun pathOf(userId: UserId): GetOnboardingPathForUserResponse? =
        onboardingPathService.findPathForUserId(userId.value)

    override fun hiddenPhaseOf(userId: UserId, phaseId: PhaseId): GetOnboardingPhaseForUserResponse? =
        onboardingPathService.findHiddenPhaseForUserId(userId.value, phaseId.value)

    override fun stepTitlesOf(userId: UserId): List<String> = pathStepReader.titlesFor(userId.value)

    override fun lastAttempt(questionId: CheckQuestionId, userId: UserId): QuestionAttemptResponse? =
        questionAttemptReader.lastAttempt(questionId, userId)

    override fun task(taskId: OnboardingTaskId): GetOnboardingTaskResponse =
        onboardingTaskService.getOnboardingTaskById(taskId.value)

    override fun tasksOf(stepId: StepId): List<GetOnboardingTaskResponse> =
        onboardingTaskService.getOnboardingTasksByStepId(stepId.value)

    override fun myTask(authId: AuthId, taskId: OnboardingTaskId): GetOnboardingTaskResponse =
        onboardingTaskService.getOnboardingTaskForMe(authId.value, taskId.value)
}
