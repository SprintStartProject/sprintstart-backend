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
class BlueprintPhaseAdminController(
    private val blueprintPhaseService: BlueprintPhaseService,
) {
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/path/{pathId}/phases")
    fun getBlueprintPhasesForPath(
        @PathVariable pathId: UUID,
    ): List<GetBlueprintPhaseResponse> {
        return blueprintPhaseService.getBlueprintPhasesForPath(BlueprintScope.Global, pathId)
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/phases/{phaseId}")
    fun getBlueprintPhaseById(
        @PathVariable phaseId: UUID,
    ): GetBlueprintPhaseResponse {
        return blueprintPhaseService.getBlueprintPhaseById(BlueprintScope.Global, phaseId)
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/path/{pathId}/phases")
    fun createBlueprintPhaseForPath(
        @PathVariable pathId: UUID,
        @Valid @RequestBody request: CreateBlueprintPhaseRequest,
    ): CreateBlueprintPhaseResponse {
        return blueprintPhaseService.createBlueprintPhaseForPath(BlueprintScope.Global, pathId, request)
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/phases/{phaseId}")
    fun updateBlueprintPhaseById(
        @PathVariable phaseId: UUID,
        @Valid @RequestBody request: UpdateBlueprintPhaseRequest,
    ): UpdateBlueprintPhaseResponse {
        return blueprintPhaseService.updateBlueprintPhaseById(BlueprintScope.Global, phaseId, request)
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/phases/{phaseId}/position")
    fun updateBlueprintPhasePositionById(
        @PathVariable phaseId: UUID,
        @Valid @RequestBody request: UpdateBlueprintPhasePositionRequest,
    ): List<UpdateBlueprintPhasePositionResponse> {
        return blueprintPhaseService.updateBlueprintPhasePositionById(BlueprintScope.Global, phaseId, request)
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/phases/{phaseId}")
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
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN','PM','HR')")
    @GetMapping("/path/{pathId}/phases")
    fun getBlueprintPhasesForPath(
        @PathVariable projectId: UUID,
        @PathVariable pathId: UUID,
    ): List<GetBlueprintPhaseResponse> {
        return blueprintPhaseService.getBlueprintPhasesForPath(BlueprintScope.Project(projectId), pathId)
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN','PM','HR')")
    @GetMapping("/phases/{phaseId}")
    fun getBlueprintPhaseById(
        @PathVariable projectId: UUID,
        @PathVariable phaseId: UUID,
    ): GetBlueprintPhaseResponse {
        return blueprintPhaseService.getBlueprintPhaseById(BlueprintScope.Project(projectId), phaseId)
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','PM','HR')")
    @PostMapping("/path/{pathId}/phases")
    fun createBlueprintPhaseForPath(
        @PathVariable projectId: UUID,
        @PathVariable pathId: UUID,
        @Valid @RequestBody request: CreateBlueprintPhaseRequest,
    ): CreateBlueprintPhaseResponse {
        return blueprintPhaseService.createBlueprintPhaseForPath(BlueprintScope.Project(projectId), pathId, request)
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN','PM','HR')")
    @PutMapping("/phases/{phaseId}")
    fun updateBlueprintPhaseById(
        @PathVariable projectId: UUID,
        @PathVariable phaseId: UUID,
        @Valid @RequestBody request: UpdateBlueprintPhaseRequest,
    ): UpdateBlueprintPhaseResponse {
        return blueprintPhaseService.updateBlueprintPhaseById(BlueprintScope.Project(projectId), phaseId, request)
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN','PM','HR')")
    @PutMapping("/phases/{phaseId}/position")
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

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','PM','HR')")
    @DeleteMapping("/phases/{phaseId}")
    fun deleteBlueprintPhaseById(
        @PathVariable projectId: UUID,
        @PathVariable phaseId: UUID,
        @RequestBody request: DeleteBlueprintPhaseRequest,
    ) {
        blueprintPhaseService.deleteBlueprintPhaseById(BlueprintScope.Project(projectId), phaseId, request)
    }
}
