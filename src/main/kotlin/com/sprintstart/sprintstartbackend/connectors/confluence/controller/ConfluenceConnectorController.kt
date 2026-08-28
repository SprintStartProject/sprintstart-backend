package com.sprintstart.sprintstartbackend.connectors.confluence.controller

import com.sprintstart.sprintstartbackend.connectors.confluence.ConfluenceConnector
import com.sprintstart.sprintstartbackend.connectors.confluence.model.api.request.ConfigureConfluenceScheduleRequest
import com.sprintstart.sprintstartbackend.connectors.confluence.model.api.request.CreateConfluenceConnectionRequest
import com.sprintstart.sprintstartbackend.connectors.confluence.model.api.response.ConfluenceConnectionResponse
import com.sprintstart.sprintstartbackend.connectors.confluence.model.ingestion.ConfluenceIngestionResult
import com.sprintstart.sprintstartbackend.connectors.confluence.service.ConfluenceConnectionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
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
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

private const val MANAGE_PROJECT =
    "(hasRole('PM') or hasRole('ADMIN')) and @projectAuth.canManageProject(authentication, #projectId)"

/** Exposes project-scoped Confluence connection and synchronization operations. */
@Tag(name = "Confluence Connector", description = "Connect and synchronize Confluence Cloud spaces.")
@Validated
@RestController
@RequestMapping("/api/v1/confluence/projects/{projectId}/connections")
internal class ConfluenceConnectorController(
    private val connectionService: ConfluenceConnectionService,
    private val connector: ConfluenceConnector,
) {
    /** Validates and stores one Confluence Cloud space connection for a managed project. */
    @Operation(
        summary = "Connect a Confluence space",
        description = "Validates the credentials and numeric space ID before atomically storing a safe connection.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "Connection created",
                content = [Content(schema = Schema(implementation = ConfluenceConnectionResponse::class))],
            ),
            ApiResponse(responseCode = "400", description = "Invalid connection configuration"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Project management permission required"),
            ApiResponse(responseCode = "404", description = "Confluence space not found"),
            ApiResponse(responseCode = "409", description = "Equivalent connection already exists"),
            ApiResponse(responseCode = "502", description = "Confluence validation service failed"),
        ],
    )
    @PostMapping
    @PreAuthorize(MANAGE_PROJECT)
    suspend fun connect(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @PathVariable projectId: UUID,
        @Valid @RequestBody request: CreateConfluenceConnectionRequest,
    ): ResponseEntity<ConfluenceConnectionResponse> {
        val response = connectionService.createConnection(jwt.subject, projectId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    /** Lists the configured Confluence sources belonging to a managed project. */
    @Operation(
        summary = "Discover configured Confluence connections",
        description = "Returns only safe connection DTOs scoped to the requested project.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Connections retrieved"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Project management permission required"),
        ],
    )
    @GetMapping
    @PreAuthorize(MANAGE_PROJECT)
    fun discover(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @PathVariable projectId: UUID,
    ): ResponseEntity<List<ConfluenceConnectionResponse>> {
        return ResponseEntity.ok(connectionService.getConnections(jwt.subject, projectId))
    }

    /** Retrieves one connection only when it belongs to the requested managed project. */
    @Operation(summary = "Get a Confluence connection", description = "Looks up a connection by project and ID.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Connection retrieved"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Project management permission required"),
            ApiResponse(responseCode = "404", description = "Connection not found in the project"),
        ],
    )
    @GetMapping("/{connectionId}")
    @PreAuthorize(MANAGE_PROJECT)
    fun getConnection(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @PathVariable projectId: UUID,
        @PathVariable connectionId: UUID,
    ): ResponseEntity<ConfluenceConnectionResponse> {
        return ResponseEntity.ok(connectionService.getConnection(jwt.subject, projectId, connectionId))
    }

    /** Configures automatic synchronization for one project-owned Confluence connection. */
    @Operation(
        summary = "Configure Confluence synchronization",
        description = "Stores a validated schedule and enables or disables automatic synchronization.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Schedule updated",
                content = [Content(schema = Schema(implementation = ConfluenceConnectionResponse::class))],
            ),
            ApiResponse(responseCode = "400", description = "Invalid schedule"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Project management permission required"),
            ApiResponse(responseCode = "404", description = "Connection not found in the project"),
        ],
    )
    @PutMapping("/{connectionId}/schedule")
    @PreAuthorize(MANAGE_PROJECT)
    fun configureSchedule(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @PathVariable projectId: UUID,
        @PathVariable connectionId: UUID,
        @Valid @RequestBody request: ConfigureConfluenceScheduleRequest,
    ): ResponseEntity<ConfluenceConnectionResponse> {
        return ResponseEntity.ok(
            connectionService.configureSchedule(jwt.subject, projectId, connectionId, request),
        )
    }

    /** Runs the existing retry-aware ingestion flow for one project-owned connection. */
    @Operation(
        summary = "Synchronize a Confluence connection",
        description =
            "Runs synchronous ingestion and returns COMPLETED, PARTIAL, or FAILED with safe aggregate counts.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Synchronization finished"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Project management permission required"),
            ApiResponse(responseCode = "404", description = "Connection not found in the project"),
            ApiResponse(responseCode = "502", description = "Terminal Confluence service failure"),
        ],
    )
    @PostMapping("/{connectionId}/update")
    @PreAuthorize(MANAGE_PROJECT)
    suspend fun update(
        @PathVariable projectId: UUID,
        @PathVariable connectionId: UUID,
    ): ResponseEntity<ConfluenceIngestionResult> {
        return ResponseEntity.ok(connector.ingest(projectId, connectionId))
    }
}
