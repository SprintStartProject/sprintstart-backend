package com.sprintstart.sprintstartbackend.offboarding.controller

import com.sprintstart.sprintstartbackend.offboarding.model.request.RiskParameters
import com.sprintstart.sprintstartbackend.offboarding.model.response.InterviewPlanResponse
import com.sprintstart.sprintstartbackend.offboarding.service.InterviewPlanService
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** PM-only planning of the exit interview of a departure, from its risk map. */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/offboarding")
@Tag(name = "Offboarding", description = "Knowledge at risk when people leave a project")
class OffboardingInterviewController(
    private val interviewPlanService: InterviewPlanService,
) {
    /**
     * Plans the questions to ask the leaving people about the files their departure puts at risk.
     */
    @Operation(
        summary = "Plan the exit interview of a departure",
        description = "Ranks the files at risk exactly like `GET /risk` with the same parameters, sends the " +
            "riskiest of them (`limit`, at most 40) with their findings to the AI service and returns the " +
            "questions only the leaving people can answer, most urgent first. Questions the project's " +
            "records already answer come back apart, with the answer and its sources. No person is named " +
            "to the AI service. PM/Admin of the project only.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Interview plan returned"),
            ApiResponse(responseCode = "400", description = "Nobody named, a blank person or an invalid setting"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Not a manager of this project"),
            ApiResponse(responseCode = "502", description = "The AI service could not plan the interview"),
        ],
    )
    @PostMapping("/interview-questions")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM') and @projectAuth.canManageProject(authentication, #projectId)")
    suspend fun interviewQuestions(
        @PathVariable projectId: ProjectId,
        parameters: RiskParameters,
    ): InterviewPlanResponse = interviewPlanService.plan(projectId, parameters.toRequest(), parameters.listLimit)
}
