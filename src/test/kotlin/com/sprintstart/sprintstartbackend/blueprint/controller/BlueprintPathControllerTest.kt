package com.sprintstart.sprintstartbackend.blueprint.controller

import com.sprintstart.sprintstartbackend.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.blueprint.model.request.path.CreateBlueprintPathRequest
import com.sprintstart.sprintstartbackend.blueprint.model.request.path.UpdateBlueprintPathRequest
import com.sprintstart.sprintstartbackend.blueprint.model.response.path.CreateBlueprintPathResponse
import com.sprintstart.sprintstartbackend.blueprint.model.response.path.GetBlueprintPathResponse
import com.sprintstart.sprintstartbackend.blueprint.model.response.path.UpdateBlueprintPathResponse
import com.sprintstart.sprintstartbackend.blueprint.service.BlueprintGraphNodeService
import com.sprintstart.sprintstartbackend.blueprint.service.BlueprintPathService
import io.mockk.clearMocks
import io.mockk.mockk
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
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
    override val controller = BlueprintPathController(service)
    override val controllerClass = BlueprintPathController::class
    override val expectedPreAuthorize = "@projectAuth.canManageProject(authentication, #projectId)"

    override val endpointCases =
        listOf(
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
