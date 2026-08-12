package com.sprintstart.sprintstartbackend.user.controller

import com.sprintstart.sprintstartbackend.user.model.request.skill.CreateSkillRequest
import com.sprintstart.sprintstartbackend.user.model.request.skill.UpdateSkillRequest
import com.sprintstart.sprintstartbackend.user.model.response.skill.CreateSkillResponse
import com.sprintstart.sprintstartbackend.user.model.response.skill.GetSkillResponse
import com.sprintstart.sprintstartbackend.user.model.response.skill.UpdateSkillResponse
import com.sprintstart.sprintstartbackend.user.service.SkillService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST Controller for browsing the skill catalog.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Skills", description = "Endpoints for browsing the skill catalog")
class SkillController(
    private val skillService: SkillService,
) {
    /**
     * Retrieves all available skills.
     *
     * @return List of all skills.
     */
    @Operation(summary = "Get all skills", description = "Retrieves a list of all available skills.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Skills returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
        ],
    )
    @GetMapping("/skills")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR', 'USER')")
    fun getAllSkills(): List<GetSkillResponse> {
        return skillService.getAllSkills()
    }

    @Operation(summary = "Get skill by ID", description = "Retrieves a skill by its unique identifier.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Skill returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "Skill not found"),
        ],
    )
    @GetMapping("/skills/{skillId}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR', 'USER')")
    fun getSkillById(
        @Parameter(description = "UUID of the skill") @PathVariable skillId: UUID,
    ): GetSkillResponse {
        return skillService.getSkillById(skillId)
    }
}

/**
 * REST Controller for administering the skill catalog.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Skills Admin", description = "Endpoints for administering the skill catalog")
class SkillAdminController(
    private val skillService: SkillService,
) {
    /**
     * Creates a new skill.
     *
     * Creates a new active skill, or reactivates a retired skill with the same normalized name.
     *
     * When a retired skill is reactivated through this endpoint, its role assignments are refreshed
     * from the request instead of creating a duplicate row.
     *
     * @param request The request containing the skill details.
     * @return The created or reactivated skill.
     */
    @Operation(
        summary = "Create skill",
        description = "Creates a new skill with the given details. If a retired skill with the same normalized " +
            "name already exists, that skill is reactivated and updated instead.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Skill created successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "Project role not found"),
            ApiResponse(responseCode = "409", description = "Skill with same name already exists"),
        ],
    )
    @PostMapping("/skills")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun createSkill(
        @RequestBody request: CreateSkillRequest,
    ): CreateSkillResponse {
        return skillService.createSkill(request)
    }

    @Operation(summary = "Update skill", description = "Updates editable fields of an existing skill.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Skill updated successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "Skill or project role not found"),
            ApiResponse(responseCode = "409", description = "Skill with same name already exists"),
        ],
    )
    @PatchMapping("/skills/{skillId}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun updateSkill(
        @Parameter(description = "UUID of the skill to update") @PathVariable skillId: UUID,
        @RequestBody request: UpdateSkillRequest,
    ): UpdateSkillResponse {
        return skillService.updateSkill(skillId, request)
    }

    /**
     * Retires a skill by its ID. The skill is not physically deleted; it is marked RETIRED so it
     * can no longer be assigned to roles.
     *
     * @param skillId The UUID of the skill to be retired.
     */
    @Operation(
        summary = "Retire skill",
        description = "Marks a skill as retired. Retired skills can no longer be assigned to roles.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Skill retired successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "Skill not found"),
        ],
    )
    @DeleteMapping("/skills/{skillId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun retireSkill(
        @Parameter(description = "UUID of the skill to retire") @PathVariable skillId: UUID,
    ) {
        skillService.retireSkill(skillId)
    }
}
