package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.controller

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.request.ConfigureBitbucketRepositoryRequest
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.response.GetBitbucketRepositoryConfigResponse
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.BitbucketRepositoryConfigService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Exposes reading and configuring the update schedule of connected Bitbucket repositories. */
@Validated
@Tag(
    name = "Bitbucket config management",
    description = "Endpoints for configuring the behaviour of the Bitbucket connector",
)
@RestController
@RequestMapping("/api/v1/bitbucket/config")
internal class BitbucketRepositoryConfigController(
    private val configService: BitbucketRepositoryConfigService,
) {
    /**
     * Configures the update behaviour of every connected repository at once.
     *
     * @param request The schedule and auto-update flag to apply to all repositories.
     * @return 204 with an empty body.
     */
    @Operation(
        summary = "Configure update behaviour for all repositories",
        description = "Applies one update behaviour to every connected Bitbucket repository",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Configuration was successfully updated."),
            ApiResponse(responseCode = "400", description = "Request body is invalid"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this endpoint"),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PutMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('PM')")
    fun configureAll(@Valid @RequestBody request: ConfigureBitbucketRepositoryRequest): ResponseEntity<Unit> {
        configService.configureAll(request)
        return ResponseEntity.noContent().build()
    }

    /**
     * Retrieves the update configuration of every connected repository.
     *
     * @return 200 with the list of configurations.
     */
    @Operation(
        summary = "Retrieve all repository configs",
        description = "Returns the update configuration of every connected Bitbucket repository",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Configs were successfully retrieved."),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this endpoint"),
        ],
    )
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('PM')")
    fun getAll(): ResponseEntity<List<GetBitbucketRepositoryConfigResponse>> =
        ResponseEntity.ok(configService.getAll())

    /**
     * Configures the update behaviour of one repository.
     *
     * @param workspace The Bitbucket workspace the repository belongs to.
     * @param slug The repository slug.
     * @param request The schedule and auto-update flag to apply.
     * @return 204 with an empty body.
     */
    @Operation(
        summary = "Configure update behaviour of a given repository",
        description = "Applies an update behaviour to one connected Bitbucket repository",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Configuration was successfully updated."),
            ApiResponse(responseCode = "400", description = "Repository not connected or request body is invalid"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this endpoint"),
            ApiResponse(responseCode = "404", description = "Config for the repository was not found"),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PutMapping("/{workspace}/{slug}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PM')")
    fun configureRepository(
        @PathVariable workspace: String,
        @PathVariable slug: String,
        @Valid @RequestBody request: ConfigureBitbucketRepositoryRequest,
    ): ResponseEntity<Unit> {
        configService.configure(workspace, slug, request)
        return ResponseEntity.noContent().build()
    }

    /**
     * Retrieves the update configuration of one repository.
     *
     * @param workspace The Bitbucket workspace the repository belongs to.
     * @param slug The repository slug.
     * @return 200 with the repository's configuration.
     */
    @Operation(
        summary = "Retrieve the configuration of a given repository",
        description = "Returns the currently active update configuration of one connected repository",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Configuration was successfully retrieved."),
            ApiResponse(responseCode = "400", description = "Repository with the given coordinates is not connected"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this endpoint"),
            ApiResponse(responseCode = "404", description = "Config for the repository was not found"),
        ],
    )
    @GetMapping("/{workspace}/{slug}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PM')")
    fun getConfigOfRepository(
        @PathVariable workspace: String,
        @PathVariable slug: String,
    ): ResponseEntity<GetBitbucketRepositoryConfigResponse> =
        ResponseEntity.ok(configService.getConfigOfRepository(workspace, slug))
}
