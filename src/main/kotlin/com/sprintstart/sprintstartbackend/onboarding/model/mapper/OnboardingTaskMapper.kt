package com.sprintstart.sprintstartbackend.onboarding.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingTask
import com.sprintstart.sprintstartbackend.onboarding.model.response.task.CreateOnboardingTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.task.GetOnboardingTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.task.GetOnboardingTasksResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.task.UpdateOnboardingTaskResponse

fun OnboardingTask.toGetAllResponse(): GetOnboardingTasksResponse {
    return GetOnboardingTasksResponse(
        id = this.id,
        stepId = this.step.id,
        position = this.position,
        title = this.title,
        description = this.description,
        finished = this.finished,
    )
}

fun OnboardingTask.toGetResponse(): GetOnboardingTaskResponse {
    return GetOnboardingTaskResponse(
        id = this.id,
        stepId = this.step.id,
        position = this.position,
        title = this.title,
        description = this.description,
        finished = this.finished,
    )
}

fun OnboardingTask.toCreateResponse(): CreateOnboardingTaskResponse {
    return CreateOnboardingTaskResponse(
        id = this.id,
        stepId = this.step.id,
        position = this.position,
        title = this.title,
        description = this.description,
        finished = this.finished,
    )
}

fun OnboardingTask.toUpdateResponse(): UpdateOnboardingTaskResponse {
    return UpdateOnboardingTaskResponse(
        id = this.id,
        stepId = this.step.id,
        position = this.position,
        title = this.title,
        description = this.description,
        finished = this.finished,
    )
}
