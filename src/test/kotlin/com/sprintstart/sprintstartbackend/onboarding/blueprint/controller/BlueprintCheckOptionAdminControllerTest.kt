package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkoption.CreateBlueprintCheckOptionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkoption.DeleteBlueprintCheckOptionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkoption.UpdateBlueprintCheckOptionPositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkoption.UpdateBlueprintCheckOptionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkoption.CreateBlueprintCheckOptionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkoption.GetBlueprintCheckOptionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkoption.UpdateBlueprintCheckOptionPositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkoption.UpdateBlueprintCheckOptionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintCheckOptionService
import io.mockk.clearMocks
import io.mockk.mockk
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import java.util.UUID

class BlueprintCheckOptionAdminControllerTest : BlueprintControllerTestSupport() {
    private val questionId = UUID.randomUUID()
    private val optionId = UUID.randomUUID()
    private val createBody = CreateBlueprintCheckOptionRequest(position = 0, label = "Label", correct = false)
    private val updateBody =
        UpdateBlueprintCheckOptionRequest(revision = 0, position = 0, label = "Label", correct = false)
    private val positionBody = UpdateBlueprintCheckOptionPositionRequest(revision = 0, position = 0)
    private val deleteBody = DeleteBlueprintCheckOptionRequest(revision = 0)
    private val service: BlueprintCheckOptionService = mockk()
    override val controller = BlueprintCheckOptionAdminController(service)
    override val controllerClass = BlueprintCheckOptionAdminController::class

    override val endpointCases =
        listOf(
            EndpointCase(
                name = "GET /questions/{questionId}/options",
                request =
                    get("/api/v1/onboarding/blueprints/checks/questions/$questionId/options"),
                expectedStatus = 200,
                response = emptyList<GetBlueprintCheckOptionResponse>(),
                serviceCall = {
                    service.getBlueprintCheckOptionsForQuestion(
                        BlueprintScope.Global,
                        questionId,
                    )
                },
                documentsNotFound = false,
            ),
            EndpointCase(
                name = "GET /options/{optionId}",
                request =
                    get("/api/v1/onboarding/blueprints/checks/options/$optionId"),
                expectedStatus = 200,
                response =
                    GetBlueprintCheckOptionResponse(
                        id = optionId,
                        blueprintCheckQuestionId = questionId,
                        revision = 0,
                        position = 0,
                        label = "Label",
                        correct = false,
                    ),
                serviceCall = {
                    service.getBlueprintCheckOptionById(
                        BlueprintScope.Global,
                        optionId,
                    )
                },
            ),
            EndpointCase(
                name = "POST /questions/{questionId}/options",
                request =
                    post("/api/v1/onboarding/blueprints/checks/questions/$questionId/options")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"position":0,"label":"Label","correct":false}"""),
                expectedStatus = 201,
                response =
                    CreateBlueprintCheckOptionResponse(
                        id = optionId,
                        blueprintCheckQuestionId = questionId,
                        revision = 0,
                        position = 0,
                        label = "Label",
                        correct = false,
                    ),
                serviceCall = {
                    service.createBlueprintCheckOptionForQuestion(
                        BlueprintScope.Global,
                        questionId,
                        createBody,
                    )
                },
                documentsBadRequest = true,
                documentsConflict = true,
            ),
            EndpointCase(
                name = "PUT /options/{optionId}",
                request =
                    put("/api/v1/onboarding/blueprints/checks/options/$optionId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"revision":0,"position":0,"label":"Label","correct":false}"""),
                expectedStatus = 200,
                response =
                    UpdateBlueprintCheckOptionResponse(
                        id = optionId,
                        blueprintCheckQuestionId = questionId,
                        revision = 0,
                        position = 0,
                        label = "Label",
                        correct = false,
                    ),
                serviceCall = {
                    service.updateBlueprintCheckOptionById(
                        BlueprintScope.Global,
                        optionId,
                        updateBody,
                    )
                },
                documentsBadRequest = true,
                documentsConflict = true,
            ),
            EndpointCase(
                name = "PUT /options/{optionId}/position",
                request =
                    put("/api/v1/onboarding/blueprints/checks/options/$optionId/position")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"revision":0,"position":0}"""),
                expectedStatus = 200,
                response = emptyList<UpdateBlueprintCheckOptionPositionResponse>(),
                serviceCall = {
                    service.updateBlueprintCheckOptionPositionById(
                        BlueprintScope.Global,
                        optionId,
                        positionBody,
                    )
                },
                documentsBadRequest = true,
                documentsConflict = true,
            ),
            EndpointCase(
                name = "DELETE /options/{optionId}",
                request =
                    delete("/api/v1/onboarding/blueprints/checks/options/$optionId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"revision":0}"""),
                expectedStatus = 204,
                response = null,
                serviceCall = {
                    service.deleteBlueprintCheckOptionById(
                        BlueprintScope.Global,
                        optionId,
                        deleteBody,
                    )
                },
                documentsConflict = true,
            ),
        )

    override fun resetMocks() {
        clearMocks(service)
    }
}
