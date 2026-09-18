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

class BlueprintPathAdminControllerTest : BlueprintControllerTestSupport() {
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
    override val controller = BlueprintPathAdminController(service, graphNodeService)
    override val controllerClass = BlueprintPathAdminController::class

    override val endpointCases =
        listOf(
            EndpointCase(
                name = "GET /",
                request = get("/api/v1/onboarding/blueprints"),
                expectedStatus = 200,
                response = emptyList<GetBlueprintPathOverviewResponse>(),
                serviceCall = {
                    service.getBlueprintPathOverviewsGroupedByBlueprintKey(BlueprintScope.Global)
                },
                documentsNotFound = false,
            ),
            EndpointCase(
                name = "GET /{blueprintKey}",
                request = get("/api/v1/onboarding/blueprints/$blueprintKey"),
                expectedStatus = 200,
                response = emptyList<GetBlueprintPathResponse>(),
                serviceCall = {
                    service.getBlueprintPathHistoryByBlueprintKey(BlueprintScope.Global, blueprintKey)
                },
                documentsNotFound = false,
            ),
            EndpointCase(
                name = "GET /paths/{pathId}/graph",
                request = get("/api/v1/onboarding/blueprints/paths/$pathId/graph"),
                expectedStatus = 200,
                response = GetBlueprintGraphResponse(nodes = emptyList()),
                serviceCall = {
                    graphNodeService.getGraph(BlueprintScope.Global, pathId)
                },
                documentsNotFound = false,
            ),
            EndpointCase(
                name = "GET /paths/{pathId}",
                request = get("/api/v1/onboarding/blueprints/paths/$pathId"),
                expectedStatus = 200,
                response = pathResponse,
                serviceCall = {
                    service.getBlueprintPathById(BlueprintScope.Global, pathId)
                },
            ),
            EndpointCase(
                name = "POST /paths",
                request =
                    post("/api/v1/onboarding/blueprints/paths")
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
                    service.createBlueprintPath(BlueprintScope.Global, createBody)
                },
                documentsNotFound = false,
            ),
            EndpointCase(
                name = "POST /{blueprintKey}/draft",
                request = post("/api/v1/onboarding/blueprints/$blueprintKey/draft"),
                expectedStatus = 200,
                response = pathResponse,
                serviceCall = {
                    service.openBlueprintPathDraftByBlueprintKey(BlueprintScope.Global, blueprintKey)
                },
            ),
            EndpointCase(
                name = "POST /paths/{pathId}/publish",
                request = post("/api/v1/onboarding/blueprints/paths/$pathId/publish"),
                expectedStatus = 200,
                response = pathResponse,
                serviceCall = {
                    service.publishBlueprintPathDraftById(BlueprintScope.Global, pathId)
                },
                documentsConflict = true,
            ),
            EndpointCase(
                name = "POST /{blueprintKey}/rollBack/{rollbackVersion}",
                request = post("/api/v1/onboarding/blueprints/$blueprintKey/rollBack/$rollbackVersion"),
                expectedStatus = 200,
                response = pathResponse,
                serviceCall = {
                    service.rollbackBlueprintPathByBlueprintKey(BlueprintScope.Global, blueprintKey, rollbackVersion)
                },
                documentsBadRequest = true,
            ),
            EndpointCase(
                name = "PUT /paths/{pathId}",
                request =
                    put("/api/v1/onboarding/blueprints/paths/$pathId")
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
                    service.updateBlueprintPathById(BlueprintScope.Global, pathId, updateBody)
                },
                documentsConflict = true,
            ),
            EndpointCase(
                name = "POST /{blueprintKey}/archive",
                request = post("/api/v1/onboarding/blueprints/$blueprintKey/archive"),
                expectedStatus = 200,
                response = null,
                serviceCall = {
                    service.archiveBlueprintPathByBlueprintKey(BlueprintScope.Global, blueprintKey)
                },
            ),
            EndpointCase(
                name = "DELETE /paths/{pathId}",
                request = delete("/api/v1/onboarding/blueprints/paths/$pathId"),
                expectedStatus = 204,
                response = null,
                serviceCall = {
                    service.deleteBlueprintPathDraftById(BlueprintScope.Global, pathId)
                },
                documentsBadRequest = true,
            ),
        )

    override fun resetMocks() {
        clearMocks(service, graphNodeService)
    }
}
