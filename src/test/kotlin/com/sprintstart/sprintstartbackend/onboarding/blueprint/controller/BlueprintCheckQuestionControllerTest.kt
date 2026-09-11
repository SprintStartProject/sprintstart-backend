package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkquestion.CreateBlueprintCheckQuestionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkquestion.DeleteBlueprintCheckQuestionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkquestion.UpdateBlueprintCheckQuestionPositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkquestion.UpdateBlueprintCheckQuestionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkquestion.CreateBlueprintCheckQuestionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkquestion.DeleteBlueprintCheckQuestionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkquestion.GetBlueprintCheckQuestionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkquestion.UpdateBlueprintCheckQuestionPositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkquestion.UpdateBlueprintCheckQuestionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintCheckQuestionService
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import io.mockk.clearMocks
import io.mockk.mockk
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import java.util.UUID

class BlueprintCheckQuestionControllerTest : BlueprintControllerTestSupport() {
    private val projectId = UUID.randomUUID()
    private val phaseId = UUID.randomUUID()
    private val questionId = UUID.randomUUID()
    private val createBody =
        CreateBlueprintCheckQuestionRequest(
            position = 0,
            type = CheckQuestionType.MULTIPLE_CHOICE,
            title = "Title",
            question = "Question",
            explanation = null,
            correctAnswer = null,
            graphX = null,
            graphY = null,
        )
    private val updateBody =
        UpdateBlueprintCheckQuestionRequest(
            revision = 0,
            title = "Title",
            position = 0,
            type = CheckQuestionType.MULTIPLE_CHOICE,
            question = "Question",
            explanation = null,
            correctAnswer = null,
        )
    private val positionBody = UpdateBlueprintCheckQuestionPositionRequest(revision = 0, position = 0)
    private val deleteBody = DeleteBlueprintCheckQuestionRequest(revision = 0)
    private val service: BlueprintCheckQuestionService = mockk()
    override val controller = BlueprintCheckQuestionController(service)
    override val controllerClass = BlueprintCheckQuestionController::class

    override val endpointCases =
        listOf(
            EndpointCase(
                name = "GET /phase/{phaseId}/checks/questions",
                request =
                    get("/api/v1/projects/$projectId/onboarding/blueprints/phase/$phaseId/checks/questions"),
                expectedStatus = 200,
                response = emptyList<GetBlueprintCheckQuestionResponse>(),
                serviceCall = {
                    service.getBlueprintCheckQuestionsForPhase(
                        BlueprintScope.Project(projectId),
                        phaseId,
                    )
                },
                documentsNotFound = false,
            ),
            EndpointCase(
                name = "GET /checks/questions/{questionId}",
                request =
                    get("/api/v1/projects/$projectId/onboarding/blueprints/checks/questions/$questionId"),
                expectedStatus = 200,
                response =
                    GetBlueprintCheckQuestionResponse(
                        id = questionId,
                        blueprintPhaseId = phaseId,
                        revision = 0,
                        title = "Title",
                        position = 0,
                        type = CheckQuestionType.MULTIPLE_CHOICE,
                        question = "Question",
                        explanation = null,
                        correctAnswer = null,
                        blueprintCheckOptions = emptyList(),
                    ),
                serviceCall = {
                    service.getBlueprintCheckQuestionById(
                        BlueprintScope.Project(projectId),
                        questionId,
                    )
                },
            ),
            EndpointCase(
                name = "POST /phase/{phaseId}/checks/questions",
                request =
                    post("/api/v1/projects/$projectId/onboarding/blueprints/phase/$phaseId/checks/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jacksonObjectMapper().writeValueAsString(createBody)),
                expectedStatus = 201,
                response =
                    CreateBlueprintCheckQuestionResponse(
                        id = questionId,
                        blueprintPhaseId = phaseId,
                        revision = 0,
                        title = "Title",
                        position = 0,
                        type = CheckQuestionType.MULTIPLE_CHOICE,
                        question = "Question",
                        explanation = null,
                        correctAnswer = null,
                        blueprintCheckOptions = emptyList(),
                        graphX = null,
                        graphY = null,
                    ),
                serviceCall = {
                    service.createBlueprintCheckQuestionForPhase(
                        BlueprintScope.Project(projectId),
                        phaseId,
                        createBody,
                    )
                },
                documentsBadRequest = true,
                documentsConflict = true,
            ),
            EndpointCase(
                name = "PUT /checks/question/{questionId}",
                request =
                    put("/api/v1/projects/$projectId/onboarding/blueprints/checks/question/$questionId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jacksonObjectMapper().writeValueAsString(updateBody)),
                expectedStatus = 200,
                response =
                    UpdateBlueprintCheckQuestionResponse(
                        id = questionId,
                        blueprintPhaseId = phaseId,
                        revision = 0,
                        title = "Title",
                        position = 0,
                        type = CheckQuestionType.MULTIPLE_CHOICE,
                        question = "Question",
                        explanation = null,
                        correctAnswer = null,
                        blueprintCheckOptions = emptyList(),
                    ),
                serviceCall = {
                    service.updateBlueprintCheckQuestionById(
                        BlueprintScope.Project(projectId),
                        questionId,
                        updateBody,
                    )
                },
                documentsBadRequest = true,
                documentsConflict = true,
            ),
            EndpointCase(
                name = "PUT /checks/question/{questionId}/position",
                request =
                    put("/api/v1/projects/$projectId/onboarding/blueprints/checks/question/$questionId/position")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"revision":0,"position":0}"""),
                expectedStatus = 200,
                response = emptyList<UpdateBlueprintCheckQuestionPositionResponse>(),
                serviceCall = {
                    service.updateBlueprintCheckQuestionPositionById(
                        BlueprintScope.Project(projectId),
                        questionId,
                        positionBody,
                    )
                },
                documentsBadRequest = true,
                documentsConflict = true,
            ),
            EndpointCase(
                name = "DELETE /checks/question/{questionId}",
                request =
                    delete("/api/v1/projects/$projectId/onboarding/blueprints/checks/question/$questionId")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"revision":0}"""),
                expectedStatus = 200,
                response = DeleteBlueprintCheckQuestionResponse(updatedQuestions = emptyList()),
                serviceCall = {
                    service.deleteBlueprintCheckQuestionById(
                        BlueprintScope.Project(projectId),
                        questionId,
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
