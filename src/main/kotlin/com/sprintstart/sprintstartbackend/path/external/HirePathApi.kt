package com.sprintstart.sprintstartbackend.path.external

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

/**
 * A hire's own path, as their assistant reads it: in the shapes the path page serves, so the assistant and
 * the page never describe the same path differently.
 *
 * Read-only. What the assistant may change goes through [HirePathActionsApi].
 */
interface HirePathApi {
    /** Whether [userId] has a path at all. */
    fun hasPath(userId: UserId): Boolean

    /** [userId]'s path as the path page shows it, or null when they have none. */
    fun pathOf(userId: UserId): GetOnboardingPathForUserResponse?

    /** A phase of [userId]'s path that the path page hides, such as one whose generation failed; null if none. */
    fun hiddenPhaseOf(userId: UserId, phaseId: PhaseId): GetOnboardingPhaseForUserResponse?

    /** The titles of [userId]'s visible steps, for a refusal that lets the model correct itself. */
    fun stepTitlesOf(userId: UserId): List<String>

    /** [userId]'s most recent answer to [questionId], or null when they have not answered it. */
    fun lastAttempt(questionId: CheckQuestionId, userId: UserId): QuestionAttemptResponse?

    /**
     * The task [taskId], whoever's path it is on. The caller checks that it is on the hire's path.
     *
     * @throws org.springframework.web.server.ResponseStatusException 404 when there is no such task.
     */
    fun task(taskId: OnboardingTaskId): GetOnboardingTaskResponse

    /** The tasks of step [stepId]. */
    fun tasksOf(stepId: StepId): List<GetOnboardingTaskResponse>

    /**
     * The task [taskId] on the caller's own path.
     *
     * @throws org.springframework.web.server.ResponseStatusException 404 when it is not on their path.
     */
    fun myTask(authId: AuthId, taskId: OnboardingTaskId): GetOnboardingTaskResponse
}
