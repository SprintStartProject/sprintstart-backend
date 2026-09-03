package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phaseRequirement.CreateBlueprintPhaseRequirementsRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phaseRequirement.DeleteBlueprintPhaseRequirementsRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.CreateBlueprintPhaseRequirementsResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.DeleteBlueprintPhaseRequirementsResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintPhaseRequirementService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("api/v1/onboarding/blueprints/")
class BlueprintPhaseRequirementAdminController(
    private val blueprintPhaseRequirementService: BlueprintPhaseRequirementService,
) {
    @PostMapping("/phases/{phaseId}/requirements")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun addRequirementList(
        @PathVariable phaseId: UUID,
        @RequestBody request: CreateBlueprintPhaseRequirementsRequest,
    ): CreateBlueprintPhaseRequirementsResponse {
        return blueprintPhaseRequirementService.createBlueprintPhaseRequirementsForPhase(
            BlueprintScope.Global,
            phaseId,
            request,
        )
    }

    @DeleteMapping("/phases/{phaseId}/requirements")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun deleteRequirementList(
        @PathVariable phaseId: UUID,
        @RequestBody request: DeleteBlueprintPhaseRequirementsRequest,
    ): DeleteBlueprintPhaseRequirementsResponse {
        return blueprintPhaseRequirementService.deleteBlueprintPhaseRequirementsForPhase(
            BlueprintScope.Global,
            phaseId,
            request,
        )
    }
}

@RestController
@RequestMapping("api/v1/projects/{projectId}/onboarding/blueprints/")
class BlueprintPhaseRequirementController(
    private val blueprintPhaseRequirementService: BlueprintPhaseRequirementService,
) {
    @PostMapping("/phases/{phaseId}/requirements")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun addRequirementList(
        @PathVariable projectId: UUID,
        @PathVariable phaseId: UUID,
        @RequestBody request: CreateBlueprintPhaseRequirementsRequest,
    ): CreateBlueprintPhaseRequirementsResponse {
        return blueprintPhaseRequirementService.createBlueprintPhaseRequirementsForPhase(
            BlueprintScope.Project(projectId),
            phaseId,
            request,
        )
    }

    @DeleteMapping("/phases/{phaseId}/requirements")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun deleteRequirementList(
        @PathVariable projectId: UUID,
        @PathVariable phaseId: UUID,
        @RequestBody request: DeleteBlueprintPhaseRequirementsRequest,
    ): DeleteBlueprintPhaseRequirementsResponse {
        return blueprintPhaseRequirementService.deleteBlueprintPhaseRequirementsForPhase(
            BlueprintScope.Project(projectId),
            phaseId,
            request,
        )
    }
}
