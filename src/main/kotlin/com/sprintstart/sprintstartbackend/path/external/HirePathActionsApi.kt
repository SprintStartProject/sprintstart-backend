package com.sprintstart.sprintstartbackend.path.external

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

/**
 * What a hire's assistant may change on the hire's own path, always as the hire ([AuthId]) and under the
 * same rules as the path page.
 *
 * Every call throws a [org.springframework.web.server.ResponseStatusException] when the target is not on
 * the caller's path or the change is not allowed in its current state; the reason is a sentence the
 * assistant can relay.
 */
interface HirePathActionsApi {
    /** Marks step [stepId] finished. */
    fun completeMyStep(authId: AuthId, stepId: StepId): UpdateOnboardingStepResponse

    /** Adds [step] to phase [phaseId] and connects it in one transaction, so it never exists half-connected. */
    fun placeMyStep(authId: AuthId, phaseId: PhaseId, step: NewConnectedStep): CreateOnboardingStepResponse

    /** Replaces task [taskId]; the owning step's status is left alone. */
    fun updateMyTask(
        authId: AuthId,
        taskId: OnboardingTaskId,
        request: UpdateOnboardingTaskRequest,
    ): UpdateOnboardingTaskResponse

    /** Asks the hire's PM to let them skip step [stepId]. */
    fun requestSkip(authId: AuthId, stepId: StepId, request: CreateOnboardingSkipRequest): CreateOnboardingSkipResponse

    /** Submits an answer to the knowledge check [questionId] and grades it. */
    fun answerCheck(
        authId: AuthId,
        questionId: CheckQuestionId,
        request: SubmitQuestionAttemptRequest,
    ): SubmitQuestionAttemptResponse
}
