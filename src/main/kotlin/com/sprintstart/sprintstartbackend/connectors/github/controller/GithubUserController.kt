package com.sprintstart.sprintstartbackend.connectors.github.controller

import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.AddPatRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.RemovePatRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.UpdatePatNameRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.UpdatePatRequest
import com.sprintstart.sprintstartbackend.connectors.github.service.GithubUserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(
    name = "Github User Management",
    description = "Endpoints for managing personal access tokens (PATs) associated with a user.",
)
@RestController
@RequestMapping("/api/v1/github/pat")
@Validated
class GithubUserController(
    private val githubUserService: GithubUserService,
) {
    /**
     * Retrieves all personal access tokens (PATs) names associated with the authenticated user.
     *
     * @param jwt The authentication principal containing the user's JWT.
     * @return A ResponseEntity containing a list of PAT Strings.
     */
    @Operation(
        summary = "Retrieve all personal access tokens (PATs) names.",
        description = "Retrieves all personal access tokens (PATs) associated with the authenticated user.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Retrieving all PATs was successful.",
            ),
        ],
    )
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('USER')")
    fun getAllPats(
        @Parameter(hidden = true)
        @AuthenticationPrincipal
        jwt: Jwt,
    ): ResponseEntity<List<String>> {
        val pats = githubUserService.getAllPATNames(jwt.subject)
        return ResponseEntity.ok(pats)
    }

    /**
     * Adds a new personal access token (PAT) to the authenticated user's account.
     *
     * Given a name and a token, this method adds a new personal access token (PAT) to the database.
     * If a PAT with the same name already exists, an appropriate error response is returned.
     *
     * @param jwt The JSON Web Token of the authenticated user, used to identify the user.
     * @param request The request object containing details of the PAT to be added.
     * @return A ResponseEntity indicating the HTTP status of the request. Returns HTTP 201 if successful.
     */
    @Operation(
        summary = "Adds a new personal access token (PAT).",
        description = "Adds a new personal access token (PAT) to the authenticated user's account.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "PAT addition was successful.",
            ),
            ApiResponse(responseCode = "400", description = "PAT with given name already exists"),
        ],
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('USER')")
    fun addPat(
        @Parameter(hidden = true)
        @AuthenticationPrincipal
        jwt: Jwt,
        @Valid
        @RequestBody
        request: AddPatRequest,
    ): ResponseEntity<Unit> {
        githubUserService.addPAT(jwt.subject, request)
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    /**
     * Updates a personal access token (PAT) associated with the authenticated user.
     *
     * Given the name of the PAT to update, this method updates the PAT in the database.
     * If the PAT is not found, an appropriate error response is returned.
     *
     * @param jwt the JWT token of the authenticated user, extracted from the security context.
     * @param request the request body containing details for updating the personal access token.
     * @return a ResponseEntity with an empty body and a status indicating whether the operation was successful.
     */
    @Operation(
        summary = "Updates a personal access token (PAT)",
        description = "Updates a personal access token (PAT) associated with the authenticated user.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "PAT update was successful.",
            ),
            ApiResponse(responseCode = "404", description = "PAT to update not found"),
        ],
    )
    @PutMapping("/update")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('USER')")
    fun updatePat(
        @Parameter(hidden = true)
        @AuthenticationPrincipal
        jwt: Jwt,
        @Valid
        @RequestBody
        request: UpdatePatRequest,
    ): ResponseEntity<Unit> {
        githubUserService.updatePAT(jwt.subject, request)
        return ResponseEntity.ok().build()
    }

    /**
     * Updates the name of an existing personal access token (PAT) associated with the authenticated user.
     *
     * Given the old PAT name, this method updates the PAT name in the database if a PAT with the given old name exists.
     * If the PAT is not found, an according error response is returned.
     *
     * @param jwt the authentication principal containing the user's JWT, used to identify the authenticated user.
     * @param request the request object containing the new name for the personal access token (PAT) to be updated.
     * @return a response entity with no content, indicating the PAT name update was successful.
     */
    @Operation(
        summary = "Adds the name of a new personal access token (PAT).",
        description = "Adds the name of a new personal access token (PAT) to the authenticated user's account.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "PAT update was successful.",
            ),
            ApiResponse(responseCode = "404", description = "PAT to update not found"),
        ],
    )
    @PutMapping("/update/name")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('USER')")
    fun updatePatName(
        @Parameter(hidden = true)
        @AuthenticationPrincipal
        jwt: Jwt,
        @Valid
        @RequestBody
        request: UpdatePatNameRequest,
    ): ResponseEntity<Unit> {
        githubUserService.updatePATName(jwt.subject, request)
        return ResponseEntity.ok().build()
    }

    /**
     * Deletes a personal access token (PAT) associated with the authenticated user.
     *
     * The method requires the authenticated user to have the 'USER' role. Upon successful
     * execution, the specified PAT will be deleted. If the PAT cannot be found or is still in use,
     * appropriate error responses are returned.
     *
     * @param jwt The JWT token representing the authenticated user.
     * @param request The request object containing the details of the PAT to be removed.
     * @return A ResponseEntity with an HTTP status of 200 if the deletion is successful.
     */
    @Operation(
        summary = "Deletes a personal access token (PAT)",
        description = "Deletes a personal access token (PAT) associated with the authenticated user.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "PAT deletion was successful.",
            ),
            ApiResponse(responseCode = "400", description = "PAT is still in use"),
            ApiResponse(responseCode = "404", description = "PAT to delete not found"),
        ],
    )
    @PutMapping("/delete")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('USER')")
    fun deletePat(
        @Parameter(hidden = true)
        @AuthenticationPrincipal
        jwt: Jwt,
        @Valid
        @RequestBody
        request: RemovePatRequest,
    ): ResponseEntity<Unit> {
        githubUserService.removePAT(jwt.subject, request)
        return ResponseEntity.ok().build()
    }
}
