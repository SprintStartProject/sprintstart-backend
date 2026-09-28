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
import io.mockk.clearMocks
import io.mockk.mockk
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import java.util.UUID

class BlueprintTaskAdminControllerTest : BlueprintControllerTestSupport() {
    private val stepId = UUID.randomUUID()
    private val taskId = UUID.randomUUID()
    private val createBody = CreateBlueprintTaskRequest(position = 0, title = "title", description = "description")
    private val updateBody =
        UpdateBlueprintTaskRequest(revision = 0, position = 0, title = "title", description = "description")
    private val updatePositionBody = UpdateBlueprintTaskPositionRequest(revision = 0, position = 0)
    private val deleteBody = DeleteBlueprintTaskRequest(revision = 0)
    private val getResponse =
        GetBlueprintTaskResponse(
            id = taskId,
            blueprintStepId = stepId,
            revision = 0,
            position = 0,
            title = "title",
            description = "description",
        )
    private val createResponse =
        CreateBlueprintTaskResponse(
            id = taskId,
            blueprintStepId = stepId,
            revision = 0,
            position = 0,
            title = "title",
            description = "description",
        )
    private val updateResponse =
        UpdateBlueprintTaskResponse(
            id = taskId,
            blueprintStepId = stepId,
            revision = 0,
            position = 0,
            title = "title",
            description = "description",
        )
    private val updatePositionResponse = UpdateBlueprintTaskPositionResponse(id = taskId, revision = 0, position = 0)
    private val service: BlueprintTaskService = mockk()
    override val controller = BlueprintTaskAdminController(service)
    override val controllerClass = BlueprintTaskAdminController::class

    override val endpointCases =
        listOf(
            EndpointCase(
                name = "GET /steps/{stepId}/tasks",
                request =
                    get("/api/v1/onboarding/blueprints/steps/$stepId/tasks"),
                expectedStatus = 200,
                response = listOf(getResponse),
                serviceCall = {
                    service.getBlueprintTasksForStep(BlueprintScope.Global, stepId)
                },
                documentsNotFound = false,
            ),
            EndpointCase(
                name = "GET /tasks/{taskId}",
                request =
                    get("/api/v1/onboarding/blueprints/tasks/$taskId"),
                expectedStatus = 200,
                response = getResponse,
                serviceCall = {
                    service.getBlueprintTaskById(BlueprintScope.Global, taskId)
                },
            ),
            EndpointCase(
                name = "POST /steps/{stepId}/task",
                request =
                    post("/api/v1/onboarding/blueprints/steps/$stepId/task")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"position":0,"title":"title","description":"description"}"""),
                expectedStatus = 201,
                response = createResponse,
                serviceCall = {
                    service.createBlueprintTaskForStep(BlueprintScope.Global, stepId, createBody)
                },
                documentsBadRequest = true,
                documentsConflict = true,
            ),
            EndpointCase(
                name = "PUT /tasks/{taskId}",
                request =
                    put("/api/v1/onboarding/blueprints/tasks/$taskId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"revision":0,"position":0,"title":"title","description":"description"}"""),
                expectedStatus = 200,
                response = updateResponse,
                serviceCall = {
                    service.updateBlueprintTaskById(BlueprintScope.Global, taskId, updateBody)
                },
                documentsBadRequest = true,
                documentsConflict = true,
            ),
            EndpointCase(
                name = "PUT /tasks/{taskId}/position",
                request =
                    put("/api/v1/onboarding/blueprints/tasks/$taskId/position")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"revision":0,"position":0}"""),
                expectedStatus = 200,
                response = listOf(updatePositionResponse),
                serviceCall = {
                    service.updateBlueprintTaskPositionById(BlueprintScope.Global, taskId, updatePositionBody)
                },
                documentsBadRequest = true,
                documentsConflict = true,
            ),
            EndpointCase(
                name = "DELETE /tasks/{taskId}",
                request =
                    delete("/api/v1/onboarding/blueprints/tasks/$taskId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"revision":0}"""),
                expectedStatus = 204,
                response = null,
                serviceCall = {
                    service.deleteBlueprintTaskById(BlueprintScope.Global, taskId, deleteBody)
                },
                documentsConflict = true,
            ),
        )

    override fun resetMocks() {
        clearMocks(service)
    }
}
