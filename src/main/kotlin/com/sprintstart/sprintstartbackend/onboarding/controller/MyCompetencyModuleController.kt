package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.request.verification.SubmitVerificationAttemptRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.module.CompetencyModuleResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.verification.SubmitVerificationAttemptResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.verification.VerificationResponse
import com.sprintstart.sprintstartbackend.onboarding.service.CompetencyModuleService
import com.sprintstart.sprintstartbackend.onboarding.service.VerificationService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * What a hire opens when they click a node on their path: the shared module for that competency.
 *
 * There is no per-user content behind these endpoints. Two hires learning the same competency in
 * the same project read the same rows, and a PM's edit reaches both — which is the whole reason
 * modules were lifted off the per-user step tree.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(name = "Onboarding - My modules", description = "Read and complete the module behind a path node")
class MyCompetencyModuleController(
    private val competencyModuleService: CompetencyModuleService,
    private val verificationService: VerificationService,
) {
    @Operation(
        summary = "Open a module",
        description = "Returns the live module behind a path node, with its ordered pages",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Module returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "The user is not a member of the module's project"),
            ApiResponse(responseCode = "404", description = "No live module with that id"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/me/modules/{moduleId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PM', 'HR')")
    fun getModule(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable moduleId: UUID,
    ): CompetencyModuleResponse = competencyModuleService.getForMe(jwt.subject, moduleId)

    @Operation(
        summary = "Read a module's check",
        description = "Returns the module's graded gate, without its rubric or expected answer",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Check returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "The user is not a member of the module's project"),
            ApiResponse(responseCode = "404", description = "No live module, or no check configured"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/me/modules/{moduleId}/verification")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PM', 'HR')")
    fun getVerification(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable moduleId: UUID,
    ): VerificationResponse = verificationService.getModuleVerificationForMe(jwt.subject, moduleId)

    @Operation(
        summary = "Submit a module check",
        description = "Grades an answer; passing writes the competency ledger and unlocks dependents",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Attempt graded"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "The user is not a member of the module's project"),
            ApiResponse(responseCode = "404", description = "No live module, or no check configured"),
            ApiResponse(responseCode = "503", description = "Grading is temporarily unavailable"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/me/modules/{moduleId}/verification/attempts")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'PM', 'HR')")
    suspend fun submitAttempt(
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable moduleId: UUID,
        @RequestBody request: SubmitVerificationAttemptRequest,
    ): SubmitVerificationAttemptResponse =
        verificationService.submitModuleAttemptForMe(jwt.subject, moduleId, request)
}
