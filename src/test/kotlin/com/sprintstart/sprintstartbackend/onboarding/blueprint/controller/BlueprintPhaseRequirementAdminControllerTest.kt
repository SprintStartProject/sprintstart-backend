package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phaseRequirement.CreateBlueprintPhaseRequirementsRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phaseRequirement.DeleteBlueprintPhaseRequirementsRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.CreateBlueprintPhaseRequirementsResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.DeleteBlueprintPhaseRequirementsResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintPhaseRequirementService
import io.mockk.clearMocks
import io.mockk.mockk
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import java.util.UUID

class BlueprintPhaseRequirementAdminControllerTest : BlueprintControllerTestSupport() {
    private val phaseId = UUID.randomUUID()
    private val createBody = CreateBlueprintPhaseRequirementsRequest(revision = 0, requirements = emptySet())
    private val deleteBody = DeleteBlueprintPhaseRequirementsRequest(revision = 0, requirementIds = emptySet())
    private val service: BlueprintPhaseRequirementService = mockk()
    override val controller = BlueprintPhaseRequirementAdminController(service)
    override val controllerClass = BlueprintPhaseRequirementAdminController::class

    override val endpointCases =
        listOf(
            EndpointCase(
                name = "POST /phases/{phaseId}/requirements",
                request =
                    post("/api/v1/onboarding/blueprints/phases/$phaseId/requirements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"revision":0,"requirements":[]}"""),
                expectedStatus = 200,
                response = CreateBlueprintPhaseRequirementsResponse(revision = 0, requirements = emptySet()),
                serviceCall = {
                    service.createBlueprintPhaseRequirementsForPhase(BlueprintScope.Global, phaseId, createBody)
                },
                documentsBadRequest = true,
                documentsConflict = true,
            ),
            EndpointCase(
                name = "DELETE /phases/{phaseId}/requirements",
                request =
                    delete("/api/v1/onboarding/blueprints/phases/$phaseId/requirements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"revision":0,"requirementIds":[]}"""),
                expectedStatus = 200,
                response = DeleteBlueprintPhaseRequirementsResponse(revision = 0),
                serviceCall = {
                    service.deleteBlueprintPhaseRequirementsForPhase(BlueprintScope.Global, phaseId, deleteBody)
                },
                documentsBadRequest = true,
                documentsConflict = true,
            ),
        )

    override fun resetMocks() {
        clearMocks(service)
    }
}
