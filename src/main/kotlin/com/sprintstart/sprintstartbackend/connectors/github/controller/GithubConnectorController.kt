package com.sprintstart.sprintstartbackend.connectors.github.controller

import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.ConnectRepositoriesRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.ConnectRepositoryRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.DiscoverRepositoriesRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.UpdateRepositoryRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.AddRepositoryToProjectResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.ConnectRepositoriesResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.ConnectRepositoryResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.DiscoverRepositoriesResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.RemoveRepositoryFromProjectResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.UpdateAllRepositoriesResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.UpdateRepositoryResponse
import com.sprintstart.sprintstartbackend.connectors.github.service.GithubConnectorService
import com.sprintstart.sprintstartbackend.connectors.github.service.GithubRepositoryConnectionOrchestrator
import com.sprintstart.sprintstartbackend.connectors.github.service.GithubRepositoryProjectService
import com.sprintstart.sprintstartbackend.connectors.github.service.GithubUpdatesService
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
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(
    name = "Github Connector",
    description = "Endpoints for interacting with the Github connector",
)
@RestController
@RequestMapping("/api/v1/github")
@Validated
internal class GithubConnectorController(
    private val githubConnectorService: GithubConnectorService,
    private val githubUpdateService: GithubUpdatesService,
    private val connectionOrchestrator: GithubRepositoryConnectionOrchestrator,
    private val githubRepositoryProjectService: GithubRepositoryProjectService,
) {
    /**
     * Discovers the GitHub repositories of a specified organization.
     *
     * This function retrieves all repositories of a GitHub organization to which the user
     * has access using the provided Personal Access Token (PAT) stored in SprintStart.
     *
     * @param jwt The authentication principal extracted from the user's JWT token (hidden parameter).
     * @param org The name of the GitHub organization whose repositories are to be discovered.
     * @param tokenName The name of the stored PAT to be used for accessing the organization repositories.
     * @param page The page number for paginated results. Defaults to 0 if not specified.
     * @param pageSize The number of repositories per page. Defaults to 20 if not specified.
     * @return A ResponseEntity containing a DiscoverRepositoriesResponse object with the list
     *         of discovered repositories and pagination details.
     */
    @Operation(
        summary = "Discover GitHub repositories of a org",
        description = """
            Given a GitHub organization name and the name of a PAT that is stored in SprintStart, this function discovers all 
            GitHub repositories that the user has access to with the given PAT.
            """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Discovery successful. Returns a list of DiscoveredRepository objects.",
            ),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this endpoint"),
            ApiResponse(responseCode = "404", description = "PAT with given name not found"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/discover/org/{org}")
    @PreAuthorize("hasRole('PM') or hasRole('ADMIN')")
    suspend fun discoverRepositoriesOfOrg(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @PathVariable org: String,
        @RequestParam(required = true) tokenName: String,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") pageSize: Int,
    ): ResponseEntity<DiscoverRepositoriesResponse> {
        val request = DiscoverRepositoriesRequest(org, jwt.subject, tokenName, page, pageSize)
        val response = githubConnectorService.discoverRepositoriesOfOrg(request)
        return ResponseEntity.ok(response)
    }

    /**
     * Discovers all GitHub repositories accessible to the specified user using a stored Personal Access Token (PAT).
     *
     * @param jwt The authentication principal representing the JSON Web Token of the current user. This is injected
     * automatically.
     * @param user The GitHub username whose repositories are to be discovered.
     * @param tokenName The name of the stored PAT that will be used for authenticating with the GitHub API.
     * @param page The page number for paginated results. Defaults to 0.
     * @param pageSize The number of repositories to include per page in the paginated results. Defaults to 20.
     * @return A ResponseEntity containing the DiscoverRepositoriesResponse, which includes the list of discovered
     * repositories.
     */
    @Operation(
        summary = "Discover GitHub repositories of a user",
        description = """
            Given a GitHub username and the name of a PAT that is stored in SprintStart, this function discovers all 
            GitHub repositories that the user has access to with the given PAT.
            """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Discovery successful. Returns a list of DiscoveredRepository objects.",
            ),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this endpoint"),
            ApiResponse(responseCode = "404", description = "PAT with given name not found"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/discover/user/{user}")
    @PreAuthorize("hasRole('PM') or hasRole('ADMIN')")
    suspend fun discoverRepositoriesOfUser(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @PathVariable user: String,
        @RequestParam(required = true) tokenName: String,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") pageSize: Int,
    ): ResponseEntity<DiscoverRepositoriesResponse> {
        val request = DiscoverRepositoriesRequest(user, jwt.subject, tokenName, page, pageSize)
        val response = githubConnectorService.discoverRepositoriesOfUser(request)
        return ResponseEntity.ok(response)
    }

    /**
     * Connects a list of repositories to the Github connector. This operation fetches
     * all files, commits, issues, and pull requests from each specified repository.
     *
     * @param jwt the authentication principal representing the user making the request
     * @param request the request object containing the list of repositories to be connected
     * @return a ResponseEntity containing the result of the repository connection process
     */
    @Operation(
        summary = "Connect a list of repositories to the Github connector",
        description =
            "Connects a list of repositories to the Github connector." +
                " This will fetch all files, commits, issues, and prs from each repository",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "202",
                description = "The connection of all repositories is accepted",
            ),
            ApiResponse(responseCode = "400", description = "Invalid request body"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this endpoint"),
            ApiResponse(responseCode = "404", description = "Repository not found"),
        ],
    )
    @PostMapping("/connect/all")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    suspend fun connectRepositories(
        @Parameter(hidden = true)
        @AuthenticationPrincipal
        jwt: Jwt,
        @Valid
        @RequestBody
        request: ConnectRepositoriesRequest,
    ): ResponseEntity<ConnectRepositoriesResponse> {
        val response = connectionOrchestrator.connectRepositoriesIfExist(jwt.subject, request)
        return ResponseEntity.accepted().body(response)
    }

    /**
     * Connects a GitHub repository to the SprintStart application.
     *
     * This method initiates the connection of a repository specified by its owner and name,
     * enabling SprintStart to fetch and process related data. The following tasks are executed
     * as part of the connection process:
     *
     * - Fetching the repository code
     * - Fetching the repository commits
     * - Fetching the repository issues
     * - Fetching the repository pull requests
     * - Setting up a nightly CRON job to detect updates
     *
     * The expected repository schema should follow the format:
     * `https://github.com/{owner}/{name}`.
     *
     * @param request The request containing the details of the repository to connect,
     * including the GitHub owner and repository name.
     *
     * @return A response indicating the success of the connection process.
     *
     * @throws IllegalStateException If the GitHub API returns malformed responses while processing file resources.
     */
    @Operation(
        summary = "Connect a repository to the Github connector",
        description =
            "Connects a repository to the Github connector." +
                " This will fetch all files, commits, issues, and prs from that repository",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "202",
                description = "Repository connection accepted - Initialization jobs started",
            ),
            ApiResponse(responseCode = "400", description = "Invalid request body"),
            ApiResponse(responseCode = "404", description = "Repository not found"),
        ],
    )
    @PostMapping("/connect")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    suspend fun connectRepository(
        @Parameter(hidden = true)
        @AuthenticationPrincipal
        jwt: Jwt,
        @Valid
        @RequestBody
        request: ConnectRepositoryRequest,
    ): ResponseEntity<ConnectRepositoryResponse> {
        val transactionId = githubConnectorService.connectRepositoryIfExists(jwt.subject, request)
        return ResponseEntity.accepted().body(ConnectRepositoryResponse(transactionId))
    }

    /**
     * Updates all repositories connected to the GitHub connector.
     *
     * This method triggers the update process for all repositories stored in the system. For each connected
     * repository, the method schedules an asynchronous task to update its data by fetching the latest
     * information, including files, commits, issues, and pull requests from GitHub.
     *
     * Upon completion, the updated state of all repositories will reflect the most recent changes
     * from their respective repositories on GitHub.
     *
     * @throws IllegalStateException If an error occurs while updating any repository.
     */
    @Operation(
        summary = "Update all repositories",
        description = "Updates all repositories that are connected to the Github connector",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "202", description = "Repositories update jobs started successfully"),
            ApiResponse(responseCode = "400", description = "One of the connected repositories is not initialized"),
        ],
    )
    @PostMapping("/update-all")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    suspend fun updateAllRepositories(): ResponseEntity<UpdateAllRepositoriesResponse> {
        val response = githubUpdateService.updateAllRepositories()
        return ResponseEntity.accepted().body(response)
    }

    /**
     * Updates a specific repository connected to the GitHub connector.
     *
     * This method updates the state of a repository by fetching the latest information
     * from GitHub, including files, commits, issues, and pull requests.
     *
     * @param request The request containing the details of the repository to update.
     * This includes the necessary parameters to identify the repository.
     */
    @Operation(
        summary = "Update a repository",
        description = "Updates a specific repository that is connected to the Github connector",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "202", description = "Repository update job started successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request body"),
            ApiResponse(responseCode = "400", description = "Repository to update is not connected or not initialized"),
        ],
    )
    @PostMapping("/update")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    suspend fun updateRepository(
        @Valid @RequestBody request: UpdateRepositoryRequest,
    ): ResponseEntity<UpdateRepositoryResponse> {
        val response = githubUpdateService.updateRepository(request, true)
        return ResponseEntity.accepted().body(response)
    }

    /**
     * Links an already-connected repository to an additional project.
     *
     * This reuses the existing connection and only adds the project id to its set of linked
     * projects. It performs no fetching or re-ingestion, and is idempotent when the project is
     * already linked.
     *
     * @param jwt The authentication principal used to authorize access to the target project.
     * @param repositoryId The connected repository to link.
     * @param projectId The project to add to the repository's linked projects.
     * @return The repository connection with its resulting set of linked project ids.
     */
    @Operation(
        summary = "Link a connected repository to another project",
        description =
            "Adds a project to an already-connected repository without fetching or re-ingesting " +
                "anything. Idempotent when the project is already linked.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Repository linked to the project"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Caller has no access to the target project"),
            ApiResponse(responseCode = "404", description = "Repository connection not found"),
        ],
    )
    @PostMapping("/connections/{repositoryId}/projects/{projectId}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    fun addRepositoryToProject(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @PathVariable repositoryId: UUID,
        @PathVariable projectId: UUID,
    ): ResponseEntity<AddRepositoryToProjectResponse> {
        val projectIds = githubRepositoryProjectService.addProjectToRepository(jwt.subject, repositoryId, projectId)
        return ResponseEntity.ok(AddRepositoryToProjectResponse(repositoryId, projectIds))
    }

    /**
     * Unlinks a connected repository from a project.
     *
     * This only removes the project id from the connection's set of linked projects; the repository
     * connection and its artifacts are kept. It is idempotent when the project is not linked.
     *
     * @param jwt The authentication principal used to authorize access to the target project.
     * @param repositoryId The connected repository to unlink.
     * @param projectId The project to remove from the repository's linked projects.
     * @return The repository connection with its resulting set of linked project ids.
     */
    @Operation(
        summary = "Unlink a connected repository from a project",
        description =
            "Removes a project from an already-connected repository, keeping the connection and its " +
                "artifacts. Idempotent when the project is not linked.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Repository unlinked from the project"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Caller has no access to the target project"),
            ApiResponse(responseCode = "404", description = "Repository connection not found"),
        ],
    )
    @DeleteMapping("/connections/{repositoryId}/projects/{projectId}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    fun removeRepositoryFromProject(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @PathVariable repositoryId: UUID,
        @PathVariable projectId: UUID,
    ): ResponseEntity<RemoveRepositoryFromProjectResponse> {
        val projectIds =
            githubRepositoryProjectService.removeProjectFromRepository(jwt.subject, repositoryId, projectId)
        return ResponseEntity.ok(RemoveRepositoryFromProjectResponse(repositoryId, projectIds))
    }
}
