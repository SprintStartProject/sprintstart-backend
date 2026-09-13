package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintPhaseType
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phase.CreateBlueprintPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phase.DeleteBlueprintPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phase.UpdateBlueprintPhasePositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phase.UpdateBlueprintPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.CreateBlueprintPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.DeleteBlueprintPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.GetBlueprintPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.UpdateBlueprintPhasePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.UpdateBlueprintPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.subGraphNode.GetBlueprintSubGraphResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintPhaseService
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintSubGraphNodeService
import io.mockk.clearMocks
import io.mockk.mockk
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import java.util.UUID

class BlueprintPhaseControllerTest : BlueprintControllerTestSupport() {
    private val projectId = UUID.randomUUID()
    private val pathId = UUID.randomUUID()
    private val phaseId = UUID.randomUUID()
    private val createBody =
        CreateBlueprintPhaseRequest(
            position = 0,
            title = "Phase",
            description = null,
            aiPrompt = null,
            type = BlueprintPhaseType.FIXED,
            graphX = null,
            graphY = null,
        )
    private val updateBody =
        UpdateBlueprintPhaseRequest(
            revision = 0,
            position = 0,
            title = "Phase",
            description = null,
            aiPrompt = null,
            type = BlueprintPhaseType.FIXED,
        )
    private val updatePositionBody = UpdateBlueprintPhasePositionRequest(revision = 0, position = 0)
    private val deleteBody = DeleteBlueprintPhaseRequest(revision = 0)
    private val phaseResponse =
        GetBlueprintPhaseResponse(
            id = phaseId,
            blueprintPathId = pathId,
            revision = 0,
            position = 0,
            title = "Phase",
            description = null,
            aiPrompt = null,
            type = BlueprintPhaseType.FIXED,
            blueprintSteps = emptyList(),
            blueprintCheckQuestions = emptyList(),
            blueprintPhaseRequirements = emptySet(),
        )
    private val service: BlueprintPhaseService = mockk()
    private val subGraphNodeService: BlueprintSubGraphNodeService = mockk()
    override val controller = BlueprintPhaseController(service, subGraphNodeService)
    override val controllerClass = BlueprintPhaseController::class

    override val endpointCases =
        listOf(
            EndpointCase(
                name = "GET /path/{pathId}/phases",
                request = get("/api/v1/projects/$projectId/onboarding/blueprints/path/$pathId/phases"),
                expectedStatus = 200,
                response = listOf(phaseResponse),
                serviceCall = {
                    service.getBlueprintPhasesForPath(BlueprintScope.Project(projectId), pathId)
                },
                documentsNotFound = false,
            ),
            EndpointCase(
                name = "POST /path/{pathId}/phases",
                request =
                    post("/api/v1/projects/$projectId/onboarding/blueprints/path/$pathId/phases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """{"position":0,"title":"Phase","description":null,"aiPrompt":null,
                                |"type":"FIXED","graphX":null,"graphY":null}
                            """.trimMargin(),
                        ),
                expectedStatus = 201,
                response =
                    CreateBlueprintPhaseResponse(
                        id = phaseId,
                        blueprintPathId = pathId,
                        revision = 0,
                        position = 0,
                        title = "Phase",
                        description = null,
                        aiPrompt = null,
                        type = BlueprintPhaseType.FIXED,
                        blueprintSteps = emptyList(),
                        blueprintCheckQuestions = emptyList(),
                        blueprintPhaseRequirements = emptySet(),
                        graphX = null,
                        graphY = null,
                    ),
                serviceCall = {
                    service.createBlueprintPhaseForPath(BlueprintScope.Project(projectId), pathId, createBody)
                },
                documentsBadRequest = true,
                documentsConflict = true,
            ),
            EndpointCase(
                name = "GET /phase/{phaseId}/graph",
                request = get("/api/v1/projects/$projectId/onboarding/blueprints/phase/$phaseId/graph"),
                expectedStatus = 200,
                response = GetBlueprintSubGraphResponse(nodes = emptyList()),
                serviceCall = {
                    subGraphNodeService.getSubGraph(BlueprintScope.Project(projectId), phaseId)
                },
                documentsNotFound = false,
            ),
            EndpointCase(
                name = "GET /phases/{phaseId}",
                request = get("/api/v1/projects/$projectId/onboarding/blueprints/phases/$phaseId"),
                expectedStatus = 200,
                response = phaseResponse,
                serviceCall = {
                    service.getBlueprintPhaseById(BlueprintScope.Project(projectId), phaseId)
                },
            ),
            EndpointCase(
                name = "PUT /phases/{phaseId}",
                request =
                    put("/api/v1/projects/$projectId/onboarding/blueprints/phases/$phaseId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """{"revision":0,"position":0,"title":"Phase","description":null,
                                |"aiPrompt":null,"type":"FIXED"}
                            """.trimMargin(),
                        ),
                expectedStatus = 200,
                response =
                    UpdateBlueprintPhaseResponse(
                        id = phaseId,
                        blueprintPathId = pathId,
                        revision = 0,
                        position = 0,
                        title = "Phase",
                        description = null,
                        aiPrompt = null,
                        type = BlueprintPhaseType.FIXED,
                        blueprintSteps = emptyList(),
                        blueprintCheckQuestions = emptyList(),
                        blueprintPhaseRequirements = emptySet(),
                    ),
                serviceCall = {
                    service.updateBlueprintPhaseById(BlueprintScope.Project(projectId), phaseId, updateBody)
                },
                documentsBadRequest = true,
                documentsConflict = true,
            ),
            EndpointCase(
                name = "PUT /phases/{phaseId}/position",
                request =
                    put("/api/v1/projects/$projectId/onboarding/blueprints/phases/$phaseId/position")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"revision":0,"position":0}"""),
                expectedStatus = 200,
                response = listOf(UpdateBlueprintPhasePositionResponse(id = phaseId, revision = 0, position = 0)),
                serviceCall = {
                    service
                        .updateBlueprintPhasePositionById(
                            BlueprintScope.Project(projectId),
                            phaseId,
                            updatePositionBody,
                        )
                },
                documentsBadRequest = true,
                documentsConflict = true,
            ),
            EndpointCase(
                name = "DELETE /phases/{phaseId}",
                request =
                    delete("/api/v1/projects/$projectId/onboarding/blueprints/phases/$phaseId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"revision":0}"""),
                expectedStatus = 200,
                response = DeleteBlueprintPhaseResponse(updatedPhases = emptyList()),
                serviceCall = {
                    service.deleteBlueprintPhaseById(BlueprintScope.Project(projectId), phaseId, deleteBody)
                },
                documentsConflict = true,
            ),
        )

    override fun resetMocks() {
        clearMocks(service, subGraphNodeService)
    }
}
