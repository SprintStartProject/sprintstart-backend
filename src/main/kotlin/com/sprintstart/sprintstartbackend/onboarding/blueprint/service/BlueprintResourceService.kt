package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintResource
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toCreateResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toUpdateResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.resource.CreateBlueprintResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.resource.UpdateBlueprintResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.resource.CreateBlueprintResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.resource.GetBlueprintResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.resource.UpdateBlueprintResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintResourceRepository
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintStepRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class BlueprintResourceService(
    private val blueprintResourceRepository: BlueprintResourceRepository,
    private val blueprintStepRepository: BlueprintStepRepository,
) {
    @Transactional(readOnly = true)
    fun getBlueprintResourcesForStep(stepId: UUID): List<GetBlueprintResourceResponse> {
        return blueprintResourceRepository
            .findAllByBlueprintStepId(stepId)
            .map { it.toGetResponse() }
    }

    @Transactional(readOnly = true)
    fun getBlueprintResourceById(resourceId: UUID): GetBlueprintResourceResponse {
        return blueprintResourceRepository
            .findById(resourceId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found with id: $resourceId") }
            .toGetResponse()
    }

    @Transactional
    fun createBlueprintResourceForStep(
        stepId: UUID,
        request: CreateBlueprintResourceRequest,
    ): CreateBlueprintResourceResponse {
        val blueprintStep = blueprintStepRepository
            .findById(stepId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Step not found with id: $stepId") }

        if (blueprintStep.blueprintPhase.blueprintPath.status != BlueprintStatus.DRAFT) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Blueprint can only be modified while in DRAFT status",
            )
        }

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
        resourceId: UUID,
        request: UpdateBlueprintResourceRequest,
    ): UpdateBlueprintResourceResponse {
        val blueprintResource = blueprintResourceRepository
            .findById(resourceId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found with id: $resourceId") }

        if (blueprintResource.blueprintStep.blueprintPhase.blueprintPath.status != BlueprintStatus.DRAFT) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Blueprint can only be modified while in DRAFT status",
            )
        }

        if (blueprintResource.revision != request.revision) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "The blueprint resource has been modified by another request. Please reload and try again.",
            )
        }

        blueprintResource.title = request.title
        blueprintResource.description = request.description
        blueprintResource.url = request.url

        return blueprintResourceRepository.save(blueprintResource).toUpdateResponse()
    }

    @Transactional
    fun deleteBlueprintResourceById(resourceId: UUID) {
        val blueprintResource = blueprintResourceRepository
            .findById(resourceId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found with id: $resourceId") }

        if (blueprintResource.blueprintStep.blueprintPhase.blueprintPath.status != BlueprintStatus.DRAFT) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Blueprint can only be modified while in DRAFT status",
            )
        }

        blueprintResourceRepository.delete(blueprintResource)
    }
}
