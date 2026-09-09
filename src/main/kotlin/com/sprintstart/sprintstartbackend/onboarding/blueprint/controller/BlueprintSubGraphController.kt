package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.subGraphNode.AddBlueprintSubGraphNodeBlockerRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.subGraphNode.RemoveBlueprintSubGraphNodeBlockerRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.subGraphNode.RemoveBlueprintSubGraphNodePositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.subGraphNode.UpdateBlueprintSubGraphNodePositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.subGraphNode.AddBlueprintSubGraphNodeBlockerResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.subGraphNode.RemoveBlueprintSubGraphNodeBlockerResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.subGraphNode.RemoveBlueprintSubGraphNodePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.subGraphNode.UpdateBlueprintSubGraphNodePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintSubGraphNodeService
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
@RequestMapping("api/v1/onboarding/blueprints/sub-graph-nodes/{nodeId}")
class BlueprintSubGraphAdminController(
    private val blueprintSubGraphNodeService: BlueprintSubGraphNodeService,
) {
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/blockers/{blockerId}")
    fun addSubGraphNodeBlocker(
        @PathVariable nodeId: UUID,
        @PathVariable blockerId: UUID,
        @RequestBody request: AddBlueprintSubGraphNodeBlockerRequest,
    ): AddBlueprintSubGraphNodeBlockerResponse {
        return blueprintSubGraphNodeService.addSubGraphNodeBlocker(
            BlueprintScope.Global,
            nodeId,
            blockerId,
            request,
        )
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/position")
    fun updateSubGraphNodePosition(
        @PathVariable nodeId: UUID,
        @RequestBody request: UpdateBlueprintSubGraphNodePositionRequest,
    ): UpdateBlueprintSubGraphNodePositionResponse {
        return blueprintSubGraphNodeService.updateSubGraphNodePositionById(
            BlueprintScope.Global,
            nodeId,
            request,
        )
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/blockers/{blockerId}")
    fun removeSubGraphNodeBlocker(
        @PathVariable nodeId: UUID,
        @PathVariable blockerId: UUID,
        @RequestBody request: RemoveBlueprintSubGraphNodeBlockerRequest,
    ): RemoveBlueprintSubGraphNodeBlockerResponse {
        return blueprintSubGraphNodeService.removeSubGraphNodeBlocker(
            BlueprintScope.Global,
            nodeId,
            blockerId,
            request,
        )
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/position")
    fun removeSubGraphNodePosition(
        @PathVariable nodeId: UUID,
        @RequestBody request: RemoveBlueprintSubGraphNodePositionRequest,
    ): RemoveBlueprintSubGraphNodePositionResponse {
        return blueprintSubGraphNodeService.removeSubGraphNodePositionById(
            BlueprintScope.Global,
            nodeId,
            request,
        )
    }
}

@RestController
@RequestMapping("api/v1/projects/{projectId}/onboarding/blueprints/sub-graph-nodes/{nodeId}")
class BlueprintSubGraphController(
    private val blueprintSubGraphNodeService: BlueprintSubGraphNodeService,
) {
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @PostMapping("/blockers/{blockerId}")
    fun addSubGraphNodeBlocker(
        @PathVariable projectId: UUID,
        @PathVariable nodeId: UUID,
        @PathVariable blockerId: UUID,
        @RequestBody request: AddBlueprintSubGraphNodeBlockerRequest,
    ): AddBlueprintSubGraphNodeBlockerResponse {
        return blueprintSubGraphNodeService.addSubGraphNodeBlocker(
            BlueprintScope.Project(projectId),
            nodeId,
            blockerId,
            request,
        )
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @PutMapping("/position")
    fun updateSubGraphNodePosition(
        @PathVariable projectId: UUID,
        @PathVariable nodeId: UUID,
        @RequestBody request: UpdateBlueprintSubGraphNodePositionRequest,
    ): UpdateBlueprintSubGraphNodePositionResponse {
        return blueprintSubGraphNodeService.updateSubGraphNodePositionById(
            BlueprintScope.Project(projectId),
            nodeId,
            request,
        )
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @DeleteMapping("/blockers/{blockerId}")
    fun removeSubGraphNodeBlocker(
        @PathVariable projectId: UUID,
        @PathVariable nodeId: UUID,
        @PathVariable blockerId: UUID,
        @RequestBody request: RemoveBlueprintSubGraphNodeBlockerRequest,
    ): RemoveBlueprintSubGraphNodeBlockerResponse {
        return blueprintSubGraphNodeService.removeSubGraphNodeBlocker(
            BlueprintScope.Project(projectId),
            nodeId,
            blockerId,
            request,
        )
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @DeleteMapping("/position")
    fun removeSubGraphNodePosition(
        @PathVariable projectId: UUID,
        @PathVariable nodeId: UUID,
        @RequestBody request: RemoveBlueprintSubGraphNodePositionRequest,
    ): RemoveBlueprintSubGraphNodePositionResponse {
        return blueprintSubGraphNodeService.removeSubGraphNodePositionById(
            BlueprintScope.Project(projectId),
            nodeId,
            request,
        )
    }
}
