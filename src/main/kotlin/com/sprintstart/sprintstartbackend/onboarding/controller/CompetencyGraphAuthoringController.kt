package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.request.competency.CreateCompetencyRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.competency.UpdateCompetencyRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.competency.CompetencyGraphResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.competency.CompetencyResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.competency.DeleteCompetencyResponse
import com.sprintstart.sprintstartbackend.onboarding.service.CompetencyGraphAuthoringService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * A PM authoring the live competency vocabulary: reading it, adding to it, correcting it, removing
 * from it.
 *
 * Reading is open to `HR` as well — the vocabulary is what every readout names — but changing it is
 * `ADMIN`/`PM`.
 */
@RestController
@RequestMapping("/api/v1/onboarding/competency-graph")
@Tag(
    name = "Onboarding - Competency Graph Authoring",
    description = "Read, add, edit and remove competencies",
)
class CompetencyGraphAuthoringController(
    private val competencyGraphAuthoringService: CompetencyGraphAuthoringService,
) {
    /**
     * Reads the whole live vocabulary, for the PM authoring surface.
     */
    @Operation(
        summary = "Read the live competency vocabulary",
        description = "Returns every live competency as a flat list, carrying no per-user state",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Graph returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun getGraph(): CompetencyGraphResponse {
        return competencyGraphAuthoringService.getGraph()
    }

    /**
     * Reads one live competency's full authoring detail.
     */
    @Operation(
        summary = "Read a live competency",
        description = "Returns one live competency's full authoring detail, including description and target level",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Competency returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No live competency found with the given key"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/competencies/{key}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun getCompetency(@PathVariable key: String): CompetencyResponse {
        return competencyGraphAuthoringService.getCompetency(key)
    }

    /**
     * Creates a hand-authored competency.
     *
     * Returns the created competency, whose `key` may differ from what was sent -- it is slugified
     * into the vocabulary's house style.
     */
    @Operation(
        summary = "Create a competency by hand",
        description = "Adds a new competency to the live vocabulary",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Competency created"),
            ApiResponse(responseCode = "400", description = "Blank key or label, or target level outside 1..4"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "409", description = "A competency with this key already exists"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/competencies")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun createCompetency(@RequestBody request: CreateCompetencyRequest): CompetencyResponse {
        return competencyGraphAuthoringService.createCompetency(request)
    }

    /**
     * Applies a PM's edit to a live competency node.
     *
     * The competency's `key` is not editable — it is the identity every ledger row and module
     * points at. The label is what a PM renames.
     */
    @Operation(
        summary = "Edit a live competency",
        description = "Updates a live competency's label, description, kind or target level",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Competency updated"),
            ApiResponse(responseCode = "400", description = "Target level outside 1..4, or blank label"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No live competency found with the given key"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/competencies/{key}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun updateCompetency(
        @PathVariable key: String,
        @RequestBody request: UpdateCompetencyRequest,
    ): CompetencyResponse {
        return competencyGraphAuthoringService.updateCompetency(key, request)
    }

    /**
     * Deletes a competency from the live vocabulary.
     *
     * Nobody loses a competency they already earned, and no authored module is destroyed: both are
     * keyed by the competency *key* rather than by a foreign key, so both survive the row going.
     * A module simply stops appearing until a competency with that key exists again.
     */
    @Operation(
        summary = "Remove a competency",
        description = "Deletes a competency; earned ledger entries and authored modules are kept",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Competency removed"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No live competency found with the given key"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @DeleteMapping("/competencies/{key}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun deleteCompetency(@PathVariable key: String): DeleteCompetencyResponse {
        return competencyGraphAuthoringService.deleteCompetency(key)
    }
}
