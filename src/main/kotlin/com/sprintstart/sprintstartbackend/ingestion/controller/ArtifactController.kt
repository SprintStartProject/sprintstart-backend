package com.sprintstart.sprintstartbackend.ingestion.controller

import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactFilterCriteria
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactSort
import com.sprintstart.sprintstartbackend.ingestion.model.dto.UploadFormat
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactContentRedirectResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactContentResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactFacetsResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactPageResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactResponse
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.service.ArtifactQueryService
import com.sprintstart.sprintstartbackend.ingestion.service.ArtifactService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.LocalDate
import java.util.UUID

private const val DEFAULT_PAGE = "1"
private const val DEFAULT_SIZE = "20"
private const val MAX_PAGE_SIZE = 100L
private const val FROM_DESCRIPTION =
    "First import day to include, ISO yyyy-MM-dd, read as a UTC calendar day (inclusive). " +
        "Must not be after `to`, else 400."
private const val TO_DESCRIPTION =
    "Last import day to include, ISO yyyy-MM-dd, read as a UTC calendar day (inclusive)."

/**
 * Read-only HTTP entry point for opening one artifact.
 *
 * The endpoint is project-scoped so callers must supply both the project identifier used for
 * authorization and the artifact identifier used to locate the payload or source URL.
 */
