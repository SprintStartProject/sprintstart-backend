package com.sprintstart.sprintstartbackend.offboarding.controller

import com.sprintstart.sprintstartbackend.offboarding.model.mapper.toIdentitiesResponse
import com.sprintstart.sprintstartbackend.offboarding.model.mapper.toResponse
import com.sprintstart.sprintstartbackend.offboarding.model.request.LinkIdentitiesRequest
import com.sprintstart.sprintstartbackend.offboarding.model.request.parseIdentity
import com.sprintstart.sprintstartbackend.offboarding.model.response.AliasLinkResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.IdentitiesResponse
import com.sprintstart.sprintstartbackend.offboarding.service.IdentityLinkService
import com.sprintstart.sprintstartbackend.offboarding.service.SeenIdentityService
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * PM-only management of which identities in a project's evidence are one person.
 *
 * Evidence names people by a commit email or a login, and one developer has several. Until a manager
 * links them, each counts as its own person and the risk map cannot tell what a departure really costs.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/offboarding/identities")
@Tag(name = "Offboarding", description = "Knowledge at risk when people leave a project")
class OffboardingIdentityController(
    private val identityLinkService: IdentityLinkService,
    private val seenIdentityService: SeenIdentityService,
) {
    /**
     * Lists the confirmed links and every identity the evidence names.
     */
    @Operation(
        summary = "List the identities in a project's evidence",
        description = "Returns the confirmed links and each email or login the commits and reviews name, with " +
            "who it counts as and how much evidence it holds. Identities that are not linked to anyone count " +
            "as their own person. PM/Admin of the project only.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Identities returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Not a manager of this project"),
        ],
    )
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PM') and @projectAuth.canManageProject(authentication, #projectId)")
    fun identities(
        @PathVariable projectId: ProjectId,
        @RequestParam(required = false) asOf: Instant?,
    ): IdentitiesResponse =
        toIdentitiesResponse(identityLinkService.links(projectId), seenIdentityService.seen(projectId, asOf))

    /**
     * Confirms that two identities are one person.
     */
    @Operation(
        summary = "Link two identities as one person",
        description = "Confirms that the alias and the person are the same developer. Linking an identity that " +
            "is already linked merges the people. Linking identities that already count as one changes nothing. " +
            "PM/Admin of the project only.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "The project's links after the change"),
            ApiResponse(responseCode = "400", description = "An identity is blank"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Not a manager of this project"),
        ],
    )
    @PutMapping("/links")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM') and @projectAuth.canManageProject(authentication, #projectId)")
    fun link(
        @PathVariable projectId: ProjectId,
        @RequestBody request: LinkIdentitiesRequest,
    ): List<AliasLinkResponse> {
        val link = request.toLink()
        return identityLinkService.link(projectId, link.alias, link.person).map { it.toResponse() }
    }

    /**
     * Takes back the link that folds an identity into a person.
     */
    @Operation(
        summary = "Unlink an identity",
        description = "Removes the link that folds the alias into a person; the alias counts as its own person " +
            "again. PM/Admin of the project only.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Link removed"),
            ApiResponse(responseCode = "400", description = "The alias is blank"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Not a manager of this project"),
            ApiResponse(responseCode = "404", description = "The alias is not linked"),
        ],
    )
    @DeleteMapping("/links")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM') and @projectAuth.canManageProject(authentication, #projectId)")
    fun unlink(
        @PathVariable projectId: ProjectId,
        @RequestParam alias: String,
    ) = identityLinkService.unlink(projectId, parseIdentity(alias))
}
