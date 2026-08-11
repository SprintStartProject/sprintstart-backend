package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.resource.CreateBlueprintResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.resource.UpdateBlueprintResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.resource.CreateBlueprintResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.resource.GetBlueprintResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.resource.UpdateBlueprintResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintResourceService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("api/v1/onboarding/blueprint")
class BlueprintResourceController(
    private val blueprintResourceService: BlueprintResourceService,
) {
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    @GetMapping("/step/{stepId}/resources")
    fun getBlueprintResourcesForStep(
        @PathVariable stepId: UUID,
    ): List<GetBlueprintResourceResponse> {
        return blueprintResourceService.getBlueprintResourcesForStep(stepId)
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    @GetMapping("/resources/{resourceId}")
    fun getBlueprintResource(@PathVariable resourceId: UUID): GetBlueprintResourceResponse {
        return blueprintResourceService.getBlueprintResourceById(resourceId)
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    @PostMapping("/step/{stepId}/resources")
    fun createBlueprintResource(
        @PathVariable stepId: UUID,
        @RequestBody request: CreateBlueprintResourceRequest,
    ): CreateBlueprintResourceResponse {
        return blueprintResourceService.createBlueprintResourceForStep(stepId, request)
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    @PutMapping("/resources/{resourceId}")
    fun updateBlueprintResourceById(
        @PathVariable resourceId: UUID,
        @RequestBody request: UpdateBlueprintResourceRequest,
    ): UpdateBlueprintResourceResponse {
        return blueprintResourceService.updateBlueprintResourceById(resourceId, request)
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    @DeleteMapping("/resources/{resourceId}")
    fun deleteBlueprintResourceById(
        @PathVariable resourceId: UUID,
    ) {
        return blueprintResourceService.deleteBlueprintResourceById(resourceId)
    }
}
