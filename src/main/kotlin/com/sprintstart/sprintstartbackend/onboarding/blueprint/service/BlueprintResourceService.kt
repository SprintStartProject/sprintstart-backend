package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintResource
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toCreateResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toUpdateResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.resource.CreateBlueprintResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.resource.DeleteBlueprintResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.resource.UpdateBlueprintResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.resource.CreateBlueprintResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.resource.GetBlueprintResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.resource.UpdateBlueprintResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintResourceRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Manages reference resources attached to blueprint steps.
 *
 * Reads are selected by global or project scope. Creation requires an editable parent step, while updates and deletes
 * require both scoped draft access and a matching optimistic revision. Entities are always mapped to response DTOs.
 */
@Service
class BlueprintResourceService(
    private val blueprintAccessService: BlueprintAccessService,
    private val blueprintResourceRepository: BlueprintResourceRepository,
) {
    /**
     * Returns resources for step.
     *
     * Runs the scope-specific step-resource query and maps the resources in repository order.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param stepId Identifier of the blueprint step.
     * @return The mapped result of the operation.
     */
    @Transactional(readOnly = true)
    fun getBlueprintResourcesForStep(
        scope: BlueprintScope,
        stepId: UUID,
    ): List<GetBlueprintResourceResponse> {
        return when (scope) {
            is BlueprintScope.Global -> {
                blueprintResourceRepository
                    .findAllByBlueprintStepBlueprintPhaseBlueprintPathProjectIdIsNullAndBlueprintStepId(stepId)
            }

            is BlueprintScope.Project -> {
                blueprintResourceRepository
                    .findAllByBlueprintStepBlueprintPhaseBlueprintPathProjectIdAndBlueprintStepId(
                        scope.projectId,
                        stepId,
                    )
            }
        }.map { it.toGetResponse() }
    }

    /**
     * Returns resource by id.
     *
     * Uses the access service to enforce the ownership scope before mapping the resource.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param resourceId Identifier of the blueprint resource.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 404 when the entity does not exist in the requested scope.
     */
    @Transactional(readOnly = true)
    fun getBlueprintResourceById(
        scope: BlueprintScope,
        resourceId: UUID,
    ): GetBlueprintResourceResponse {
        return blueprintAccessService
            .getAuthorizedResource(scope, resourceId)
            .toGetResponse()
    }

    /**
     * Creates resource for step.
     *
     * Requires an editable parent step, constructs the attached resource, and persists it.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param stepId Identifier of the blueprint step.
     * @param request Request data, including the expected revision when optimistic concurrency applies.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 404 for a missing step, or 409 when its path is not a draft.
     */
    @Transactional
    fun createBlueprintResourceForStep(
        scope: BlueprintScope,
        stepId: UUID,
        request: CreateBlueprintResourceRequest,
    ): CreateBlueprintResourceResponse {
        val blueprintStep = blueprintAccessService.getAuthorizedEditableStep(scope, stepId)

        val blueprintResource = BlueprintResource(
            blueprintStep = blueprintStep,
            title = request.title,
            description = request.description,
            url = request.url,
        )

        return blueprintResourceRepository.save(blueprintResource).toCreateResponse()
    }

    /**
     * Updates resource by id.
     *
     * Requires an editable resource and matching revision before replacing its title, description, and URL.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param resourceId Identifier of the blueprint resource.
     * @param request Request data, including the expected revision when optimistic concurrency applies.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 404 for a missing resource, or 409 for a stale revision or non-draft path.
     */
    @Transactional
    fun updateBlueprintResourceById(
        scope: BlueprintScope,
        resourceId: UUID,
        request: UpdateBlueprintResourceRequest,
    ): UpdateBlueprintResourceResponse {
        val blueprintResource = blueprintAccessService.getAuthorizedEditableResource(scope, resourceId)

        validateRevision(blueprintResource, request.revision)

        blueprintResource.title = request.title
        blueprintResource.description = request.description
        blueprintResource.url = request.url

        return blueprintResourceRepository.save(blueprintResource).toUpdateResponse()
    }

    /**
     * Deletes resource by id.
     *
     * Requires an editable resource and matching revision before deleting it.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param resourceId Identifier of the blueprint resource.
     * @param request Request data, including the expected revision when optimistic concurrency applies.
     * @throws ResponseStatusException With 404 for a missing entity, or 409 for a stale revision or non-draft path.
     */
    @Transactional
    fun deleteBlueprintResourceById(
        scope: BlueprintScope,
        resourceId: UUID,
        request: DeleteBlueprintResourceRequest,
    ) {
        val blueprintResource = blueprintAccessService.getAuthorizedEditableResource(scope, resourceId)

        validateRevision(blueprintResource, request.revision)

        blueprintResourceRepository.delete(blueprintResource)
    }

    // Helper methods

    private fun validateRevision(
        resource: BlueprintResource,
        revision: Long,
    ) {
        if (resource.revision != revision) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "The blueprint resource has been modified by another request. Please reload and try again.",
            )
        }
    }
}
