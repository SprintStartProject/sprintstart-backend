package com.sprintstart.sprintstartbackend.insights.controller

import com.sprintstart.sprintstartbackend.insights.model.dto.request.SetComponentOwnersRequest
import com.sprintstart.sprintstartbackend.insights.model.dto.response.KnowledgeGapOwnerResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.KnowledgeGapResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.KnowledgeGapsOverviewResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.RefreshKnowledgeGapsResponse
import com.sprintstart.sprintstartbackend.insights.service.KnowledgeGapsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Endpoints exposing knowledge gaps (components missing runbooks/ADRs).
 *
 * Everything that shows or changes the project's full gap panel is restricted to project managers
 * (and admins). The single exception is `/mine`, which is scoped to the caller's own component
 * ownership and is therefore open to every member of the project — it can only ever return gaps the
 * caller was already assigned. Reads are served from the cached classification; the refresh endpoint
 * triggers a reclassification via the AI service.
 */
@RestController
@RequestMapping("/api/v1/insights/knowledge-gaps")
@Tag(name = "Insights - Knowledge Gaps", description = "Insights into components missing documentation")
class KnowledgeGapsController(
    private val knowledgeGapsService: KnowledgeGapsService,
) {
    /**
     * Returns the knowledge gaps ordered by severity.
     */
    @Operation(
        summary = "Get knowledge gaps",
        description = "Returns components missing documentation, most severe first. PM/Admin only.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Knowledge gaps returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access endpoint"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping
    @PreAuthorize(
        "hasAnyRole('ADMIN', 'PM') and @projectAuth.canAccessProject(authentication, #projectId)",
    )
    fun getKnowledgeGaps(
        @RequestParam projectId: UUID,
    ): KnowledgeGapsOverviewResponse {
        return knowledgeGapsService.getKnowledgeGaps(projectId)
    }

    /**
     * Returns the caller's own knowledge gaps in the given project.
     *
     * Filtered by component ownership rather than by role, so a regular team member sees exactly the
     * components they were made owner of and nothing else. A caller who owns nothing gets an empty
     * list, which is a valid answer and not a 404.
     */
    @Operation(
        summary = "Get the knowledge gaps assigned to me",
        description =
            "Returns the components in this project that the calling user owns and that are missing " +
                "documentation, most severe first. Available to every member of the project, whatever " +
                "their permission group — managers own components too.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Knowledge gaps returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "No access to the given project"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/mine")
    // Every permission group named, because this endpoint is open to all of them on purpose:
    // ownership decides what comes back, not role, and a manager owns components too.
    // `hasRole('USER')` — what the rest of the codebase writes for "any signed-in user" — would
    // pass here as well, but only because `user` is a realm default role and so reaches every
    // token through the `default-roles-<realm>` composite. Naming the groups says what is meant
    // without leaning on that realm config staying as it is.
    @PreAuthorize(
        "hasAnyRole('USER', 'PM', 'HR', 'ADMIN') and " +
            "@projectAuth.canAccessProject(authentication, #projectId)",
    )
    fun getMyKnowledgeGaps(
        @RequestParam projectId: UUID,
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
    ): KnowledgeGapsOverviewResponse {
        return knowledgeGapsService.getMyKnowledgeGaps(projectId, jwt.subject)
    }

    /**
     * Returns the details of a single knowledge gap.
     */
    @Operation(
        summary = "Get a knowledge gap",
        description = "Returns the missing document types, owners and metadata for a single gap. PM/Admin only.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Knowledge gap returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access endpoint"),
            ApiResponse(responseCode = "404", description = "No knowledge gap found for the given id"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/{gapId}")
    @PreAuthorize(
        "hasAnyRole('ADMIN', 'PM') and @projectAuth.canAccessProject(authentication, #projectId)",
    )
    fun getKnowledgeGap(
        @RequestParam projectId: UUID,
        @PathVariable gapId: UUID,
    ): KnowledgeGapResponse {
        return knowledgeGapsService.getKnowledgeGap(projectId, gapId)
    }

    /**
     * Reclassifies knowledge gaps via the AI service and replaces the cache.
     */
    @Operation(
        summary = "Refresh knowledge gaps",
        description = "Triggers AI classification of missing documentation and rebuilds the cache. PM/Admin only.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Knowledge gaps refreshed successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access endpoint"),
            ApiResponse(responseCode = "500", description = "The AI service failed to return a classification result"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/refresh")
    @PreAuthorize(
        "hasAnyRole('ADMIN', 'PM') and @projectAuth.canAccessProject(authentication, #projectId)",
    )
    suspend fun refreshKnowledgeGaps(
        @RequestParam projectId: UUID,
    ): RefreshKnowledgeGapsResponse {
        return knowledgeGapsService.refreshKnowledgeGaps(projectId)
    }

    /**
     * Returns the owners currently assigned to a component.
     */
    @Operation(
        summary = "Get component owners",
        description = "Returns the users assigned as owners of the given component. PM/Admin only.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Component owners returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access endpoint"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/component-owners")
    @PreAuthorize(
        "hasAnyRole('ADMIN', 'PM') and @projectAuth.canAccessProject(authentication, #projectId)",
    )
    // `projectId` is referenced by the @PreAuthorize expression above, which detekt cannot see.
    // It does not reach the service: component ownership is keyed by component name alone and is
    // not yet project-partitioned, so two projects sharing a component name share its owners.
    // Scoping ComponentOwner is deliberately left out of #166 §4.
    @Suppress("UnusedParameter")
    fun getComponentOwners(
        @RequestParam projectId: UUID,
        @RequestParam component: String,
    ): List<KnowledgeGapOwnerResponse> {
        return knowledgeGapsService.getComponentOwners(component)
    }

    /**
     * Replaces the owners of a component and returns the resolved owners.
     */
    @Operation(
        summary = "Set component owners",
        description = "Assigns the owners of a component, replacing any previous assignment. PM/Admin only.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Component owners updated successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request body"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access endpoint"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/component-owners")
    @PreAuthorize(
        "hasAnyRole('ADMIN', 'PM') and @projectAuth.canAccessProject(authentication, #projectId)",
    )
    // `projectId` is referenced by the @PreAuthorize expression above, which detekt cannot see.
    // It does not reach the service: component ownership is keyed by component name alone and is
    // not yet project-partitioned, so two projects sharing a component name share its owners.
    // Scoping ComponentOwner is deliberately left out of #166 §4.
    @Suppress("UnusedParameter")
    fun setComponentOwners(
        @RequestParam projectId: UUID,
        @Valid @RequestBody request: SetComponentOwnersRequest,
    ): List<KnowledgeGapOwnerResponse> {
        return knowledgeGapsService.setComponentOwners(request)
    }
}
