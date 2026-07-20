package com.sprintstart.sprintstartbackend.connectors.github.controller

import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.ConfigureRepositoryRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.GetRepositoryConfigRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.GetRepositoryConfigResponse
import com.sprintstart.sprintstartbackend.connectors.github.service.GithubRepositoryConfigService
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

@Validated
@Tag(
    name = "Github config management",
    description = "Endpoints for configuring the behaviour of the GitHub connector",
)
@RestController
@RequestMapping("/api/v1/github/config")
class GithubRepositoryConfigController(
    private val configService: GithubRepositoryConfigService,
) {
    /**
     * Configures the update behavior for all repositories globally.
     *
     * This endpoint allows administrators or project managers to define a common configuration for repository updates.
     *
     * @param request The configuration details encapsulated in a {@link ConfigureRepositoryRequest} object.
     * @return A ResponseEntity with no content, indicating that the global configuration was successfully updated.
     */
    @Operation(
        summary = "Configure update behaviour for all repositories",
        description = "Allows configuring the update behaviour once for all repositories",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204",
                description = "Configuration was successfully updated.",
            ),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this endpoint"),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PutMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('PM')")
    fun configureAll(
        @Valid @RequestBody request: ConfigureRepositoryRequest,
    ): ResponseEntity<Unit> {
        configService.configureAll(request)
        return ResponseEntity.noContent().build()
    }

    /**
     * Retrieves all repository update configurations.
     *
     * @return ResponseEntity containing a list of repository configuration responses.
     */
    @Operation(
        summary = "Retrieve all repository configs",
        description = "Allows retrieval of all repository update configurations",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Retrieved all repository configs successfully.",
            ),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this endpoint"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('PM')")
    fun getAll(): ResponseEntity<List<GetRepositoryConfigResponse>> {
        val result = configService.getAll()
        return ResponseEntity.ok(result)
    }

    /**
     * Configures the update behavior for a specific repository.
     *
     * This method enables administrators or project managers to set the update configuration
     * for a repository identified by its owner and name.
     *
     * @param owner The username of the repository owner.
     * @param name The name of the repository.
     * @param request The configuration details encapsulated in a ConfigureRepositoryRequest object.
     * @return A ResponseEntity with no content, indicating that the repository configuration was successfully updated.
     */
    @Operation(
        summary = "Configure update behaviour of a given repository",
        description = "Allows configuring the update behaviour for one specific repository",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204",
                description = "Configuration was successfully updated.",
            ),
            ApiResponse(responseCode = "400", description = "Repository with given owner and name not found"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this endpoint"),
            ApiResponse(
                responseCode = "404",
                description = "Config for repository with given owner and name not found",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PutMapping("/{owner}/{name}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PM')")
    fun configureRepository(
        @PathVariable owner: String,
        @PathVariable name: String,
        @Valid @RequestBody request: ConfigureRepositoryRequest,
    ): ResponseEntity<Unit> {
        configService.configure(owner, name, request)
        return ResponseEntity.noContent().build()
    }

    /**
     * Retrieves the configuration of a specific repository.
     *
     * This method allows authorized users to fetch the currently active update configuration
     * for a given repository, identified by its owner and name.
     *
     * @param owner The username of the repository owner.
     * @param name The name of the repository.
     * @return A ResponseEntity containing the configuration details wrapped in a GetRepositoryConfigResponse,
     *         or an appropriate error response code if the repository or its configuration cannot be found.
     */
    @Operation(
        summary = "Retrieves the configuration of a given repository",
        description = "Allows retrieval of the currently active repository update configuration",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Configuration was successfully retrieved.",
            ),
            ApiResponse(responseCode = "400", description = "Repository with given owner and name not found"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this endpoint"),
            ApiResponse(
                responseCode = "404",
                description = "Config for repository with given owner and name not found",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/{owner}/{name}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('PM')")
    fun getConfigOfRepository(
        @PathVariable owner: String,
        @PathVariable name: String,
    ): ResponseEntity<GetRepositoryConfigResponse> {
        val result = configService.getConfigOfRepository(GetRepositoryConfigRequest(owner, name))
        return ResponseEntity.ok(result)
    }
}
