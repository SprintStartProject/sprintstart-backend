package com.sprintstart.sprintstartbackend.connectors.atlassian.controller

import com.sprintstart.sprintstartbackend.connectors.atlassian.model.api.request.AddAtlassianCredentialRequest
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.api.request.ChangeAtlassianCredentialNameRequest
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.api.request.ChangeAtlassianCredentialTokenRequest
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.api.request.DeleteAtlassianCredentialRequest
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.api.response.AtlassianCredentialDto
import com.sprintstart.sprintstartbackend.connectors.atlassian.service.AtlassianCredentialService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/atlassian/credentials")
internal class AtlassianCredentialController(
    private val credentialsService: AtlassianCredentialService,
) {
    /**
     * Adds a new Atlassian credential to the system.
     *
     * @param request The request containing the details of the Atlassian credential to be added.
     * @return A ResponseEntity with no content if the credential is successfully added.
     */
    @Operation(
        summary = "Adds a new Atlassian credential",
        description = "Adds a new Atlassian credential to the system.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204",
                description = "Credential was successfully added.",
            ),
            ApiResponse(responseCode = "400", description = "A credential with the same name already exists."),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this endpoint"),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping
    @PreAuthorize("hasRole('PM') or hasRole('ADMIN')")
    fun addCredentials(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @RequestBody @Valid request: AddAtlassianCredentialRequest,
    ): ResponseEntity<Unit> {
        credentialsService.addCredentials(jwt.subject, request)
        return ResponseEntity.noContent().build()
    }

    /**
     * Retrieves all Atlassian credentials associated with the specified user's email.
     *
     * @return a ResponseEntity containing a list of AtlassianCredentialDto
     *         objects representing the user's credentials
     */
    @Operation(
        summary = "Gets all Atlassian credentials of a user",
        description = "Gets all Atlassian credentials of a user.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Retrieved all Atlassian credentials successfully.",
            ),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this endpoint"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping
    @PreAuthorize("hasRole('PM') or hasRole('ADMIN')")
    fun getCredentialsOfUser(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<List<AtlassianCredentialDto>> {
        val response = credentialsService.getCredentialsOfUser(jwt.subject)
        return ResponseEntity.ok(response)
    }

    /**
     * Removes an Atlassian credential from the system.
     *
     * This operation deletes the specified Atlassian credential based on the given request details.
     *
     * @param request The details of the Atlassian credential to be removed. This must contain valid and
     *                 complete information.
     * @return A ResponseEntity with no content indicating successful removal of the credential.
     */
    @Operation(
        summary = "Removes an Atlassian credential",
        description = "Removes an Atlassian credential from the system.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204",
                description = "Credential was successfully removed.",
            ),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this endpoint"),
            ApiResponse(responseCode = "404", description = "Credential to delete not found"),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping
    @PreAuthorize("hasRole('PM') or hasRole('ADMIN')")
    fun removeCredential(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @RequestBody @Valid request: DeleteAtlassianCredentialRequest,
    ): ResponseEntity<Unit> {
        credentialsService.removeCredential(jwt.subject, request)
        return ResponseEntity.noContent().build()
    }

    /**
     * Updates the name of an existing Atlassian credential.
     *
     * @param request The request object containing the current Atlassian credential details and the new name.
     * @return A ResponseEntity containing the updated Atlassian credential details.
     */
    @Operation(
        summary = "Changes the name of an Atlassian credential",
        description = "Changes the name of an Atlassian credential.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Credential name was successfully changed.",
            ),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this endpoint"),
            ApiResponse(responseCode = "404", description = "Credential to rename not found"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PatchMapping("/patch/name")
    @PreAuthorize("hasRole('PM') or hasRole('ADMIN')")
    fun changeCredentialName(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @RequestBody @Valid request: ChangeAtlassianCredentialNameRequest,
    ): ResponseEntity<AtlassianCredentialDto> {
        val response = credentialsService.changeCredentialName(jwt.subject, request)
        return ResponseEntity.ok(response)
    }

    /**
     * Changes the token of an Atlassian credential.
     *
     * @param request The request object containing the details for changing the token of an Atlassian credential.
     * @return A ResponseEntity containing the updated Atlassian credential details.
     */
    @Operation(
        summary = "Changes the token of an Atlassian credential",
        description = "Changes the token of an Atlassian credential.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Token was successfully changed.",
            ),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this endpoint"),
            ApiResponse(responseCode = "404", description = "Credential to change token of not found"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PatchMapping("/patch/token")
    @PreAuthorize("hasRole('PM') or hasRole('ADMIN')")
    fun changeCredentialToken(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @RequestBody @Valid request: ChangeAtlassianCredentialTokenRequest,
    ): ResponseEntity<AtlassianCredentialDto> {
        val response = credentialsService.changeCredentialToken(jwt.subject, request)
        return ResponseEntity.ok(response)
    }
}
