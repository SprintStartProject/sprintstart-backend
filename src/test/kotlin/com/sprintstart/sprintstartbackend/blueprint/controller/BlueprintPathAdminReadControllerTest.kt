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

class BlueprintPathAdminReadControllerTest : BlueprintControllerTestSupport() {
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
    override val controller = BlueprintPathAdminReadController(service, graphNodeService)
    override val controllerClass = BlueprintPathAdminReadController::class

    override val endpointCases =
        listOf(
            EndpointCase(
                name = "GET /",
                request = get("/api/v1/onboarding/blueprints"),
                expectedStatus = 200,
                response = listOf(overviewResponse),
                serviceCall = {
                    service.getBlueprintPathOverviewsGroupedByBlueprintKey(BlueprintScope.Global)
                },
                documentsNotFound = false,
            ),
            EndpointCase(
                name = "GET /{blueprintKey}",
                request = get("/api/v1/onboarding/blueprints/$blueprintKey"),
                expectedStatus = 200,
                response = listOf(pathResponse),
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
        )

    override fun resetMocks() {
        clearMocks(service, graphNodeService)
    }
}