@RestController
@Validated
@RequestMapping("/api/v1/")
@Tag(
    name = "Artifacts",
    description = "Read-only artifact content retrieval scoped to a project",
)
class ArtifactController(
    private val artifactService: ArtifactService,
    private val artifactQueryService: ArtifactQueryService,
) {
    /**
     * Returns a paginated artifact list across all projects for administrative callers.
     *
     * When a filter is provided, the response is narrowed to artifacts whose searchable fields
     * contain the given case-insensitive fragment.
     */
    @GetMapping("admin/artifacts")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
        summary = "Get all artifacts",
        description =
            "Returns a paginated artifact list across all projects. When a filter is provided, " +
                "the search is performed case-insensitively across the configured searchable fields.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Artifact page returned successfully"),
        ],
    )
    fun getAllArtifacts(
        @RequestParam(defaultValue = DEFAULT_PAGE) @Min(1) page: Int,
        @RequestParam(defaultValue = DEFAULT_SIZE) @Min(1) @Max(MAX_PAGE_SIZE) size: Int,
        @RequestParam(defaultValue = "") filter: String,
    ): ResponseEntity<ArtifactPageResponse> =
        ResponseEntity.ok(
            artifactQueryService.getAllArtifacts(page, size, filter),
        )

    /**
     * Returns a paginated artifact list for one project visible to the authenticated caller.
     *
     * When a filter is provided, the response is narrowed to project artifacts whose searchable
     * fields contain the given case-insensitive fragment.
     */
    @GetMapping("projects/{projectId}/artifacts")
    @PreAuthorize("hasRole('USER')")
    @Operation(
        summary = "Get project artifacts",
        description =
            "Returns a paginated artifact list limited to one project visible to the " +
                "authenticated user. When a filter or criteria are provided, the search is performed " +
                "case-insensitively across the configured searchable fields.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Project artifact page returned successfully"),
            ApiResponse(
                responseCode = "400",
                description = "Invalid query or pagination parameters, unknown sort, malformed date, or from after to",
            ),
            ApiResponse(responseCode = "403", description = "Caller has no access to the project"),
        ],
    )
    fun getProjectArtifacts(
        @RequestParam(defaultValue = DEFAULT_PAGE) @Min(1) page: Int,
        @RequestParam(defaultValue = DEFAULT_SIZE) @Min(1) @Max(MAX_PAGE_SIZE) size: Int,
        // `filter` predates `search` and kept its own contract (a fragment matched against title,
        // type, source system and metadata) until the Knowledge Base moved server-side. It is now
        // an alias of `search`: nothing in this repo sends it, and it stays only so an outside
        // client that does is not broken by a query whose meaning it cannot see changing.
        @Parameter(description = "Deprecated alias of `search`; send `search` instead")
        @RequestParam(defaultValue = "")
        filter: String,
        @Parameter(
            description = "Case-insensitive match against the artifact's title, source id and source url",
        )
        @RequestParam(required = false)
        search: String?,
        @RequestParam(required = false) types: Set<ArtifactType>?,
        @RequestParam(required = false) sources: Set<SourceSystem>?,
        @RequestParam(required = false) repositories: Set<String>?,
        @RequestParam(required = false) format: UploadFormat?,
        @Parameter(
            description = "Row order: ADDED_DESC (newest import first, the default), " +
                "CHANGED_DESC (latest content change first, falling back to the import time) or " +
                "TITLE_ASC (case-insensitive, untitled last). Any other value is rejected with 400.",
        )
        @RequestParam(defaultValue = "ADDED_DESC")
        sort: ArtifactSort,
        @Parameter(description = FROM_DESCRIPTION)
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        from: LocalDate?,
        @Parameter(description = TO_DESCRIPTION)
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        to: LocalDate?,
        @Parameter(
            description = "UUID of the project whose artifacts should be returned",
        ) @PathVariable projectId: UUID,
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ArtifactPageResponse> {
        val effectiveSearch = search ?: (if (filter.isNotBlank()) filter else null)
        val criteria = ArtifactFilterCriteria(
            search = effectiveSearch,
            types = types,
            sources = sources,
            repositories = repositories,
            format = format,
            from = from,
            to = to,
        )
        return ResponseEntity.ok(
            artifactQueryService.getProjectArtifacts(page, size, criteria, sort, projectId, jwt.subject),
        )
    }

    @GetMapping("projects/{projectId}/artifacts/facets")
    @PreAuthorize("hasRole('USER')")
    @Operation(
        summary = "Get project artifact facets",
        description = "Returns aggregated counts for artifact facets scoped to a project.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Facet counts returned successfully"),
            ApiResponse(
                responseCode = "400",
                description = "Invalid facet query parameters, malformed date, or from after to",
            ),
            ApiResponse(responseCode = "403", description = "Caller has no access to the project"),
        ],
    )
    fun getProjectArtifactFacets(
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) types: Set<ArtifactType>?,
        @RequestParam(required = false) sources: Set<SourceSystem>?,
        @RequestParam(required = false) repositories: Set<String>?,
        @RequestParam(required = false) format: UploadFormat?,
        @Parameter(description = FROM_DESCRIPTION)
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        from: LocalDate?,
        @Parameter(description = TO_DESCRIPTION)
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        to: LocalDate?,
        @Parameter(
            description = "UUID of the project whose artifact facets should be calculated",
        ) @PathVariable projectId: UUID,
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ArtifactFacetsResponse> {
        val criteria = ArtifactFilterCriteria(
            search = search,
            types = types,
            sources = sources,
            repositories = repositories,
            format = format,
            from = from,
            to = to,
        )
        return ResponseEntity.ok(
            artifactQueryService.getProjectArtifactFacets(projectId, criteria, jwt.subject),
        )
    }

    @GetMapping("projects/{projectId}/artifacts/{artifactId}")
    @PreAuthorize("hasRole('USER')")
    @Operation(
        summary = "Get single artifact",
        description = "Returns metadata for one artifact when the caller has access to the requested project.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Artifact returned successfully"),
            ApiResponse(responseCode = "403", description = "Caller has no access to the project"),
            ApiResponse(responseCode = "404", description = "Artifact not found in project"),
        ],
    )
    fun getArtifact(
        @Parameter(description = "UUID of the project that scopes artifact access") @PathVariable projectId: UUID,
        @Parameter(description = "UUID of the artifact to return") @PathVariable artifactId: UUID,
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ArtifactResponse> =
        ResponseEntity.ok(
            artifactQueryService.getArtifact(projectId, artifactId, jwt.subject),
        )

    /**
     * Opens one artifact by returning stored bytes or redirecting to its source URL.
     *
     * The caller must have `USER` access to the given project, and the artifact must be linked to
     * that same project. Artifacts with local payloads return `200`; remote artifacts without local
     * payloads return `302` to their source URL when one is known.
     *
     * @param projectId The SprintStart project that scopes access to the artifact.
     * @param artifactId The artifact whose stored content should be returned.
     * @param jwt The authenticated JWT used to resolve the caller subject.
     * @return The artifact payload with a response `Content-Type`, or a redirect to the source URL.
     */
    @GetMapping("/projects/{projectId}/artifacts/{artifactId}/content")
    @PreAuthorize("hasRole('USER')")
    @Operation(
        summary = "Get artifact content",
        description =
            "Returns the raw stored payload for one artifact when the authenticated user has " +
                "access to the requested project and the artifact is linked to that project.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Artifact content returned successfully"),
            ApiResponse(responseCode = "302", description = "Artifact source URL returned for remote content"),
            ApiResponse(responseCode = "403", description = "Caller has no access to the project"),
            ApiResponse(responseCode = "404", description = "Artifact or artifact content not found"),
        ],
    )
    fun getArtifactContent(
        @Parameter(description = "UUID of the project that scopes artifact access") @PathVariable projectId: UUID,
        @Parameter(
            description = "UUID of the artifact whose content should be returned",
        ) @PathVariable artifactId: UUID,
        @Parameter(hidden = true) @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ByteArray> {
        val response = artifactService.getArtifactContent(
            projectId = projectId,
            artifactId = artifactId,
            authId = jwt.subject,
        )
        return when (response) {
            is ArtifactContentResponse -> {
                val mediaType = runCatching {
                    MediaType.parseMediaType(
                        response.mime,
                    )
                }.getOrDefault(MediaType.APPLICATION_OCTET_STREAM)
                ResponseEntity
                    .ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .body(response.content)
            }

            is ArtifactContentRedirectResponse ->
                ResponseEntity
                    .status(HttpStatus.FOUND)
                    .location(URI.create(response.url))
                    .build<ByteArray>()
        }
    }
}
