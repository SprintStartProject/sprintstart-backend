package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phase.CreateBlueprintPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phase.DeleteBlueprintPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phase.UpdateBlueprintPhasePositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phase.UpdateBlueprintPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.CreateBlueprintPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.GetBlueprintPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.UpdateBlueprintPhasePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.UpdateBlueprintPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintPhaseService
import jakarta.validation.Valid
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
@RequestMapping("/api/v1/onboarding/blueprints")
class BlueprintPhaseAdminController(
    private val blueprintPhaseService: BlueprintPhaseService,
) {
    @GetMapping("/path/{pathId}/phases")
    @PreAuthorize("hasRole('ADMIN')")
    fun getBlueprintPhasesForPath(
        @PathVariable pathId: UUID,
    ): List<GetBlueprintPhaseResponse> {
        return blueprintPhaseService.getBlueprintPhasesForPath(BlueprintScope.Global, pathId)
    }

    @GetMapping("/phases/{phaseId}")
    @PreAuthorize("hasRole('ADMIN')")
    fun getBlueprintPhaseById(
        @PathVariable phaseId: UUID,
    ): GetBlueprintPhaseResponse {
        return blueprintPhaseService.getBlueprintPhaseById(BlueprintScope.Global, phaseId)
    }

    @PostMapping("/path/{pathId}/phases")
    @PreAuthorize("hasRole('ADMIN')")
    fun createBlueprintPhaseForPath(
        @PathVariable pathId: UUID,
        @Valid @RequestBody request: CreateBlueprintPhaseRequest,
    ): CreateBlueprintPhaseResponse {
        return blueprintPhaseService.createBlueprintPhaseForPath(BlueprintScope.Global, pathId, request)
    }

    @PutMapping("/phases/{phaseId}")
    @PreAuthorize("hasRole('ADMIN')")
    fun updateBlueprintPhaseById(
        @PathVariable phaseId: UUID,
        @Valid @RequestBody request: UpdateBlueprintPhaseRequest,
    ): UpdateBlueprintPhaseResponse {
        return blueprintPhaseService.updateBlueprintPhaseById(BlueprintScope.Global, phaseId, request)
    }

    @PutMapping("/phases/{phaseId}/position")
    @PreAuthorize("hasRole('ADMIN')")
    fun updateBlueprintPhasePositionById(
        @PathVariable phaseId: UUID,
        @Valid @RequestBody request: UpdateBlueprintPhasePositionRequest,
    ): List<UpdateBlueprintPhasePositionResponse> {
        return blueprintPhaseService.updateBlueprintPhasePositionById(BlueprintScope.Global, phaseId, request)
    }

    @DeleteMapping("/phases/{phaseId}")
    @PreAuthorize("hasRole('ADMIN')")
    fun deleteBlueprintPhaseById(
        @PathVariable phaseId: UUID,
        @RequestBody request: DeleteBlueprintPhaseRequest,
    ) {
        blueprintPhaseService.deleteBlueprintPhaseById(BlueprintScope.Global, phaseId, request)
    }
}

@RestController
@RequestMapping("/api/v1/projects/{projectId}/onboarding/blueprints")
class BlueprintPhaseController(
    private val blueprintPhaseService: BlueprintPhaseService,
) {
    @GetMapping("/path/{pathId}/phases")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun getBlueprintPhasesForPath(
        @PathVariable projectId: UUID,
        @PathVariable pathId: UUID,
    ): List<GetBlueprintPhaseResponse> {
        return blueprintPhaseService.getBlueprintPhasesForPath(BlueprintScope.Project(projectId), pathId)
    }

    @GetMapping("/phases/{phaseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun getBlueprintPhaseById(
        @PathVariable projectId: UUID,
        @PathVariable phaseId: UUID,
    ): GetBlueprintPhaseResponse {
        return blueprintPhaseService.getBlueprintPhaseById(BlueprintScope.Project(projectId), phaseId)
    }

    @PostMapping("/path/{pathId}/phases")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun createBlueprintPhaseForPath(
        @PathVariable projectId: UUID,
        @PathVariable pathId: UUID,
        @Valid @RequestBody request: CreateBlueprintPhaseRequest,
    ): CreateBlueprintPhaseResponse {
        return blueprintPhaseService.createBlueprintPhaseForPath(BlueprintScope.Project(projectId), pathId, request)
    }

    @PutMapping("/phases/{phaseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun updateBlueprintPhaseById(
        @PathVariable projectId: UUID,
        @PathVariable phaseId: UUID,
        @Valid @RequestBody request: UpdateBlueprintPhaseRequest,
    ): UpdateBlueprintPhaseResponse {
        return blueprintPhaseService.updateBlueprintPhaseById(BlueprintScope.Project(projectId), phaseId, request)
    }

    @PutMapping("/phases/{phaseId}/position")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun updateBlueprintPhasePositionById(
        @PathVariable projectId: UUID,
        @PathVariable phaseId: UUID,
        @Valid @RequestBody request: UpdateBlueprintPhasePositionRequest,
    ): List<UpdateBlueprintPhasePositionResponse> {
        return blueprintPhaseService.updateBlueprintPhasePositionById(
            BlueprintScope.Project(projectId),
            phaseId,
            request,
        )
    }

    @DeleteMapping("/phases/{phaseId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun deleteBlueprintPhaseById(
        @PathVariable projectId: UUID,
        @PathVariable phaseId: UUID,
        @RequestBody request: DeleteBlueprintPhaseRequest,
    ) {
        blueprintPhaseService.deleteBlueprintPhaseById(BlueprintScope.Project(projectId), phaseId, request)
    }
}
