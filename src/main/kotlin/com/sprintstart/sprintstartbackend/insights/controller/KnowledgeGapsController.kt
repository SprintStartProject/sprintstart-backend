package com.sprintstart.sprintstartbackend.insights.controller

import com.sprintstart.sprintstartbackend.insights.model.dto.request.SetComponentOwnersRequest
import com.sprintstart.sprintstartbackend.insights.model.dto.response.KnowledgeGapOwnerResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.KnowledgeGapResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.KnowledgeGapsOverviewResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.RefreshKnowledgeGapsResponse
import com.sprintstart.sprintstartbackend.insights.service.KnowledgeGapsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
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
 * PM-only endpoints exposing knowledge gaps (components missing runbooks/ADRs).
 *
 * All endpoints are restricted to project managers (and admins). Reads are served from the cached
 * classification; the refresh endpoint triggers a reclassification via the AI service.
 */
@RestController
@RequestMapping("/api/v1/insights/knowledge-gaps")
@Tag(name = "Insights - Knowledge Gaps", description = "PM insights into components missing documentation")
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
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun getKnowledgeGaps(): KnowledgeGapsOverviewResponse {
        return knowledgeGapsService.getKnowledgeGaps()
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
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun getKnowledgeGap(
        @PathVariable gapId: UUID,
    ): KnowledgeGapResponse {
        return knowledgeGapsService.getKnowledgeGap(gapId)
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
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    suspend fun refreshKnowledgeGaps(): RefreshKnowledgeGapsResponse {
        return knowledgeGapsService.refreshKnowledgeGaps()
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
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun getComponentOwners(
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
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun setComponentOwners(
        @Valid @RequestBody request: SetComponentOwnersRequest,
    ): List<KnowledgeGapOwnerResponse> {
        return knowledgeGapsService.setComponentOwners(request)
    }
}
