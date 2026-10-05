package com.sprintstart.sprintstartbackend.connectors.notion.controller

import com.sprintstart.sprintstartbackend.connectors.notion.NotionConnector
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.request.ConfigureNotionScheduleRequest
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.request.CreateNotionWorkspaceConnectionRequest
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.response.NotionDiscoveredPageResponse
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.response.NotionWorkspaceConnectionResponse
import com.sprintstart.sprintstartbackend.connectors.notion.model.ingestion.NotionIngestionResult
import com.sprintstart.sprintstartbackend.connectors.notion.service.NotionWorkspaceConnectionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
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
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

private const val NOTION_ROLE = "hasAnyRole('PM', 'ADMIN')"
private const val MANAGE_NOTION_PROJECT =
    "$NOTION_ROLE and @projectAuth.canManageProject(authentication, #projectId)"

@Tag(name = "Notion", description = "Discover Notion pages and manage workspace-scoped connections.")
@Validated
@RestController
@RequestMapping("/api/v1/notion")
internal class NotionPageController(
    private val workspaceConnectionService: NotionWorkspaceConnectionService,
    private val connector: NotionConnector,
) {
    @Operation(
        summary = "Discover accessible Notion pages",
        description = "Lists pages visible to the selected stored Notion credential.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Pages discovered"),
            ApiResponse(responseCode = "401", description = "Backend authentication required"),
            ApiResponse(responseCode = "403", description = "PM or Admin role required"),
            ApiResponse(responseCode = "404", description = "Credential not found"),
            ApiResponse(responseCode = "422", description = "Notion rejected the stored credential"),
            ApiResponse(responseCode = "502", description = "Notion discovery failed"),
        ],
    )
    @GetMapping("/pages")
    @PreAuthorize(NOTION_ROLE)
    suspend fun discoverPages(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @RequestParam @NotBlank @Size(max = 255) credentialName: String,
    ): ResponseEntity<List<NotionDiscoveredPageResponse>> {
        val response = workspaceConnectionService.discoverPages(
            authId = jwt.subject,
            credentialName = credentialName,
        )
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "Connect a Notion workspace to a project")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Workspace connection created"),
            ApiResponse(responseCode = "400", description = "Invalid workspace connection"),
            ApiResponse(responseCode = "401", description = "Backend authentication required"),
            ApiResponse(responseCode = "403", description = "Project management permission required"),
            ApiResponse(responseCode = "404", description = "Credential not found"),
            ApiResponse(responseCode = "409", description = "Integration is already connected to this project"),
            ApiResponse(responseCode = "422", description = "Notion rejected the stored credential"),
            ApiResponse(responseCode = "502", description = "Notion validation failed"),
        ],
    )
    @PostMapping("/projects/{projectId}/connections")
    @PreAuthorize(MANAGE_NOTION_PROJECT)
    suspend fun connectWorkspace(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @PathVariable projectId: UUID,
        @Valid @RequestBody request: CreateNotionWorkspaceConnectionRequest,
    ): ResponseEntity<NotionWorkspaceConnectionResponse> {
        val response = workspaceConnectionService.connectWorkspace(
            authId = jwt.subject,
            projectId = projectId,
            request = request,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @Operation(summary = "List a project's Notion workspace connections")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Connections retrieved"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Project management permission required"),
        ],
    )
    @GetMapping("/projects/{projectId}/connections")
    @PreAuthorize(MANAGE_NOTION_PROJECT)
    fun getConnections(
        @PathVariable projectId: UUID,
    ): ResponseEntity<List<NotionWorkspaceConnectionResponse>> {
        val response = workspaceConnectionService.getConnections(projectId)
        return ResponseEntity.ok(response)
    }

    /** Configures automatic synchronization for one project-owned Notion workspace connection. */
    @Operation(
        summary = "Configure Notion synchronization",
        description = "Stores a validated schedule and enables or disables automatic synchronization.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Schedule updated"),
            ApiResponse(responseCode = "400", description = "Invalid schedule"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Project management permission required"),
            ApiResponse(responseCode = "404", description = "Connection not found in the project"),
        ],
    )
    @PutMapping("/projects/{projectId}/connections/{connectionId}/schedule")
    @PreAuthorize(MANAGE_NOTION_PROJECT)
    fun configureSchedule(
        @PathVariable projectId: UUID,
        @PathVariable connectionId: UUID,
        @Valid @RequestBody request: ConfigureNotionScheduleRequest,
    ): ResponseEntity<NotionWorkspaceConnectionResponse> {
        return ResponseEntity.ok(workspaceConnectionService.configureSchedule(projectId, connectionId, request))
    }

    /** Runs synchronous ingestion for one project-owned Notion workspace connection. */
    @Operation(
        summary = "Synchronize a Notion workspace",
        description = "Ingests accessible pages as separate PAGE artifacts in one workspace run.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Synchronization finished"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Project management permission required"),
            ApiResponse(responseCode = "404", description = "Connection not found in the project"),
            ApiResponse(responseCode = "502", description = "Notion synchronization service failed"),
        ],
    )
    @PostMapping("/projects/{projectId}/connections/{connectionId}/update")
    @PreAuthorize(MANAGE_NOTION_PROJECT)
    suspend fun update(
        @PathVariable projectId: UUID,
        @PathVariable connectionId: UUID,
    ): ResponseEntity<NotionIngestionResult> {
        return ResponseEntity.ok(connector.ingest(projectId, connectionId))
    }

    @Operation(summary = "Delete a Notion workspace connection")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Connection deleted"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Project management permission required"),
            ApiResponse(responseCode = "404", description = "Connection not found in this project"),
        ],
    )
    @DeleteMapping("/projects/{projectId}/connections/{connectionId}")
    @PreAuthorize(MANAGE_NOTION_PROJECT)
    fun deleteConnection(
        @PathVariable projectId: UUID,
        @PathVariable connectionId: UUID,
    ): ResponseEntity<Unit> {
        workspaceConnectionService.deleteConnection(
            projectId = projectId,
            connectionId = connectionId,
        )
        return ResponseEntity.noContent().build()
    }
}
