package com.sprintstart.sprintstartbackend.onboarding.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingResource
import com.sprintstart.sprintstartbackend.onboarding.model.response.resource.CreateOnboardingResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.resource.GetOnboardingResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.resource.GetOnboardingResourcesResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.resource.UpdateOnboardingResourceResponse

fun OnboardingResource.toGetAllResponse(): GetOnboardingResourcesResponse {
    return GetOnboardingResourcesResponse(
        id = this.id,
        stepId = this.step.id,
        title = this.title,
        description = this.description,
        url = this.url,
    )
}

fun OnboardingResource.toGetResponse(): GetOnboardingResourceResponse {
    return GetOnboardingResourceResponse(
        id = this.id,
        stepId = this.step.id,
        title = this.title,
        description = this.description,
        url = this.url,
    )
}

fun OnboardingResource.toCreateResponse(): CreateOnboardingResourceResponse {
    return CreateOnboardingResourceResponse(
        id = this.id,
        stepId = this.step.id,
        title = this.title,
        description = this.description,
        url = this.url,
    )
}

fun OnboardingResource.toUpdateResponse(): UpdateOnboardingResourceResponse {
    return UpdateOnboardingResourceResponse(
        id = this.id,
        stepId = this.step.id,
        title = this.title,
        description = this.description,
        url = this.url,
    )
}
