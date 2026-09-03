package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.task.CreateBlueprintTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.task.DeleteBlueprintTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.task.UpdateBlueprintTaskPositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.task.UpdateBlueprintTaskRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.task.CreateBlueprintTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.task.GetBlueprintTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.task.UpdateBlueprintTaskPositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.task.UpdateBlueprintTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintTaskService
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
class BlueprintTaskAdminController(
    private val blueprintTaskService: BlueprintTaskService,
) {
    @GetMapping("/steps/{stepId}/tasks")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun getBlueprintTasksForStep(
        @PathVariable stepId: UUID,
    ): List<GetBlueprintTaskResponse> {
        return blueprintTaskService.getBlueprintTasksForStep(BlueprintScope.Global, stepId)
    }

    @GetMapping("/tasks/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun getTaskById(
        @PathVariable taskId: UUID,
    ): GetBlueprintTaskResponse {
        return blueprintTaskService.getBlueprintTaskById(BlueprintScope.Global, taskId)
    }

    @PostMapping("/steps/{stepId}/task")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun createTaskForStep(
        @PathVariable stepId: UUID,
        @RequestBody request: CreateBlueprintTaskRequest,
    ): CreateBlueprintTaskResponse {
        return blueprintTaskService.createBlueprintTaskForStep(BlueprintScope.Global, stepId, request)
    }

    @PutMapping("/tasks/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun updateTaskById(
        @PathVariable taskId: UUID,
        @RequestBody request: UpdateBlueprintTaskRequest,
    ): UpdateBlueprintTaskResponse {
        return blueprintTaskService.updateBlueprintTaskById(BlueprintScope.Global, taskId, request)
    }

    @PutMapping("/tasks/{taskId}/position")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun updateBlueprintTaskPositionById(
        @PathVariable taskId: UUID,
        @RequestBody request: UpdateBlueprintTaskPositionRequest,
    ): List<UpdateBlueprintTaskPositionResponse> {
        return blueprintTaskService.updateBlueprintTaskPositionById(BlueprintScope.Global, taskId, request)
    }

    @DeleteMapping("/tasks/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun deleteTaskById(
        @PathVariable taskId: UUID,
        @RequestBody request: DeleteBlueprintTaskRequest,
    ) {
        blueprintTaskService.deleteBlueprintTaskById(BlueprintScope.Global, taskId, request)
    }
}

@RestController
@RequestMapping("/api/v1/projects/{projectId}/onboarding/blueprints")
class BlueprintTaskController(
    private val blueprintTaskService: BlueprintTaskService,
) {
    @GetMapping("/steps/{stepId}/tasks")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun getBlueprintTasksForStep(
        @PathVariable projectId: UUID,
        @PathVariable stepId: UUID,
    ): List<GetBlueprintTaskResponse> {
        return blueprintTaskService.getBlueprintTasksForStep(BlueprintScope.Project(projectId), stepId)
    }

    @GetMapping("/tasks/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun getTaskById(
        @PathVariable projectId: UUID,
        @PathVariable taskId: UUID,
    ): GetBlueprintTaskResponse {
        return blueprintTaskService.getBlueprintTaskById(BlueprintScope.Project(projectId), taskId)
    }

    @PostMapping("/steps/{stepId}/task")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun createTaskForStep(
        @PathVariable projectId: UUID,
        @PathVariable stepId: UUID,
        @RequestBody request: CreateBlueprintTaskRequest,
    ): CreateBlueprintTaskResponse {
        return blueprintTaskService.createBlueprintTaskForStep(
            BlueprintScope.Project(projectId),
            stepId,
            request,
        )
    }

    @PutMapping("/tasks/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun updateTaskById(
        @PathVariable projectId: UUID,
        @PathVariable taskId: UUID,
        @RequestBody request: UpdateBlueprintTaskRequest,
    ): UpdateBlueprintTaskResponse {
        return blueprintTaskService.updateBlueprintTaskById(
            BlueprintScope.Project(projectId),
            taskId,
            request,
        )
    }

    @PutMapping("/tasks/{taskId}/position")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun updateBlueprintTaskPositionById(
        @PathVariable projectId: UUID,
        @PathVariable taskId: UUID,
        @RequestBody request: UpdateBlueprintTaskPositionRequest,
    ): List<UpdateBlueprintTaskPositionResponse> {
        return blueprintTaskService.updateBlueprintTaskPositionById(
            BlueprintScope.Project(projectId),
            taskId,
            request,
        )
    }

    @DeleteMapping("/tasks/{taskId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun deleteTaskById(
        @PathVariable projectId: UUID,
        @PathVariable taskId: UUID,
        @RequestBody request: DeleteBlueprintTaskRequest,
    ) {
        blueprintTaskService.deleteBlueprintTaskById(BlueprintScope.Project(projectId), taskId, request)
    }
}
