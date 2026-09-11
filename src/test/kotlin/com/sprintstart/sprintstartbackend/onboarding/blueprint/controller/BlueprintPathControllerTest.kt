package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.path.CreateBlueprintPathRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.path.UpdateBlueprintPathRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.graphNode.GetBlueprintGraphResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.path.CreateBlueprintPathResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.path.GetBlueprintPathOverviewResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.path.GetBlueprintPathResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.path.UpdateBlueprintPathResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintGraphNodeService
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintPathService
import io.mockk.clearMocks
import io.mockk.mockk
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import java.util.UUID

class BlueprintPathControllerTest : BlueprintControllerTestSupport() {
    private val projectId = UUID.randomUUID()
    private val blueprintKey = UUID.randomUUID()
    private val pathId = UUID.randomUUID()
    private val rollbackVersion = 1
    private val createBody = CreateBlueprintPathRequest(title = "title", description = "description")
    private val updateBody =
        UpdateBlueprintPathRequest(title = "title", description = "description", version = 0, revision = 0L)
    private val pathResponse =
        GetBlueprintPathResponse(
            id = pathId,
            blueprintKey = blueprintKey,
            version = 0,
            revision = 0L,
            title = "title",
            status = BlueprintStatus.DRAFT,
            blueprintPhases = emptyList(),
        )
    private val service: BlueprintPathService = mockk()
    private val graphNodeService: BlueprintGraphNodeService = mockk()
    override val controller = BlueprintPathController(service, graphNodeService)
    override val controllerClass = BlueprintPathController::class

    override val endpointCases =
        listOf(
            EndpointCase(
                name = "GET /",
                request = get("/api/v1/projects/$projectId/onboarding/blueprints"),
                expectedStatus = 200,
                response = emptyList<GetBlueprintPathOverviewResponse>(),
                serviceCall = {
                    service.getBlueprintPathOverviewsGroupedByBlueprintKey(BlueprintScope.Project(projectId))
                },
                documentsNotFound = false,
            ),
            EndpointCase(
                name = "GET /{blueprintKey}",
                request = get("/api/v1/projects/$projectId/onboarding/blueprints/$blueprintKey"),
                expectedStatus = 200,
                response = emptyList<GetBlueprintPathResponse>(),
                serviceCall = {
                    service.getBlueprintPathHistoryByBlueprintKey(BlueprintScope.Project(projectId), blueprintKey)
                },
                documentsNotFound = false,
            ),
            EndpointCase(
                name = "GET /paths",
                request = get("/api/v1/projects/$projectId/onboarding/blueprints/paths"),
                expectedStatus = 200,
                response = emptyList<GetBlueprintPathOverviewResponse>(),
                serviceCall = {
                    service.getBlueprintPathOverviewsForProjectId(projectId)
                },
                documentsNotFound = false,
            ),
            EndpointCase(
                name = "GET /paths/{pathId}/graph",
                request = get("/api/v1/projects/$projectId/onboarding/blueprints/paths/$pathId/graph"),
                expectedStatus = 200,
                response = GetBlueprintGraphResponse(nodes = emptyList()),
                serviceCall = {
                    graphNodeService.getGraph(BlueprintScope.Project(projectId), pathId)
                },
                documentsNotFound = false,
            ),
            EndpointCase(
                name = "GET /paths/{pathId}",
                request = get("/api/v1/projects/$projectId/onboarding/blueprints/paths/$pathId"),
                expectedStatus = 200,
                response = pathResponse,
                serviceCall = {
                    service.getBlueprintPathById(BlueprintScope.Project(projectId), pathId)
                },
            ),
            EndpointCase(
                name = "POST /paths",
                request =
                    post("/api/v1/projects/$projectId/onboarding/blueprints/paths")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"title":"title","description":"description"}"""),
                expectedStatus = 201,
                response =
                    CreateBlueprintPathResponse(
                        id = pathId,
                        blueprintKey = blueprintKey,
                        version = 0,
                        revision = 0L,
                        title = "title",
                        status = BlueprintStatus.DRAFT,
                        blueprintPhases = emptyList(),
                    ),
                serviceCall = {
                    service.createBlueprintPath(BlueprintScope.Project(projectId), createBody)
                },
                documentsNotFound = false,
            ),
            EndpointCase(
                name = "POST /{blueprintKey}/draft",
                request = post("/api/v1/projects/$projectId/onboarding/blueprints/$blueprintKey/draft"),
                expectedStatus = 200,
                response = pathResponse,
                serviceCall = {
                    service.openBlueprintPathDraftByBlueprintKey(BlueprintScope.Project(projectId), blueprintKey)
                },
            ),
            EndpointCase(
                name = "POST /paths/{pathId}/publish",
                request = post("/api/v1/projects/$projectId/onboarding/blueprints/paths/$pathId/publish"),
                expectedStatus = 200,
                response = pathResponse,
                serviceCall = {
                    service.publishBlueprintPathDraftById(BlueprintScope.Project(projectId), pathId)
                },
                documentsConflict = true,
            ),
            EndpointCase(
                name = "POST /{blueprintKey}/rollBack/{rollbackVersion}",
                request =
                    post(
                        "/api/v1/projects/$projectId/onboarding/blueprints/$blueprintKey/rollBack/$rollbackVersion",
                    ),
                expectedStatus = 200,
                response = pathResponse,
                serviceCall = {
                    service.rollbackBlueprintPathByBlueprintKey(
                        BlueprintScope.Project(projectId),
                        blueprintKey,
                        rollbackVersion,
                    )
                },
                documentsBadRequest = true,
            ),
            EndpointCase(
                name = "PUT /paths/{pathId}",
                request =
                    put("/api/v1/projects/$projectId/onboarding/blueprints/paths/$pathId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"title":"title","description":"description","version":0,"revision":0}"""),
                expectedStatus = 200,
                response =
                    UpdateBlueprintPathResponse(
                        id = pathId,
                        blueprintKey = blueprintKey,
                        version = 0,
                        revision = 0L,
                        title = "title",
                        status = BlueprintStatus.DRAFT,
                        blueprintPhases = emptyList(),
                    ),
                serviceCall = {
                    service.updateBlueprintPathById(BlueprintScope.Project(projectId), pathId, updateBody)
                },
                documentsConflict = true,
            ),
            EndpointCase(
                name = "POST /{blueprintKey}/archive",
                request = post("/api/v1/projects/$projectId/onboarding/blueprints/$blueprintKey/archive"),
                expectedStatus = 200,
                response = null,
                serviceCall = {
                    service.archiveBlueprintPathByBlueprintKey(BlueprintScope.Project(projectId), blueprintKey)
                },
            ),
            EndpointCase(
                name = "DELETE /paths/{pathId}",
                request = delete("/api/v1/projects/$projectId/onboarding/blueprints/paths/$pathId"),
                expectedStatus = 204,
                response = null,
                serviceCall = {
                    service.deleteBlueprintPathDraftById(BlueprintScope.Project(projectId), pathId)
                },
                documentsBadRequest = true,
            ),
        )

    override fun resetMocks() {
        clearMocks(service, graphNodeService)
    }
}
