package com.sprintstart.sprintstartbackend.path.external

import com.sprintstart.sprintstartbackend.path.model.request.resource.CreateOnboardingResourceRequest
import com.sprintstart.sprintstartbackend.path.model.request.resource.UpdateOnboardingResourceRequest
import com.sprintstart.sprintstartbackend.path.model.request.step.CreateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.path.model.request.step.UpdateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.path.model.response.resource.CreateOnboardingResourceResponse
import com.sprintstart.sprintstartbackend.path.model.response.resource.GetOnboardingResourceResponse
import com.sprintstart.sprintstartbackend.path.model.response.resource.UpdateOnboardingResourceResponse
import com.sprintstart.sprintstartbackend.path.model.response.step.CreateOnboardingStepResponse
import com.sprintstart.sprintstartbackend.path.model.response.step.GetOnboardingStepResponse
import com.sprintstart.sprintstartbackend.path.model.response.step.UpdateOnboardingStepResponse
import com.sprintstart.sprintstartbackend.shared.ids.PhaseId
import com.sprintstart.sprintstartbackend.shared.ids.ResourceId
import com.sprintstart.sprintstartbackend.shared.ids.StepId

/**
 * The steps of a team member's path and the resources on them, as their manager edits them.
 *
 * By id and without an access check, as [PathReviewApi]. A call that names something that is not there
 * throws a 404 [org.springframework.web.server.ResponseStatusException].
 */
interface StepAuthoringApi {
    /** Adds a step to phase [phaseId]. */
    fun createStep(phaseId: PhaseId, request: CreateOnboardingStepRequest): CreateOnboardingStepResponse

    /** Step [stepId]. */
    fun step(stepId: StepId): GetOnboardingStepResponse

    /** Every step of phase [phaseId]. */
    fun stepsOf(phaseId: PhaseId): List<GetOnboardingStepResponse>

    /** Replaces step [stepId]. */
    fun updateStep(stepId: StepId, request: UpdateOnboardingStepRequest): UpdateOnboardingStepResponse

    /** Deletes step [stepId] and everything on it. */
    fun deleteStep(stepId: StepId)

    /** Adds a resource to step [stepId]. */
    fun createResource(stepId: StepId, request: CreateOnboardingResourceRequest): CreateOnboardingResourceResponse

    /** Resource [resourceId]. */
    fun resource(resourceId: ResourceId): GetOnboardingResourceResponse

    /** Replaces resource [resourceId]. */
    fun updateResource(
        resourceId: ResourceId,
        request: UpdateOnboardingResourceRequest,
    ): UpdateOnboardingResourceResponse

    /** Deletes resource [resourceId]. */
    fun deleteResource(resourceId: ResourceId)
}
