package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.subGraphNode.AddBlueprintSubGraphNodeBlockerRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.subGraphNode.RemoveBlueprintSubGraphNodeBlockerRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.subGraphNode.RemoveBlueprintSubGraphNodePositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.subGraphNode.UpdateBlueprintSubGraphNodePositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.subGraphNode.AddBlueprintSubGraphNodeBlockerResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.subGraphNode.RemoveBlueprintSubGraphNodeBlockerResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.subGraphNode.RemoveBlueprintSubGraphNodePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.subGraphNode.UpdateBlueprintSubGraphNodePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintSubGraphNodeService
import io.mockk.clearMocks
import io.mockk.mockk
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import java.util.UUID

class BlueprintSubGraphAdminControllerTest : BlueprintControllerTestSupport() {
    private val nodeId = UUID.randomUUID()
    private val blockerId = UUID.randomUUID()
    private val addBlockerBody = AddBlueprintSubGraphNodeBlockerRequest(revision = 0)
    private val updatePositionBody =
        UpdateBlueprintSubGraphNodePositionRequest(revision = 0, graphX = 1.0, graphY = 2.0)
    private val removeBlockerBody = RemoveBlueprintSubGraphNodeBlockerRequest(revision = 0)
    private val removePositionBody = RemoveBlueprintSubGraphNodePositionRequest(revision = 0)
    private val service: BlueprintSubGraphNodeService = mockk()
    override val controller = BlueprintSubGraphAdminController(service)
    override val controllerClass = BlueprintSubGraphAdminController::class

    override val endpointCases =
        listOf(
            EndpointCase(
                name = "POST /sub-graph-nodes/{nodeId}/blockers/{blockerId}",
                request =
                    post("/api/v1/onboarding/blueprints/sub-graph-nodes/$nodeId/blockers/$blockerId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"revision":0}"""),
                expectedStatus = 200,
                response = AddBlueprintSubGraphNodeBlockerResponse(revision = 1, blockerIds = setOf(blockerId)),
                serviceCall = {
                    service.addSubGraphNodeBlocker(
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
                name = "PUT /sub-graph-nodes/{nodeId}/position",
                request =
                    put("/api/v1/onboarding/blueprints/sub-graph-nodes/$nodeId/position")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"revision":0,"graphX":1.0,"graphY":2.0}"""),
                expectedStatus = 200,
                response = UpdateBlueprintSubGraphNodePositionResponse(revision = 1, graphX = 1.0, graphY = 2.0),
                serviceCall = {
                    service.updateSubGraphNodePositionById(
                        BlueprintScope.Global,
                        nodeId,
                        updatePositionBody,
                    )
                },
                documentsConflict = true,
            ),
            EndpointCase(
                name = "DELETE /sub-graph-nodes/{nodeId}/blockers/{blockerId}",
                request =
                    delete("/api/v1/onboarding/blueprints/sub-graph-nodes/$nodeId/blockers/$blockerId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"revision":0}"""),
                expectedStatus = 200,
                response = RemoveBlueprintSubGraphNodeBlockerResponse(revision = 1, blockerIds = emptySet()),
                serviceCall = {
                    service.removeSubGraphNodeBlocker(
                        BlueprintScope.Global,
                        nodeId,
                        blockerId,
                        removeBlockerBody,
                    )
                },
                documentsConflict = true,
            ),
            EndpointCase(
                name = "DELETE /sub-graph-nodes/{nodeId}/position",
                request =
                    delete("/api/v1/onboarding/blueprints/sub-graph-nodes/$nodeId/position")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"revision":0}"""),
                expectedStatus = 200,
                response = RemoveBlueprintSubGraphNodePositionResponse(updatedNodes = emptyList()),
                serviceCall = {
                    service.removeSubGraphNodePositionById(
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
