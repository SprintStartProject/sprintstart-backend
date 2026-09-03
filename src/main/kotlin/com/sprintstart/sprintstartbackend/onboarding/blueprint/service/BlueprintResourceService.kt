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

@Service
class BlueprintResourceService(
    private val blueprintAccessService: BlueprintAccessService,
    private val blueprintResourceRepository: BlueprintResourceRepository,
) {
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

    @Transactional(readOnly = true)
    fun getBlueprintResourceById(
        scope: BlueprintScope,
        resourceId: UUID,
    ): GetBlueprintResourceResponse {
        return blueprintAccessService
            .getAuthorizedResource(scope, resourceId)
            .toGetResponse()
    }

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
