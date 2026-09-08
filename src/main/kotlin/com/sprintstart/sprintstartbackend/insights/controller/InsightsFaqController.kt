package com.sprintstart.sprintstartbackend.insights.controller

import com.sprintstart.sprintstartbackend.insights.model.dto.request.FaqRebuildScope
import com.sprintstart.sprintstartbackend.insights.model.dto.response.FaqDetailResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.FaqOverviewResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.FaqRebuildPreviewResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.RefreshFaqResponse
import com.sprintstart.sprintstartbackend.insights.service.InsightsFaqService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
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
    @PreAuthorize(
        "hasAnyRole('ADMIN', 'PM') and @projectAuth.canAccessProject(authentication, #projectId)",
    )
    fun getFaqOverview(
        @RequestParam projectId: UUID,
    ): FaqOverviewResponse {
        return insightsFaqService.getFaqOverview(projectId)
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
    @PreAuthorize(
        "hasAnyRole('ADMIN', 'PM') and @projectAuth.canAccessProject(authentication, #projectId)",
    )
    fun getFaqGroup(
        @RequestParam projectId: UUID,
        @PathVariable groupId: UUID,
    ): FaqDetailResponse {
        return insightsFaqService.getFaqGroup(projectId, groupId)
    }

    /**
     * Recomputes the recurring-question groups via the AI service and replaces the cache.
     */
    @Operation(
        summary = "Refresh recurring-question groups",
        description = "Regroups the project's questions from scratch and replaces the stored " +
            "entries. Destructive: whatever falls outside the requested scope is gone from the " +
            "counts afterwards, and the surviving entries get new ids. PM/Admin only.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "FAQ groups refreshed successfully"),
            ApiResponse(responseCode = "400", description = "A scope bound was not a positive number"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access endpoint"),
            ApiResponse(responseCode = "500", description = "The AI service failed to return a usable grouping"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/refresh")
    @PreAuthorize(
        "hasAnyRole('ADMIN', 'PM') and @projectAuth.canAccessProject(authentication, #projectId)",
    )
    suspend fun refreshFaqGroups(
        @RequestParam projectId: UUID,
        @Parameter(description = "At most this many questions, newest first. Never above the configured ceiling.")
        @RequestParam(required = false) questionLimit: Int? = null,
        @Parameter(description = "Only questions asked within this many days.")
        @RequestParam(required = false) sinceDays: Int? = null,
    ): RefreshFaqResponse {
        return insightsFaqService.refreshFaqGroups(
            projectId,
            FaqRebuildScope(questionLimit = questionLimit, sinceDays = sinceDays),
        )
    }

    /**
     * Reports how much material a rebuild would have, per requested time window.
     */
    @Operation(
        summary = "Preview what a rebuild would cover",
        description = "Returns the project's question count and, per requested window, how many " +
            "questions a rebuild scoped to it would send. Lets a client show the trade-off " +
            "before a scope is chosen, since a rebuild drops whatever it does not cover. " +
            "PM/Admin only.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Preview returned successfully"),
            ApiResponse(responseCode = "400", description = "A window was not a positive number of days"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access endpoint"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/rebuild-preview")
    @PreAuthorize(
        "hasAnyRole('ADMIN', 'PM') and @projectAuth.canAccessProject(authentication, #projectId)",
    )
    fun previewRebuild(
        @RequestParam projectId: UUID,
        @Parameter(description = "Window lengths in days. Repeat the parameter for several.")
        @RequestParam(required = false) sinceDays: List<Int>?,
    ): FaqRebuildPreviewResponse {
        return insightsFaqService.previewRebuild(projectId, sinceDays.orEmpty())
    }
}
