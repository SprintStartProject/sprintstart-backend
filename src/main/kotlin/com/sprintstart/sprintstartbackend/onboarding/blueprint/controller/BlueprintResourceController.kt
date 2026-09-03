package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.resource.CreateBlueprintResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.resource.DeleteBlueprintResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.resource.UpdateBlueprintResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.resource.CreateBlueprintResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.resource.GetBlueprintResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.resource.UpdateBlueprintResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintResourceService
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/onboarding/blueprints")
class BlueprintResourceAdminController(
    private val blueprintResourceService: BlueprintResourceService,
) {
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/step/{stepId}/resources")
    fun getBlueprintResourcesForStep(
        @PathVariable stepId: UUID,
    ): List<GetBlueprintResourceResponse> {
        return blueprintResourceService.getBlueprintResourcesForStep(BlueprintScope.Global, stepId)
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/resources/{resourceId}")
    fun getBlueprintResource(
        @PathVariable resourceId: UUID,
    ): GetBlueprintResourceResponse {
        return blueprintResourceService.getBlueprintResourceById(BlueprintScope.Global, resourceId)
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/step/{stepId}/resources")
    fun createBlueprintResource(
        @PathVariable stepId: UUID,
        @RequestBody request: CreateBlueprintResourceRequest,
    ): CreateBlueprintResourceResponse {
        return blueprintResourceService.createBlueprintResourceForStep(BlueprintScope.Global, stepId, request)
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/resources/{resourceId}")
    fun updateBlueprintResourceById(
        @PathVariable resourceId: UUID,
        @RequestBody request: UpdateBlueprintResourceRequest,
    ): UpdateBlueprintResourceResponse {
        return blueprintResourceService.updateBlueprintResourceById(BlueprintScope.Global, resourceId, request)
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/resources/{resourceId}")
    fun deleteBlueprintResourceById(
        @PathVariable resourceId: UUID,
        @RequestBody request: DeleteBlueprintResourceRequest,
    ) {
        return blueprintResourceService.deleteBlueprintResourceById(BlueprintScope.Global, resourceId, request)
    }
}

@RestController
@RequestMapping("/api/v1/projects/{projectId}/onboarding/blueprints")
class BlueprintResourceController(
    private val blueprintResourceService: BlueprintResourceService,
) {
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    @GetMapping("/step/{stepId}/resources")
    fun getBlueprintResourcesForStep(
        @PathVariable projectId: UUID,
        @PathVariable stepId: UUID,
    ): List<GetBlueprintResourceResponse> {
        return blueprintResourceService.getBlueprintResourcesForStep(BlueprintScope.Project(projectId), stepId)
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    @GetMapping("/resources/{resourceId}")
    fun getBlueprintResource(
        @PathVariable projectId: UUID,
        @PathVariable resourceId: UUID,
    ): GetBlueprintResourceResponse {
        return blueprintResourceService.getBlueprintResourceById(
            BlueprintScope.Project(projectId),
            resourceId,
        )
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    @PostMapping("/step/{stepId}/resources")
    fun createBlueprintResource(
        @PathVariable projectId: UUID,
        @PathVariable stepId: UUID,
        @RequestBody request: CreateBlueprintResourceRequest,
    ): CreateBlueprintResourceResponse {
        return blueprintResourceService.createBlueprintResourceForStep(
            BlueprintScope.Project(projectId),
            stepId,
            request,
        )
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    @PutMapping("/resources/{resourceId}")
    fun updateBlueprintResourceById(
        @PathVariable projectId: UUID,
        @PathVariable resourceId: UUID,
        @RequestBody request: UpdateBlueprintResourceRequest,
    ): UpdateBlueprintResourceResponse {
        return blueprintResourceService.updateBlueprintResourceById(
            BlueprintScope.Project(projectId),
            resourceId,
            request,
        )
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    @DeleteMapping("/resources/{resourceId}")
    fun deleteBlueprintResourceById(
        @PathVariable projectId: UUID,
        @PathVariable resourceId: UUID,
        @RequestBody request: DeleteBlueprintResourceRequest,
    ) {
        return blueprintResourceService.deleteBlueprintResourceById(
            BlueprintScope.Project(projectId),
            resourceId,
            request,
        )
    }
}
