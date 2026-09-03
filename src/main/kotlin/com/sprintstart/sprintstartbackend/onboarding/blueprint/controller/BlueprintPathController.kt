package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.path.CreateBlueprintPathRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.path.UpdateBlueprintPathRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.path.CreateBlueprintPathResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.path.GetBlueprintPathOverviewResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.path.GetBlueprintPathResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.path.UpdateBlueprintPathResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintPathService
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
class BlueprintPathAdminController(
    private val blueprintPathService: BlueprintPathService,
) {
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    fun getBlueprints(): List<GetBlueprintPathOverviewResponse> {
        return blueprintPathService.getBlueprintPathOverviewsGroupedByBlueprintKey(BlueprintScope.Global)
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{blueprintKey}")
    fun getBlueprintHistoryByBlueprintKey(
        @PathVariable blueprintKey: UUID,
    ): List<GetBlueprintPathResponse> {
        return blueprintPathService.getBlueprintPathHistoryByBlueprintKey(BlueprintScope.Global, blueprintKey)
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/paths/{pathId}")
    fun getBlueprintPathById(
        @PathVariable pathId: UUID,
    ): GetBlueprintPathResponse {
        return blueprintPathService.getBlueprintPathById(BlueprintScope.Global, pathId)
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/paths")
    fun createBlueprintPath(
        @RequestBody request: CreateBlueprintPathRequest,
    ): CreateBlueprintPathResponse {
        return blueprintPathService.createBlueprintPath(BlueprintScope.Global, request)
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("{blueprintKey}/draft")
    fun editBlueprintPathById(
        @PathVariable blueprintKey: UUID,
    ): GetBlueprintPathResponse {
        return blueprintPathService.openBlueprintPathDraftByBlueprintKey(BlueprintScope.Global, blueprintKey)
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/paths/{pathId}/publish")
    fun publishBlueprintPathById(
        @PathVariable pathId: UUID,
    ): GetBlueprintPathResponse {
        return blueprintPathService.publishBlueprintPathDraftById(BlueprintScope.Global, pathId)
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{blueprintKey}/rollBack/{rollbackVersion}")
    fun rollBackBlueprintPathByBlueprintKey(
        @PathVariable blueprintKey: UUID,
        @PathVariable rollbackVersion: Int,
    ): GetBlueprintPathResponse {
        return blueprintPathService.rollbackBlueprintPathByBlueprintKey(
            BlueprintScope.Global,
            blueprintKey,
            rollbackVersion,
        )
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/paths/{pathId}")
    fun updateBlueprintPathById(
        @PathVariable pathId: UUID,
        @RequestBody request: UpdateBlueprintPathRequest,
    ): UpdateBlueprintPathResponse {
        return blueprintPathService.updateBlueprintPathById(BlueprintScope.Global, pathId, request)
    }

    // any unpublished drafts are deleted
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{blueprintKey}/archive")
    fun archiveBlueprintPathById(
        @PathVariable blueprintKey: UUID,
    ) {
        blueprintPathService.archiveBlueprintPathByBlueprintKey(BlueprintScope.Global, blueprintKey)
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/paths/{pathId}")
    fun deleteBlueprintDraftById(
        @PathVariable pathId: UUID,
    ) {
        blueprintPathService.deleteBlueprintPathDraftById(BlueprintScope.Global, pathId)
    }
}

@RestController
@RequestMapping("/api/v1/projects/{projectId}/onboarding/blueprints")
class BlueprintPathController(
    private val blueprintPathService: BlueprintPathService,
) {
    // This should return based on the blueprintKey
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @GetMapping
    fun getBlueprints(
        @PathVariable projectId: UUID,
    ): List<GetBlueprintPathOverviewResponse> {
        return blueprintPathService.getBlueprintPathOverviewsGroupedByBlueprintKey(
            BlueprintScope.Project(projectId),
        )
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @GetMapping("/{blueprintKey}")
    fun getBlueprintHistoryByBlueprintKey(
        @PathVariable projectId: UUID,
        @PathVariable blueprintKey: UUID,
    ): List<GetBlueprintPathResponse> {
        return blueprintPathService.getBlueprintPathHistoryByBlueprintKey(
            BlueprintScope.Project(projectId),
            blueprintKey,
        )
    }

    // This should probably not be used
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @GetMapping("/paths")
    fun getBlueprintPaths(
        @PathVariable projectId: UUID,
    ): List<GetBlueprintPathOverviewResponse> {
        return blueprintPathService.getBlueprintPathOverviewsForProjectId(projectId)
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @GetMapping("/paths/{pathId}")
    fun getBlueprintPathById(
        @PathVariable projectId: UUID,
        @PathVariable pathId: UUID,
    ): GetBlueprintPathResponse {
        return blueprintPathService.getBlueprintPathById(BlueprintScope.Project(projectId), pathId)
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @PostMapping("/paths")
    fun createBlueprintPath(
        @PathVariable projectId: UUID,
        @RequestBody request: CreateBlueprintPathRequest,
    ): CreateBlueprintPathResponse {
        return blueprintPathService.createBlueprintPath(BlueprintScope.Project(projectId), request)
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @PostMapping("{blueprintKey}/draft")
    fun editBlueprintPathById(
        @PathVariable projectId: UUID,
        @PathVariable blueprintKey: UUID,
    ): GetBlueprintPathResponse {
        return blueprintPathService.openBlueprintPathDraftByBlueprintKey(
            BlueprintScope.Project(projectId),
            blueprintKey,
        )
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @PostMapping("/paths/{pathId}/publish")
    fun publishBlueprintPathById(
        @PathVariable projectId: UUID,
        @PathVariable pathId: UUID,
    ): GetBlueprintPathResponse {
        return blueprintPathService.publishBlueprintPathDraftById(BlueprintScope.Project(projectId), pathId)
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @PostMapping("/{blueprintKey}/rollBack/{rollbackVersion}")
    fun rollBackBlueprintPathByBlueprintKey(
        @PathVariable projectId: UUID,
        @PathVariable blueprintKey: UUID,
        @PathVariable rollbackVersion: Int,
    ): GetBlueprintPathResponse {
        return blueprintPathService.rollbackBlueprintPathByBlueprintKey(
            BlueprintScope.Project(projectId),
            blueprintKey,
            rollbackVersion,
        )
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @PutMapping("/paths/{pathId}")
    fun updateBlueprintPathById(
        @PathVariable projectId: UUID,
        @PathVariable pathId: UUID,
        @RequestBody request: UpdateBlueprintPathRequest,
    ): UpdateBlueprintPathResponse {
        return blueprintPathService.updateBlueprintPathById(BlueprintScope.Project(projectId), pathId, request)
    }

    // any unpublished drafts are deleted
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @PostMapping("/{blueprintKey}/archive")
    fun archiveBlueprintPathById(
        @PathVariable projectId: UUID,
        @PathVariable blueprintKey: UUID,
    ) {
        blueprintPathService.archiveBlueprintPathByBlueprintKey(BlueprintScope.Project(projectId), blueprintKey)
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @DeleteMapping("/paths/{pathId}")
    fun deleteBlueprintDraftById(
        @PathVariable projectId: UUID,
        @PathVariable pathId: UUID,
    ) {
        blueprintPathService.deleteBlueprintPathDraftById(BlueprintScope.Project(projectId), pathId)
    }
}
