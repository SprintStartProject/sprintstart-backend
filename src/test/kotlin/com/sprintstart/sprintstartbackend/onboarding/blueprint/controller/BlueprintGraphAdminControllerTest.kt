package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.graphNode.AddBlueprintGraphNodeBlockerRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.graphNode.RemoveBlueprintGraphNodeBlockerRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.graphNode.RemoveBlueprintGraphNodePositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.graphNode.UpdateBlueprintGraphNodePositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.graphNode.AddBlueprintGraphNodeBlockerResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.graphNode.RemoveBlueprintGraphNodeBlockerResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.graphNode.RemoveBlueprintGraphNodePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.graphNode.UpdateBlueprintGraphNodePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintGraphNodeService
import io.mockk.clearMocks
import io.mockk.mockk
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import java.util.UUID

class BlueprintGraphAdminControllerTest : BlueprintControllerTestSupport() {
    private val nodeId = UUID.randomUUID()
    private val blockerId = UUID.randomUUID()
    private val addBlockerBody = AddBlueprintGraphNodeBlockerRequest(revision = 0)
    private val updatePositionBody = UpdateBlueprintGraphNodePositionRequest(revision = 0, graphX = 1.0, graphY = 2.0)
    private val removeBlockerBody = RemoveBlueprintGraphNodeBlockerRequest(revision = 0)
    private val removePositionBody = RemoveBlueprintGraphNodePositionRequest(revision = 0)
    private val service: BlueprintGraphNodeService = mockk()
    override val controller = BlueprintGraphAdminController(service)
    override val controllerClass = BlueprintGraphAdminController::class

    override val endpointCases =
        listOf(
            EndpointCase(
                name = "POST /graph-nodes/{nodeId}/blockers/{blockerId}",
                request =
                    post("/api/v1/onboarding/blueprint/graph-nodes/$nodeId/blockers/$blockerId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"revision":0}"""),
                expectedStatus = 200,
                response = AddBlueprintGraphNodeBlockerResponse(revision = 1, blockerIds = setOf(blockerId)),
                serviceCall = {
                    service.addGraphNodeBlocker(
                        BlueprintScope.Global,
                        nodeId,
                        blockerId,
                        addBlockerBody,
                    )
                },
                documentsBadRequest = true,
                documentsConflict = true,
            ),
            EndpointCase(
                name = "PUT /graph-nodes/{nodeId}/position",
                request =
                    put("/api/v1/onboarding/blueprint/graph-nodes/$nodeId/position")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"revision":0,"graphX":1.0,"graphY":2.0}"""),
                expectedStatus = 200,
                response = UpdateBlueprintGraphNodePositionResponse(revision = 1, graphX = 1.0, graphY = 2.0),
                serviceCall = {
                    service.updateGraphNodePositionById(
                        BlueprintScope.Global,
                        nodeId,
                        updatePositionBody,
                    )
                },
                documentsConflict = true,
            ),
            EndpointCase(
                name = "DELETE /graph-nodes/{nodeId}/blockers/{blockerId}",
                request =
                    delete("/api/v1/onboarding/blueprint/graph-nodes/$nodeId/blockers/$blockerId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"revision":0}"""),
                expectedStatus = 200,
                response = RemoveBlueprintGraphNodeBlockerResponse(revision = 1, blockerIds = emptySet()),
                serviceCall = {
                    service.removeGraphNodeBlocker(
                        BlueprintScope.Global,
                        nodeId,
                        blockerId,
                        removeBlockerBody,
                    )
                },
                documentsConflict = true,
            ),
            EndpointCase(
                name = "DELETE /graph-nodes/{nodeId}/position",
                request =
                    delete("/api/v1/onboarding/blueprint/graph-nodes/$nodeId/position")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"revision":0}"""),
                expectedStatus = 200,
                response = RemoveBlueprintGraphNodePositionResponse(changedNodes = emptyList()),
                serviceCall = {
                    service.removeGraphNodePositionById(
                        BlueprintScope.Global,
                        nodeId,
                        removePositionBody,
                    )
                },
                documentsConflict = true,
            ),
        )

    override fun resetMocks() {
        clearMocks(service)
    }
}
