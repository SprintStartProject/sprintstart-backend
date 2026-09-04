package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.graphNode.AddBlueprintGraphNodeBlockerRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.graphNode.RemoveBlueprintGraphNodeBlockerRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.graphNode.RemoveBlueprintGraphNodePositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.graphNode.UpdateBlueprintGraphNodePositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.graphNode.AddBlueprintGraphNodeBlockerResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.graphNode.RemoveBlueprintGraphNodeBlockerResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.graphNode.RemoveBlueprintGraphNodePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.graphNode.UpdateBlueprintGraphNodePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintGraphNodeService
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/onboarding/blueprint/graph-nodes/{nodeId}")
class BlueprintGraphAdminController(
    private val blueprintGraphNodeService: BlueprintGraphNodeService,
) {
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/blockers/{blockerId}")
    fun addGraphNodeBlocker(
        @PathVariable nodeId: UUID,
        @PathVariable blockerId: UUID,
        @RequestBody request: AddBlueprintGraphNodeBlockerRequest,
    ): AddBlueprintGraphNodeBlockerResponse {
        return blueprintGraphNodeService.addGraphNodeBlocker(
            BlueprintScope.Global,
            nodeId,
            blockerId,
            request,
        )
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/position")
    fun updateGraphNodePosition(
        @PathVariable nodeId: UUID,
        @RequestBody request: UpdateBlueprintGraphNodePositionRequest,
    ): UpdateBlueprintGraphNodePositionResponse {
        return blueprintGraphNodeService.updateGraphNodePositionById(
            BlueprintScope.Global,
            nodeId,
            request,
        )
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/blockers/{blockerId}")
    fun removeGraphNodeBlocker(
        @PathVariable nodeId: UUID,
        @PathVariable blockerId: UUID,
        @RequestBody request: RemoveBlueprintGraphNodeBlockerRequest,
    ): RemoveBlueprintGraphNodeBlockerResponse {
        return blueprintGraphNodeService.removeGraphNodeBlocker(
            BlueprintScope.Global,
            nodeId,
            blockerId,
            request,
        )
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/position")
    fun removeGraphNodePosition(
        @PathVariable nodeId: UUID,
        @RequestBody request: RemoveBlueprintGraphNodePositionRequest,
    ): RemoveBlueprintGraphNodePositionResponse {
        return blueprintGraphNodeService.removeGraphNodePositionById(
            BlueprintScope.Global,
            nodeId,
            request,
        )
    }
}

@RestController
@RequestMapping("/api/v1/projects/{projectId}/onboarding/blueprint/graph-nodes/{nodeId}")
class BlueprintGraphController(
    private val blueprintGraphNodeService: BlueprintGraphNodeService,
) {
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @PostMapping("/blockers/{blockerId}")
    fun addGraphNodeBlocker(
        @PathVariable projectId: UUID,
        @PathVariable nodeId: UUID,
        @PathVariable blockerId: UUID,
        @RequestBody request: AddBlueprintGraphNodeBlockerRequest,
    ): AddBlueprintGraphNodeBlockerResponse {
        return blueprintGraphNodeService.addGraphNodeBlocker(
            BlueprintScope.Project(projectId),
            nodeId,
            blockerId,
            request,
        )
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @PutMapping("/position")
    fun updateGraphNodePosition(
        @PathVariable projectId: UUID,
        @PathVariable nodeId: UUID,
        @RequestBody request: UpdateBlueprintGraphNodePositionRequest,
    ): UpdateBlueprintGraphNodePositionResponse {
        return blueprintGraphNodeService.updateGraphNodePositionById(
            BlueprintScope.Project(projectId),
            nodeId,
            request,
        )
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @DeleteMapping("/blockers/{blockerId}")
    fun removeGraphNodeBlocker(
        @PathVariable projectId: UUID,
        @PathVariable nodeId: UUID,
        @PathVariable blockerId: UUID,
        @RequestBody request: RemoveBlueprintGraphNodeBlockerRequest,
    ): RemoveBlueprintGraphNodeBlockerResponse {
        return blueprintGraphNodeService.removeGraphNodeBlocker(
            BlueprintScope.Project(projectId),
            nodeId,
            blockerId,
            request,
        )
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @DeleteMapping("/position")
    fun removeGraphNodePosition(
        @PathVariable projectId: UUID,
        @PathVariable nodeId: UUID,
        @RequestBody request: RemoveBlueprintGraphNodePositionRequest,
    ): RemoveBlueprintGraphNodePositionResponse {
        return blueprintGraphNodeService.removeGraphNodePositionById(
            BlueprintScope.Project(projectId),
            nodeId,
            request,
        )
    }
}
