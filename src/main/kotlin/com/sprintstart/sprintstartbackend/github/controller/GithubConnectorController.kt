package com.sprintstart.sprintstartbackend.github.controller

import com.sprintstart.sprintstartbackend.github.models.api.requests.ConnectRepositoryRequest
import com.sprintstart.sprintstartbackend.github.models.api.requests.UpdateRepositoryRequest
import com.sprintstart.sprintstartbackend.github.models.api.responses.ConnectRepositoryResponse
import com.sprintstart.sprintstartbackend.github.models.api.responses.UpdateAllRepositoriesResponse
import com.sprintstart.sprintstartbackend.github.models.api.responses.UpdateRepositoryResponse
import com.sprintstart.sprintstartbackend.github.service.GithubConnectorService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(
    name = "Github Connector",
    description = "Endpoints for interacting with the Github connector",
)
@RestController
@RequestMapping("/api/v1/github")
@Validated
internal class GithubConnectorController(
    val githubConnectorService: GithubConnectorService,
) {
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
    suspend fun connectRepository(
        @Valid @RequestBody request: ConnectRepositoryRequest,
    ): ResponseEntity<ConnectRepositoryResponse> {
        val transactionId = githubConnectorService.connectRepositoryIfExists(request)
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
    suspend fun updateAllRepositories(): ResponseEntity<UpdateAllRepositoriesResponse> {
        val transactionId = githubConnectorService.updateAllRepositories()
        return ResponseEntity.accepted().body(UpdateAllRepositoriesResponse(transactionId))
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
    suspend fun updateRepository(
        @Valid @RequestBody request: UpdateRepositoryRequest,
    ): ResponseEntity<UpdateRepositoryResponse> {
        val transactionId = githubConnectorService.updateRepository(request)
        return ResponseEntity.accepted().body(UpdateRepositoryResponse(transactionId))
    }
}
