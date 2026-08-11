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
@RequestMapping("/api/v1/onboarding/blueprint")
class BlueprintPathController(
    private val blueprintPathService: BlueprintPathService,
) {
    // Filter options: status, version,
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

    @PutMapping("/paths/{pathId}")
    fun updateBlueprintPathById(
        @PathVariable pathId: UUID,
        @RequestBody request: UpdateBlueprintPathRequest,
    ): UpdateBlueprintPathResponse {
        return blueprintPathService.updateBlueprintPathById(pathId, request)
    }

    @DeleteMapping("/paths/{pathId}")
    fun deleteBlueprintPathById(@PathVariable pathId: UUID) {
        blueprintPathService.deleteBlueprintPathById(pathId)
    }
}
