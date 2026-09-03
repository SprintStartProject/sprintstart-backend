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
class BlueprintTaskAdminController(
    private val blueprintTaskService: BlueprintTaskService,
) {
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/steps/{stepId}/tasks")
    fun getBlueprintTasksForStep(
        @PathVariable stepId: UUID,
    ): List<GetBlueprintTaskResponse> {
        return blueprintTaskService.getBlueprintTasksForStep(BlueprintScope.Global, stepId)
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/tasks/{taskId}")
    fun getTaskById(
        @PathVariable taskId: UUID,
    ): GetBlueprintTaskResponse {
        return blueprintTaskService.getBlueprintTaskById(BlueprintScope.Global, taskId)
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/steps/{stepId}/task")
    fun createTaskForStep(
        @PathVariable stepId: UUID,
        @RequestBody request: CreateBlueprintTaskRequest,
    ): CreateBlueprintTaskResponse {
        return blueprintTaskService.createBlueprintTaskForStep(BlueprintScope.Global, stepId, request)
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/tasks/{taskId}")
    fun updateTaskById(
        @PathVariable taskId: UUID,
        @RequestBody request: UpdateBlueprintTaskRequest,
    ): UpdateBlueprintTaskResponse {
        return blueprintTaskService.updateBlueprintTaskById(BlueprintScope.Global, taskId, request)
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/tasks/{taskId}/position")
    fun updateBlueprintTaskPositionById(
        @PathVariable taskId: UUID,
        @RequestBody request: UpdateBlueprintTaskPositionRequest,
    ): List<UpdateBlueprintTaskPositionResponse> {
        return blueprintTaskService.updateBlueprintTaskPositionById(BlueprintScope.Global, taskId, request)
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/tasks/{taskId}")
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
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN','PM','HR')")
    @GetMapping("/steps/{stepId}/tasks")
    fun getBlueprintTasksForStep(
        @PathVariable projectId: UUID,
        @PathVariable stepId: UUID,
    ): List<GetBlueprintTaskResponse> {
        return blueprintTaskService.getBlueprintTasksForStep(BlueprintScope.Project(projectId), stepId)
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN','PM','HR')")
    @GetMapping("/tasks/{taskId}")
    fun getTaskById(
        @PathVariable projectId: UUID,
        @PathVariable taskId: UUID,
    ): GetBlueprintTaskResponse {
        return blueprintTaskService.getBlueprintTaskById(BlueprintScope.Project(projectId), taskId)
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','PM','HR')")
    @PostMapping("/steps/{stepId}/task")
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

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN','PM','HR')")
    @PutMapping("/tasks/{taskId}")
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

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN','PM','HR')")
    @PutMapping("/tasks/{taskId}/position")
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

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','PM','HR')")
    @DeleteMapping("/tasks/{taskId}")
    fun deleteTaskById(
        @PathVariable projectId: UUID,
        @PathVariable taskId: UUID,
        @RequestBody request: DeleteBlueprintTaskRequest,
    ) {
        blueprintTaskService.deleteBlueprintTaskById(BlueprintScope.Project(projectId), taskId, request)
    }
}
