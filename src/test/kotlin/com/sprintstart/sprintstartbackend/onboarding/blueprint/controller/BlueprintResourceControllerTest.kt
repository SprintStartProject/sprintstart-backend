package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.resource.CreateBlueprintResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.resource.DeleteBlueprintResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.resource.UpdateBlueprintResourceRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.resource.CreateBlueprintResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.resource.GetBlueprintResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.resource.UpdateBlueprintResourceResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintResourceService
import io.mockk.clearMocks
import io.mockk.mockk
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import java.util.UUID

class BlueprintResourceControllerTest : BlueprintControllerTestSupport() {
    private val projectId = UUID.randomUUID()
    private val stepId = UUID.randomUUID()
    private val resourceId = UUID.randomUUID()
    private val createBody =
        CreateBlueprintResourceRequest(
            title = "Resource",
            description = "Description",
            url = "https://example.com",
        )
    private val updateBody =
        UpdateBlueprintResourceRequest(
            revision = 0,
            title = "Resource",
            description = "Description",
            url = "https://example.com",
        )
    private val deleteBody = DeleteBlueprintResourceRequest(revision = 0)
    private val resourceResponse =
        GetBlueprintResourceResponse(
            id = resourceId,
            blueprintStepId = stepId,
            revision = 0,
            title = "Resource",
            description = "Description",
            url = "https://example.com",
        )
    private val service: BlueprintResourceService = mockk()
    override val controller = BlueprintResourceController(service)
    override val controllerClass = BlueprintResourceController::class

    override val endpointCases =
        listOf(
            EndpointCase(
                name = "GET /step/{stepId}/resources",
                request = get("/api/v1/projects/$projectId/onboarding/blueprints/step/$stepId/resources"),
                expectedStatus = 200,
                response = listOf(resourceResponse),
                serviceCall = {
                    service.getBlueprintResourcesForStep(BlueprintScope.Project(projectId), stepId)
                },
                documentsNotFound = false,
            ),
            EndpointCase(
                name = "GET /resources/{resourceId}",
                request = get("/api/v1/projects/$projectId/onboarding/blueprints/resources/$resourceId"),
                expectedStatus = 200,
                response = resourceResponse,
                serviceCall = {
                    service.getBlueprintResourceById(BlueprintScope.Project(projectId), resourceId)
                },
            ),
            EndpointCase(
                name = "POST /step/{stepId}/resources",
                request =
                    post("/api/v1/projects/$projectId/onboarding/blueprints/step/$stepId/resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"title":"Resource","description":"Description","url":"https://example.com"}"""),
                expectedStatus = 201,
                response =
                    CreateBlueprintResourceResponse(
                        id = resourceId,
                        blueprintStepId = stepId,
                        revision = 0,
                        title = "Resource",
                        description = "Description",
                        url = "https://example.com",
                    ),
                serviceCall = {
                    service.createBlueprintResourceForStep(BlueprintScope.Project(projectId), stepId, createBody)
                },
                documentsConflict = true,
            ),
            EndpointCase(
                name = "PUT /resources/{resourceId}",
                request =
                    put("/api/v1/projects/$projectId/onboarding/blueprints/resources/$resourceId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """{"revision":0,"title":"Resource","description":"Description",
                                |"url":"https://example.com"}
                            """.trimMargin(),
                        ),
                expectedStatus = 200,
                response =
                    UpdateBlueprintResourceResponse(
                        id = resourceId,
                        blueprintStepId = stepId,
                        revision = 0,
                        title = "Resource",
                        description = "Description",
                        url = "https://example.com",
                    ),
                serviceCall = {
                    service.updateBlueprintResourceById(BlueprintScope.Project(projectId), resourceId, updateBody)
                },
                documentsConflict = true,
            ),
            EndpointCase(
                name = "DELETE /resources/{resourceId}",
                request =
                    delete("/api/v1/projects/$projectId/onboarding/blueprints/resources/$resourceId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"revision":0}"""),
                expectedStatus = 204,
                response = null,
                serviceCall = {
                    service.deleteBlueprintResourceById(BlueprintScope.Project(projectId), resourceId, deleteBody)
                },
                documentsConflict = true,
            ),
        )

    override fun resetMocks() {
        clearMocks(service)
    }
}
