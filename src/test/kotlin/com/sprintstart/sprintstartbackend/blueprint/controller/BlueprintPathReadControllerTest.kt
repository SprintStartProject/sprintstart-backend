package com.sprintstart.sprintstartbackend.blueprint.controller

import com.sprintstart.sprintstartbackend.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.blueprint.model.response.graphNode.GetBlueprintGraphResponse
import com.sprintstart.sprintstartbackend.blueprint.model.response.path.GetBlueprintPathOverviewResponse
import com.sprintstart.sprintstartbackend.blueprint.model.response.path.GetBlueprintPathResponse
import com.sprintstart.sprintstartbackend.blueprint.service.BlueprintGraphNodeService
import com.sprintstart.sprintstartbackend.blueprint.service.BlueprintPathService
import io.mockk.clearMocks
import io.mockk.mockk
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import java.util.UUID

class BlueprintPathReadControllerTest : BlueprintControllerTestSupport() {
    private val projectId = UUID.randomUUID()
    private val blueprintKey = UUID.randomUUID()
    private val pathId = UUID.randomUUID()
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
    private val overviewResponse =
        GetBlueprintPathOverviewResponse(
            id = pathId,
            blueprintKey = blueprintKey,
            version = 0,
            revision = 0L,
            title = "title",
            status = BlueprintStatus.DRAFT,
        )
    private val service: BlueprintPathService = mockk()
    private val graphNodeService: BlueprintGraphNodeService = mockk()
    override val controller = BlueprintPathReadController(service, graphNodeService)
    override val controllerClass = BlueprintPathReadController::class
    override val expectedPreAuthorize = "@projectAuth.canManageProject(authentication, #projectId)"

    override val endpointCases =
        listOf(
            EndpointCase(
                name = "GET /",
                request = get("/api/v1/projects/$projectId/onboarding/blueprints"),
                expectedStatus = 200,
                response = listOf(overviewResponse),
                serviceCall = {
                    service.getBlueprintPathOverviewsGroupedByBlueprintKey(BlueprintScope.Project(projectId))
                },
                documentsNotFound = false,
            ),
            EndpointCase(
                name = "GET /{blueprintKey}",
                request = get("/api/v1/projects/$projectId/onboarding/blueprints/$blueprintKey"),
                expectedStatus = 200,
                response = listOf(pathResponse),
                serviceCall = {
                    service.getBlueprintPathHistoryByBlueprintKey(BlueprintScope.Project(projectId), blueprintKey)
                },
                documentsNotFound = false,
            ),
            EndpointCase(
                name = "GET /paths",
                request = get("/api/v1/projects/$projectId/onboarding/blueprints/paths"),
                expectedStatus = 200,
                response = listOf(overviewResponse),
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
        )

    override fun resetMocks() {
        clearMocks(service, graphNodeService)
    }
}
