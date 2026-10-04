package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.controller

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.request.ConnectBitbucketRepositoriesRequest
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.request.ConnectBitbucketRepositoryRequest
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.request.DiscoverBitbucketRepositoriesRequest
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.response.BitbucketRepositoryProjectLinkResponse
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.response.ConnectBitbucketRepositoriesResponse
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.response.ConnectBitbucketRepositoryResponse
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.response.DiscoverBitbucketRepositoriesResponse
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.response.UpdateAllBitbucketRepositoriesResponse
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.response.UpdateBitbucketRepositoryResponse
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.BitbucketConnectionService
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.BitbucketRepositoryProjectService
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.BitbucketRepositoryVisibilityService
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.BitbucketUpdatesService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
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

/** Exposes connecting, discovering, linking and updating Bitbucket repositories. */
@Tag(name = "Bitbucket Connector", description = "Connect Bitbucket Cloud repositories.")
@RestController
@RequestMapping("/api/v1/bitbucket")
internal class BitbucketConnectionController(
    private val service: BitbucketConnectionService,
    private val projectService: BitbucketRepositoryProjectService,
    private val visibilityService: BitbucketRepositoryVisibilityService,
    private val updatesService: BitbucketUpdatesService,
) {
    /**
     * Connects one Bitbucket repository using a credential of the authenticated user.
     *
     * The connection is stored and the repository is cloned asynchronously, so the response carries
     * the transaction id the connector's events correlate on rather than the repository state.
     * A credential that does not exist surfaces as 404 through the shared Atlassian credential
     * exception handler, and a repository Bitbucket cannot find or show to that credential as 404
     * through the Bitbucket exception handler.
     *
     * @param jwt The authentication principal the repository's credential is resolved for.
     * @param request The repository to connect, with its own credential and project.
     * @return 202 with one transaction id.
     */
    @Operation(
        summary = "Connect a Bitbucket repository",
        description = "Stores the connection and starts cloning the repository in the background.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "202",
                description = "Connection accepted",
                content = [Content(schema = Schema(implementation = ConnectBitbucketRepositoryResponse::class))],
            ),
            ApiResponse(responseCode = "400", description = "Request body is invalid"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(
                responseCode = "404",
                description = "The named credential does not exist, or the repository does not exist or is " +
                    "not readable with it",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PostMapping
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    suspend fun connect(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: ConnectBitbucketRepositoryRequest,
    ): ResponseEntity<ConnectBitbucketRepositoryResponse> {
        val response = service.connectRepositoryIfExists(jwt.subject, request)
        return ResponseEntity.accepted().body(response)
    }

    /**
     * Connects several Bitbucket repositories in one request.
     *
     * Repositories are connected in order and the batch aborts on the first failure, so a
     * repository the caller's credential cannot read stops the entries after it. Connections
     * stored before the failure stay stored, but the batch answers with the failure instead of a
     * per-repository result. Connecting a repository that is already connected reuses its
     * connection and only links the submitted project, exactly like the single-repository endpoint.
     *
     * @param jwt The authentication principal the repository's credential is resolved for.
     * @param request The repositories to connect, each with its own credential and project.
     * @return 202 with one transaction id per `workspace/slug` and the coordinates that were reused.
     */
    @Operation(
        summary = "Connect several Bitbucket repositories",
        description =
            "Connects each submitted repository in order, aborting on the first failure, and starts " +
                "cloning the ones that are new. Already-connected repositories are reused and only " +
                "linked to the submitted project.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "202",
                description = "Connections accepted; per-repository transaction ids returned",
                content = [Content(schema = Schema(implementation = ConnectBitbucketRepositoriesResponse::class))],
            ),
            ApiResponse(responseCode = "400", description = "Request body is invalid"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Caller has no access to a target project"),
            ApiResponse(
                responseCode = "404",
                description = "A named credential does not exist, or a repository does not exist or is not " +
                    "readable with it",
            ),
        ],
    )
    @PostMapping("/connect/all")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    suspend fun connectRepositories(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @Valid @RequestBody request: ConnectBitbucketRepositoriesRequest,
    ): ResponseEntity<ConnectBitbucketRepositoriesResponse> {
        val response = service.connectRepositoriesIfExist(jwt.subject, request)
        return ResponseEntity.accepted().body(response)
    }

    /**
     * Discovers the repositories of one Bitbucket workspace.
     *
     * Bitbucket retired cross-workspace repository listing, so discovery is always scoped to a single
     * workspace. The named credential is resolved for the authenticated caller; a name the caller has
     * no credential for surfaces as 404 through the shared Atlassian credential handler.
     *
     * @param jwt The authentication principal the credential is resolved for.
     * @param workspace The workspace whose repositories should be listed.
     * @param credentialName The name of the caller's stored Atlassian credential to read it with.
     * @param page The zero-based page index. Defaults to 0.
     * @param pageSize The number of repositories per page. Defaults to 20.
     * @return 200 with the repositories on that page.
     */
    @Operation(
        summary = "Discover the repositories of a Bitbucket workspace",
        description =
            "Lists the repositories of one Bitbucket workspace that the caller's stored credential " +
                "can read, one page at a time.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Discovery successful",
                content = [Content(schema = Schema(implementation = DiscoverBitbucketRepositoriesResponse::class))],
            ),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this endpoint"),
            ApiResponse(responseCode = "404", description = "Credential with the given name does not exist"),
        ],
    )
    @GetMapping("/discover/workspace/{workspace}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    suspend fun discoverRepositoriesOfWorkspace(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @PathVariable workspace: String,
        @RequestParam(required = true) credentialName: String,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "20") pageSize: Int,
    ): ResponseEntity<DiscoverBitbucketRepositoriesResponse> {
        val request = DiscoverBitbucketRepositoriesRequest(
            workspace = workspace,
            authId = jwt.subject,
            credentialName = credentialName,
            page = page,
            pageSize = pageSize,
        )
        return ResponseEntity.ok(service.discoverRepositoriesOfWorkspace(request))
    }

    /**
     * Links an already-connected repository to an additional project.
     *
     * This reuses the existing connection and only adds the project id to its set of linked
     * projects. It performs no fetching or re-ingestion, and is idempotent when the project is
     * already linked. The project-access check inside the service answers 403; a connection the
     * caller cannot see answers the same 404 as an unknown one.
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
            ApiResponse(
                responseCode = "404",
                description = "Repository connection not found or invisible to the caller",
            ),
        ],
    )
    @PostMapping("/connections/{repositoryId}/projects/{projectId}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    suspend fun addRepositoryToProject(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @PathVariable repositoryId: UUID,
        @PathVariable projectId: UUID,
    ): ResponseEntity<BitbucketRepositoryProjectLinkResponse> {
        visibilityService.requireCallerCanSeeConnection(jwt.subject, repositoryId)

        val projectIds = withContext(Dispatchers.IO) {
            projectService.addProjectToRepository(jwt.subject, repositoryId, projectId)
        }
        return ResponseEntity.ok(BitbucketRepositoryProjectLinkResponse(repositoryId, projectIds))
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
    suspend fun removeRepositoryFromProject(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @PathVariable repositoryId: UUID,
        @PathVariable projectId: UUID,
    ): ResponseEntity<BitbucketRepositoryProjectLinkResponse> {
        val projectIds = withContext(Dispatchers.IO) {
            projectService.removeProjectFromRepository(jwt.subject, repositoryId, projectId)
        }
        return ResponseEntity.ok(BitbucketRepositoryProjectLinkResponse(repositoryId, projectIds))
    }

    /**
     * Re-ingests one connected repository incrementally on top of already ingested data.
     *
     * A repository whose source is disabled is refused: disabling is how a caller pauses ingestion,
     * and an explicit update of a paused repository is answered rather than quietly performed.
     *
     * @param repositoryId The connected repository to update.
     * @return 202 with the transaction id the connector's progress events report under.
     */
    @Operation(
        summary = "Update a connected Bitbucket repository",
        description =
            "Re-ingests the files, commits and pull requests of one connected repository, reading " +
                "only what changed since the last sync.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "202",
                description = "Update accepted; the transaction id is returned",
                content = [Content(schema = Schema(implementation = UpdateBitbucketRepositoryResponse::class))],
            ),
            ApiResponse(
                responseCode = "400",
                description = "The repository's source is disabled",
            ),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this endpoint"),
            ApiResponse(
                responseCode = "404",
                description = "Repository connection not found or invisible to the caller",
            ),
        ],
    )
    @PostMapping("/connections/{repositoryId}/update")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    suspend fun updateRepository(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @PathVariable repositoryId: UUID,
    ): ResponseEntity<UpdateBitbucketRepositoryResponse> {
        val transactionId = updatesService.updateRepository(jwt.subject, repositoryId)
        return ResponseEntity.accepted().body(UpdateBitbucketRepositoryResponse(transactionId))
    }

    /**
     * Re-ingests every connected repository the caller may reach incrementally on top of the already ingested data.
     *
     * Each repository is updated on its own, so one repository whose credential was revoked does not
     * stop the others. Disabled repositories are skipped rather than updated: a batch call is not a
     * reason to wake a source the caller paused. Repositories linked to none of the caller's
     * projects are skipped the same way.
     *
     * @return 202 with one transaction id per updated `workspace/slug`.
     */
    @Operation(
        summary = "Update all connected Bitbucket repositories",
        description =
            "Re-ingests every connected repository the caller may reach, reading " +
                "only what changed since each one's last sync.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "202",
                description = "Batch update accepted; one transaction id per updated repository is returned",
                content = [Content(schema = Schema(implementation = UpdateAllBitbucketRepositoriesResponse::class))],
            ),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this endpoint"),
        ],
    )
    @PostMapping("/update-all")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    suspend fun updateAllRepositories(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<UpdateAllBitbucketRepositoriesResponse> {
        val response = updatesService.updateAllRepositories(jwt.subject)
        return ResponseEntity.accepted().body(response)
    }
}
