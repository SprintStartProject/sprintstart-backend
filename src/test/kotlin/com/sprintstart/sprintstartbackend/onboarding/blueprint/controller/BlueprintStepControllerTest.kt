package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step.CreateBlueprintStepRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step.DeleteBlueprintStepRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step.UpdateBlueprintStepPositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step.UpdateBlueprintStepRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.CreateBlueprintStepResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.DeleteBlueprintStepResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.GetBlueprintStepResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.UpdateBlueprintStepPositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.UpdateBlueprintStepResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintStepService
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import io.mockk.clearMocks
import io.mockk.mockk
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import java.util.UUID

class BlueprintStepControllerTest : BlueprintControllerTestSupport() {
    private val projectId = UUID.randomUUID()
    private val phaseId = UUID.randomUUID()
    private val stepId = UUID.randomUUID()
    private val createBody =
        CreateBlueprintStepRequest(
            position = 0,
            title = "title",
            description = "description",
            type = StepType.TASK,
            estimatedMinutes = 0,
            expectedOutcome = "outcome",
            graphX = null,
            graphY = null,
        )
    private val updateBody =
        UpdateBlueprintStepRequest(
            revision = 0,
            position = 0,
            title = "title",
            description = "description",
            type = StepType.TASK,
            aiAssisted = false,
            estimatedMinutes = 0,
            expectedOutcome = "outcome",
        )
    private val updatePositionBody = UpdateBlueprintStepPositionRequest(revision = 0, position = 0)
    private val deleteBody = DeleteBlueprintStepRequest(revision = 0)
    private val getResponse =
        GetBlueprintStepResponse(
            id = stepId,
            blueprintPhaseId = phaseId,
            revision = 0,
            position = 0,
            title = "title",
            description = "description",
            type = StepType.TASK,
            aiAssisted = false,
            estimatedMinutes = 0,
            expectedOutcome = "outcome",
            blueprintTasks = emptyList(),
            blueprintResources = emptyList(),
            blockerIds = emptySet(),
        )
    private val createResponse =
        CreateBlueprintStepResponse(
            id = stepId,
            blueprintPhaseId = phaseId,
            revision = 0,
            position = 0,
            title = "title",
            description = "description",
            type = StepType.TASK,
            aiAssisted = false,
            estimatedMinutes = 0,
            expectedOutcome = "outcome",
            blockerIds = emptySet(),
            graphX = null,
            graphY = null,
            blueprintTasks = emptyList(),
            blueprintResources = emptyList(),
        )
    private val updateResponse =
        UpdateBlueprintStepResponse(
            id = stepId,
            blueprintPhaseId = phaseId,
            revision = 0,
            position = 0,
            title = "title",
            description = "description",
            type = StepType.TASK,
            aiAssisted = false,
            estimatedMinutes = 0,
            expectedOutcome = "outcome",
            blockerIds = emptySet(),
            graphX = null,
            graphY = null,
            blueprintTasks = emptyList(),
            blueprintResources = emptyList(),
        )
    private val updatePositionResponse = UpdateBlueprintStepPositionResponse(id = stepId, revision = 0, position = 0)
    private val deleteResponse = DeleteBlueprintStepResponse(updatedSteps = emptyList())
    private val service: BlueprintStepService = mockk()
    override val controller = BlueprintStepController(service)
    override val controllerClass = BlueprintStepController::class

    override val endpointCases =
        listOf(
            EndpointCase(
                name = "GET /phases/{phaseId}/steps",
                request =
                    get("/api/v1/projects/$projectId/onboarding/blueprints/phases/$phaseId/steps"),
                expectedStatus = 200,
                response = listOf(getResponse),
                serviceCall = {
                    service.getBlueprintStepForPhase(BlueprintScope.Project(projectId), phaseId)
                },
                documentsNotFound = false,
            ),
            EndpointCase(
                name = "GET /steps/{stepId}",
                request =
                    get("/api/v1/projects/$projectId/onboarding/blueprints/steps/$stepId"),
                expectedStatus = 200,
                response = getResponse,
                serviceCall = {
                    service.getBlueprintStepById(BlueprintScope.Project(projectId), stepId)
                },
            ),
            EndpointCase(
                name = "POST /phases/{phaseId}/steps",
                request =
                    post("/api/v1/projects/$projectId/onboarding/blueprints/phases/$phaseId/steps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """{"position":0,"title":"title","description":"description","type":"TASK",
                                |"estimatedMinutes":0,"expectedOutcome":"outcome","graphX":null,
                                |"graphY":null}
                            """.trimMargin(),
                        ),
                expectedStatus = 201,
                response = createResponse,
                serviceCall = {
                    service.createBlueprintStepForPhase(BlueprintScope.Project(projectId), phaseId, createBody)
                },
                documentsBadRequest = true,
                documentsConflict = true,
            ),
            EndpointCase(
                name = "PUT /steps/{stepId}",
                request =
                    put("/api/v1/projects/$projectId/onboarding/blueprints/steps/$stepId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """{"revision":0,"position":0,"title":"title","description":"description",
                                |"type":"TASK","aiAssisted":false,"estimatedMinutes":0,
                                |"expectedOutcome":"outcome"}
                            """.trimMargin(),
                        ),
                expectedStatus = 200,
                response = updateResponse,
                serviceCall = {
                    service.updateBlueprintStepById(BlueprintScope.Project(projectId), stepId, updateBody)
                },
                documentsBadRequest = true,
                documentsConflict = true,
            ),
            EndpointCase(
                name = "PUT /steps/{stepId}/position",
                request =
                    put("/api/v1/projects/$projectId/onboarding/blueprints/steps/$stepId/position")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"revision":0,"position":0}"""),
                expectedStatus = 200,
                response = listOf(updatePositionResponse),
                serviceCall = {
                    service.updateBlueprintStepPositionById(
                        BlueprintScope.Project(projectId),
                        stepId,
                        updatePositionBody,
                    )
                },
                documentsBadRequest = true,
                documentsConflict = true,
            ),
            EndpointCase(
                name = "DELETE /steps/{stepId}",
                request =
                    delete("/api/v1/projects/$projectId/onboarding/blueprints/steps/$stepId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"revision":0}"""),
                expectedStatus = 200,
                response = deleteResponse,
                serviceCall = {
                    service.deleteBlueprintStepById(BlueprintScope.Project(projectId), stepId, deleteBody)
                },
                documentsConflict = true,
            ),
        )

    override fun resetMocks() {
        clearMocks(service)
    }
}
