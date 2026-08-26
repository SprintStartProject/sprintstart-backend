package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.path.CreateBlueprintPathRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.path.UpdateBlueprintPathRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.path.CreateBlueprintPathResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.path.GetBlueprintPathOverviewResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.path.GetBlueprintPathResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.path.UpdateBlueprintPathResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintPathService
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
@RequestMapping("/api/v1/projects/{projectId}/onboarding/blueprints")
class BlueprintPathController(
    private val blueprintPathService: BlueprintPathService,
) {
    // This should return based on the blueprintKey
    @GetMapping()
    fun getBlueprints(
        @PathVariable projectId: UUID,
    ): List<GetBlueprintPathOverviewResponse> {
        return blueprintPathService.getBlueprintPathOverviewsForProjectGroupedByBlueprintKey(projectId)
    }

    @GetMapping("/{blueprintKey}")
    fun getBlueprintHistoryByBlueprintKey(
        @PathVariable projectId: UUID,
        @PathVariable blueprintKey: UUID,
    ): List<GetBlueprintPathResponse> {
        return blueprintPathService.getBlueprintPathHistoryForProjectByBlueprintKey(projectId, blueprintKey)
    }

    // This should probably not be used
    @GetMapping("/paths")
    fun getBlueprintPaths(
        @PathVariable projectId: UUID,
    ): List<GetBlueprintPathOverviewResponse> {
        return blueprintPathService.getBlueprintPathOverviewsForProjectId(projectId)
    }

    @GetMapping("/paths/{pathId}")
    fun getBlueprintPathById(
        @PathVariable projectId: UUID,
        @PathVariable pathId: UUID,
    ): GetBlueprintPathResponse {
        return blueprintPathService.getBlueprintPathByProjectIdAndId(projectId, pathId)
    }

    @PostMapping("/paths")
    fun createBlueprintPath(
        @PathVariable projectId: UUID,
        @RequestBody request: CreateBlueprintPathRequest,
    ): CreateBlueprintPathResponse {
        return blueprintPathService.createBlueprintPath(projectId, request)
    }

    @PostMapping("{blueprintKey}/draft")
    fun editBlueprintPathById(
        @PathVariable projectId: UUID,
        @PathVariable blueprintKey: UUID,
    ): GetBlueprintPathResponse {
        return blueprintPathService.openBlueprintPathDraftByBlueprintKey(projectId, blueprintKey)
    }

    @PostMapping("/paths/{pathId}/publish")
    fun publishBlueprintPathById(
        @PathVariable projectId: UUID,
        @PathVariable pathId: UUID,
    ): GetBlueprintPathResponse {
        return blueprintPathService.publishBlueprintPathDraftById(projectId, pathId)
    }

    @PostMapping("/{blueprintKey}/rollBack/{rollbackVersion}")
    fun rollBackBlueprintPathByBlueprintKey(
        @PathVariable projectId: UUID,
        @PathVariable blueprintKey: UUID,
        @PathVariable rollbackVersion: Int,
    ): GetBlueprintPathResponse {
        return blueprintPathService.rollbackBlueprintPathByBlueprintKey(projectId, blueprintKey, rollbackVersion)
    }

    @PutMapping("/paths/{pathId}")
    fun updateBlueprintPathById(
        @PathVariable projectId: UUID,
        @PathVariable pathId: UUID,
        @RequestBody request: UpdateBlueprintPathRequest,
    ): UpdateBlueprintPathResponse {
        return blueprintPathService.updateBlueprintPathById(projectId, pathId, request)
    }

    // any unpublished drafts are deleted
    @PostMapping("/{blueprintKey}/archive")
    fun archiveBlueprintPathById(
        @PathVariable projectId: UUID,
        @PathVariable blueprintKey: UUID,
    ) {
        blueprintPathService.archiveBlueprintPathByBlueprintKey(projectId, blueprintKey)
    }

    @DeleteMapping("/paths/{pathId}")
    fun deleteBlueprintDraftById(
        @PathVariable projectId: UUID,
        @PathVariable pathId: UUID,
    ) {
        blueprintPathService.deleteBlueprintPathDraftById(projectId, pathId)
    }
}
