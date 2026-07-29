package com.sprintstart.sprintstartbackend.connectors.jira.controller

import com.sprintstart.sprintstartbackend.connectors.jira.model.api.request.credentials.AddCredentialRequest
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.request.credentials.ChangeJiraCredentialNameRequest
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.request.credentials.ChangeJiraCredentialTokenRequest
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.request.credentials.DeleteJiraCredentialRequest
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.response.credentials.JiraCredentialsDto
import com.sprintstart.sprintstartbackend.connectors.jira.service.JiraCredentialsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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

@RestController
@RequestMapping("/api/v1/jira/credentials")
internal class JiraCredentialsController(
    private val credentialsService: JiraCredentialsService,
) {
    /**
     * Adds a new Jira credential to the system.
     *
     * @param request The request containing the details of the Jira credential to be added.
     * @return A ResponseEntity with no content if the credential is successfully added.
     */
    @Operation(
        summary = "Adds a new Jira credential",
        description = "Adds a new Jira credential to the system.",
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
    fun addCredentials(@RequestBody @Valid request: AddCredentialRequest): ResponseEntity<Unit> {
        credentialsService.addCredentials(request)
        return ResponseEntity.noContent().build()
    }

    /**
     * Retrieves all Jira credentials associated with the specified user's email.
     *
     * @param userEmail the email address of the user whose Jira credentials are to be retrieved
     * @return a ResponseEntity containing a list of JiraCredentialsDto
     *         objects representing the user's credentials
     */
    @Operation(
        summary = "Gets all Jira credentials of a user",
        description = "Gets all Jira credentials of a user.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Retrieved all Jira credentials successfully.",
            ),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this endpoint"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/{userEmail}")
    @PreAuthorize("hasRole('PM') or hasRole('ADMIN')")
    fun getCredentialsOfUser(@PathVariable userEmail: String): ResponseEntity<List<JiraCredentialsDto>> {
        val response = credentialsService.getCredentialsOfUser(userEmail)
        return ResponseEntity.ok(response)
    }

    /**
     * Removes a Jira credential from the system.
     *
     * This operation deletes the specified Jira credential based on the given request details.
     *
     * @param request The details of the Jira credential to be removed. This must contain valid and
     *                 complete information.
     * @return A ResponseEntity with no content indicating successful removal of the credential.
     */
    @Operation(
        summary = "Removes a Jira credential",
        description = "Removes a Jira credential from the system.",
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
    fun removeCredential(@RequestBody @Valid request: DeleteJiraCredentialRequest): ResponseEntity<Unit> {
        credentialsService.removeCredential(request)
        return ResponseEntity.noContent().build()
    }

    /**
     * Updates the name of an existing Jira credential.
     *
     * @param request The request object containing the current Jira credential details and the new name.
     * @return A ResponseEntity containing the updated Jira credential details.
     */
    @Operation(
        summary = "Changes the name of a Jira credential",
        description = "Changes the name of a Jira credential.",
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
        @RequestBody @Valid request: ChangeJiraCredentialNameRequest,
    ): ResponseEntity<JiraCredentialsDto> {
        val response = credentialsService.changeCredentialName(request)
        return ResponseEntity.ok(response)
    }

    /**
     * Changes the token of a Jira credential.
     *
     * @param request The request object containing the details for changing the token of a Jira credential.
     * @return A ResponseEntity containing the updated Jira credential details.
     */
    @Operation(
        summary = "Changes the token of a Jira credential",
        description = "Changes the token of a Jira credential.",
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
        @RequestBody @Valid request: ChangeJiraCredentialTokenRequest,
    ): ResponseEntity<JiraCredentialsDto> {
        val response = credentialsService.changeCredentialToken(request)
        return ResponseEntity.ok(response)
    }
}
