package com.sprintstart.sprintstartbackend.insights.controller

import com.sprintstart.sprintstartbackend.insights.model.dto.response.FaqDetailResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.FaqOverviewResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.RefreshFaqResponse
import com.sprintstart.sprintstartbackend.insights.service.InsightsFaqService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * PM-only endpoints exposing recurring-question (FAQ) insights.
 *
 * All endpoints are restricted to project managers (and admins). Reads are served from the cached
 * grouping; the refresh endpoint triggers a recomputation via the AI service.
 */
@RestController
@RequestMapping("/api/v1/insights/faq")
@Tag(name = "Insights - FAQ", description = "PM insights into recurring questions")
class InsightsFaqController(
    private val insightsFaqService: InsightsFaqService,
) {
    /**
     * Returns the recurring-question groups sorted by frequency.
     */
    @Operation(
        summary = "Get recurring-question groups",
        description = "Returns FAQ groups ordered by descending occurrence count. PM/Admin only.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "FAQ groups returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access endpoint"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun getFaqOverview(): FaqOverviewResponse {
        return insightsFaqService.getFaqOverview()
    }

    /**
     * Returns the details of a single recurring-question group.
     */
    @Operation(
        summary = "Get a recurring-question group",
        description = "Returns sample questions and answering documents for a single group. PM/Admin only.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "FAQ group returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access endpoint"),
            ApiResponse(responseCode = "404", description = "No FAQ group found for the given id"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/{groupId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun getFaqGroup(
        @PathVariable groupId: UUID,
    ): FaqDetailResponse {
        return insightsFaqService.getFaqGroup(groupId)
    }

    /**
     * Recomputes the recurring-question groups via the AI service and replaces the cache.
     */
    @Operation(
        summary = "Refresh recurring-question groups",
        description = "Triggers AI grouping of recurring questions and rebuilds the cache. PM/Admin only.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "FAQ groups refreshed successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access endpoint"),
            ApiResponse(responseCode = "500", description = "The AI service failed to return a grouping result"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/refresh")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    suspend fun refreshFaqGroups(): RefreshFaqResponse {
        return insightsFaqService.refreshFaqGroups()
    }
}
