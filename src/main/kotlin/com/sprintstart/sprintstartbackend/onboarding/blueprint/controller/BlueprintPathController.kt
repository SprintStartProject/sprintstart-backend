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
@RequestMapping("/api/v1/onboarding/blueprints")
class BlueprintPathController(
    private val blueprintPathService: BlueprintPathService,
) {
    // This should return based on the blueprintKey
    @GetMapping()
    fun getBlueprints(): List<GetBlueprintPathOverviewResponse> {
        return blueprintPathService.getBlueprintPathOverviewsByBlueprintKeys()
    }

    @GetMapping("/{blueprintKey}")
    fun getBlueprintHistoryByBlueprintKey(@PathVariable blueprintKey: UUID): List<GetBlueprintPathResponse> {
        return blueprintPathService.getBlueprintPathHistoryByBlueprintKey(blueprintKey)
    }

    // This should probably not be used
    @GetMapping("/paths")
    fun getBlueprintPaths(): List<GetBlueprintPathOverviewResponse> {
        return blueprintPathService.getBlueprintPathOverviews()
    }

    @GetMapping("/paths/{pathId}")
    fun getBlueprintPathById(@PathVariable pathId: UUID): GetBlueprintPathResponse {
        return blueprintPathService.getBlueprintPathById(pathId)
    }

    @PostMapping("/paths")
    fun createBlueprintPath(
        @RequestBody request: CreateBlueprintPathRequest,
    ): CreateBlueprintPathResponse {
        return blueprintPathService.createBlueprintPath(request)
    }

    @PostMapping("/paths/{pathId}/edit")
    fun editBlueprintPathById(
        @PathVariable pathId: UUID,
    ): GetBlueprintPathResponse {
        return blueprintPathService.openBlueprintPathDraftById(pathId)
    }

    @PostMapping("/paths/{pathId}/publish")
    fun publishBlueprintPathById(
        @PathVariable pathId: UUID,
    ): GetBlueprintPathResponse {
        return blueprintPathService.publishBlueprintPathDraftById(pathId)
    }

    @PostMapping("/{blueprintKey}/rollBack/{rollbackVersion}")
    fun rollBackBlueprintPathByBlueprintKey(
        @PathVariable blueprintKey: UUID,
        @PathVariable rollbackVersion: Int,
    ): GetBlueprintPathResponse {
        return blueprintPathService.rollbackBlueprintPathByBlueprintKey(blueprintKey, rollbackVersion)
    }

    @PutMapping("/paths/{pathId}")
    fun updateBlueprintPathById(
        @PathVariable pathId: UUID,
        @RequestBody request: UpdateBlueprintPathRequest,
    ): UpdateBlueprintPathResponse {
        return blueprintPathService.updateBlueprintPathById(pathId, request)
    }

    // any unpublished drafts are deleted
    @PostMapping("/paths/{pathId}/archive")
    fun archiveBlueprintPathById(@PathVariable pathId: UUID) {
        blueprintPathService.archiveBlueprintPathByBlueprintKey(pathId)
    }

    @DeleteMapping("/paths/{pathId}")
    fun deleteBlueprintDraftById(@PathVariable pathId: UUID) {
        blueprintPathService.deleteBlueprintPathDraftById(pathId)
    }
}
