package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingResource
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toCreateResponse
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toGetAllResponse
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toUpdateResponse
import com.sprintstart.sprintstartbackend.onboarding.model.request.resource.CreateOnboardingResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.resource.UpdateOnboardingResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.resource.CreateOnboardingResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.resource.GetOnboardingResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.resource.GetOnboardingResourcesResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.resource.UpdateOnboardingResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingResourceRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingStepRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Manages onboarding resources attached to steps.
 *
 * Resources are unordered reference items, so create and update operations only mutate
 * the targeted resource and do not trigger sibling reordering.
 */
@Service
class OnboardingResourceService(
    private val onboardingResourceRepository: OnboardingResourceRepository,
    private val onboardingStepRepository: OnboardingStepRepository,
    private val userApi: UserApi,
) {
//  ========================== Methods for users ==========================

    /**
     * Returns all resources for one step in the authenticated user's onboarding path.
     *
     * @param authId External authentication identifier.
     * @param stepId Identifier of the parent step.
     * @return Resources attached to the step.
     * @throws ResponseStatusException When the user does not exist.
     */
    @Transactional(readOnly = true)
    fun getOnboardingResourcesForMe(authId: String, stepId: UUID): List<GetOnboardingResourcesResponse> {
        val userId = userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }

        return onboardingResourceRepository
            .findByStepIdAndStepPhasePathUserId(stepId, userId)
            .map { it.toGetAllResponse() }
    }

    /**
     * Creates a resource for one step in the authenticated user's onboarding path.
     *
     * Resources are not position-ordered and are attached directly to the target step.
     *
     * @param authId External authentication identifier.
     * @param stepId Identifier of the parent step.
     * @param request Resource creation payload.
     * @return The created resource.
     * @throws ResponseStatusException When the user or step does not exist.
     */
    @Transactional
    fun createOnboardingResourceForMe(
        authId: String,
        stepId: UUID,
        request: CreateOnboardingResourceRequest,
    ): CreateOnboardingResourceResponse {
        val userId = userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }

        val step = onboardingStepRepository
            .findByIdAndPhasePathUserId(stepId, userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Step not found") }

        val resource = OnboardingResource(
            step = step,
            title = request.title,
            description = request.description,
            url = request.url,
        )

        return onboardingResourceRepository.save(resource).toCreateResponse()
    }

    /**
     * Returns one resource from the authenticated user's onboarding path.
     *
     * @param authId External authentication identifier.
     * @param resourceId Identifier of the resource to load.
     * @return The requested resource.
     * @throws ResponseStatusException When the user or resource does not exist.
     */
    @Transactional(readOnly = true)
    fun getOnboardingResourceForMe(authId: String, resourceId: UUID): GetOnboardingResourceResponse {
        val userId = userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }

        return onboardingResourceRepository
            .findByIdAndStepPhasePathUserId(resourceId, userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not Found") }
            .toGetResponse()
    }

    /**
     * Updates a resource in the authenticated user's onboarding path.
     *
     * @param authId External authentication identifier.
     * @param resourceId Identifier of the resource to update.
     * @param request Resource update payload.
     * @return The updated resource.
     * @throws ResponseStatusException When the user or resource does not exist.
     */
    @Transactional
    fun updateOnboardingResourceForMe(
        authId: String,
        resourceId: UUID,
        request: UpdateOnboardingResourceRequest,
    ): UpdateOnboardingResourceResponse {
        val userId = userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }

        val resource = onboardingResourceRepository
            .findByIdAndStepPhasePathUserId(resourceId, userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found") }

        resource.title = request.title
        resource.description = request.description
        resource.url = request.url

        return onboardingResourceRepository.save(resource).toUpdateResponse()
    }

    /**
     * Deletes a resource from the authenticated user's onboarding path.
     *
     * @param authId External authentication identifier.
     * @param resourceId Identifier of the resource to delete.
     * @throws ResponseStatusException When the user or resource does not exist.
     */
    @Transactional
    fun deleteOnboardingResourceForMe(
        authId: String,
        resourceId: UUID,
    ) {
        val userId = userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }

        val resource = onboardingResourceRepository
            .findByIdAndStepPhasePathUserId(resourceId, userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found") }

        onboardingResourceRepository.delete(resource)
    }

//  ========================== Methods for admins ==========================

    /**
     * Creates a new onboarding resource (reference link) attached to the specified step.
     *
     * Unlike phases, steps, and tasks, resources are not position-ordered.
     *
     * @param stepId The ID of the step to attach the resource to.
     * @param request The resource creation request containing title, description, and URL.
     * @return The created resource response.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no step exists with [stepId].
     */
    @Transactional
    fun createOnboardingResourceForStepId(
        stepId: UUID,
        request: CreateOnboardingResourceRequest,
    ): CreateOnboardingResourceResponse {
        val step = onboardingStepRepository
            .findById(stepId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No step found with id: $stepId") }

        val resource = OnboardingResource(
            step = step,
            title = request.title,
            description = request.description,
            url = request.url,
        )

        return onboardingResourceRepository.save(resource).toCreateResponse()
    }

    /**
     * Retrieves all onboarding resources attached to the specified step.
     *
     * @param stepId The ID of the step whose resources should be retrieved.
     * @return A list of resources for the given step.
     */
    @Transactional(readOnly = true)
    fun getOnboardingResourcesByStepId(stepId: UUID): List<GetOnboardingResourcesResponse> {
        return onboardingResourceRepository
            .findAllByStepId(stepId)
            .map { it.toGetAllResponse() }
    }

    /**
     * Retrieves a single onboarding resource by its ID.
     *
     * @param resourceId The ID of the onboarding resource.
     * @return The onboarding resource response.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no resource exists with [resourceId].
     */
    @Transactional(readOnly = true)
    fun getOnboardingResourceById(resourceId: UUID): GetOnboardingResourceResponse {
        return onboardingResourceRepository
            .findById(resourceId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No resource found with id $resourceId") }
            .toGetResponse()
    }

    /**
     * Updates an existing onboarding resource's title, description, and URL.
     *
     * @param resourceId The ID of the resource to update.
     * @param request The update request.
     * @return The updated resource response.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no resource exists with [resourceId].
     */
    @Transactional
    fun updateOnboardingResourceById(
        resourceId: UUID,
        request: UpdateOnboardingResourceRequest,
    ): UpdateOnboardingResourceResponse {
        val resource = onboardingResourceRepository
            .findById(resourceId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No resource found with id: $resourceId") }

        resource.title = request.title
        resource.description = request.description
        resource.url = request.url

        return onboardingResourceRepository.save(resource).toUpdateResponse()
    }

    /**
     * Deletes an onboarding resource by its ID.
     *
     * @param resourceId The ID of the resource to delete.
     * @throws ResponseStatusException with [HttpStatus.NOT_FOUND] if no resource exists with [resourceId].
     */
    @Transactional
    fun deleteOnboardingResourceById(resourceId: UUID) {
        val resource = onboardingResourceRepository
            .findById(resourceId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No resource found with id: $resourceId") }

        onboardingResourceRepository.delete(resource)
    }
}
