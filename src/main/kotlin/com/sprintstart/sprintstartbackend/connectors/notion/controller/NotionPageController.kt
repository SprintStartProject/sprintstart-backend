package com.sprintstart.sprintstartbackend.connectors.notion.controller

import com.sprintstart.sprintstartbackend.connectors.notion.model.api.request.CreateNotionPageConnectionRequest
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.response.NotionDiscoveredPageResponse
import com.sprintstart.sprintstartbackend.connectors.notion.model.api.response.NotionPageConnectionResponse
import com.sprintstart.sprintstartbackend.connectors.notion.service.NotionPageConnectionService
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
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

private const val NOTION_ROLE = "hasAnyRole('PM', 'ADMIN')"
private const val MANAGE_NOTION_PROJECT =
    "$NOTION_ROLE and @projectAuth.canManageProject(authentication, #projectId)"

@Tag(name = "Notion Pages", description = "Discover and connect Notion pages.")
@Validated
@RestController
@RequestMapping("/api/v1/notion")
internal class NotionPageController(
    private val pageConnectionService: NotionPageConnectionService,
) {
    @Operation(
        summary = "Discover shared Notion pages",
        description = "Lists pages visible to the selected stored Notion credential.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Pages discovered"),
            ApiResponse(responseCode = "401", description = "Authentication required or invalid Notion token"),
            ApiResponse(responseCode = "403", description = "PM or Admin role required"),
            ApiResponse(responseCode = "404", description = "Credential not found"),
            ApiResponse(responseCode = "502", description = "Notion discovery failed"),
        ],
    )
    @GetMapping("/pages")
    @PreAuthorize(NOTION_ROLE)
    suspend fun discoverPages(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @RequestParam @NotBlank @Size(max = 255) credentialName: String,
    ): ResponseEntity<List<NotionDiscoveredPageResponse>> {
        val response = pageConnectionService.discoverPages(
            authId = jwt.subject,
            credentialName = credentialName
        )
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "Connect a Notion page to a project")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Page connection created"),
            ApiResponse(responseCode = "400", description = "Invalid page connection"),
            ApiResponse(responseCode = "401", description = "Authentication required or invalid Notion token"),
            ApiResponse(responseCode = "403", description = "Project management permission required"),
            ApiResponse(responseCode = "404", description = "Credential or Notion page not found"),
            ApiResponse(responseCode = "409", description = "Page is already connected to this project"),
            ApiResponse(responseCode = "502", description = "Notion validation failed"),
        ],
    )
    @PostMapping("/projects/{projectId}/connections")
    @PreAuthorize(MANAGE_NOTION_PROJECT)
    suspend fun connectPage(
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
        @PathVariable projectId: UUID,
        @Valid @RequestBody request: CreateNotionPageConnectionRequest,
    ): ResponseEntity<NotionPageConnectionResponse> {
        val response = pageConnectionService.connectPage(
            authId = jwt.subject,
            projectId = projectId,
            request = request,
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @Operation(summary = "List a project's Notion page connections")
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
    ): ResponseEntity<List<NotionPageConnectionResponse>> {
        val response = pageConnectionService.getConnections(projectId)
        return ResponseEntity.ok(response)
    }

    @Operation(summary = "Delete a Notion page connection")
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
        pageConnectionService.deleteConnection(
            projectId = projectId,
            connectionId = connectionId
        )
        return ResponseEntity.noContent().build()
    }
}
