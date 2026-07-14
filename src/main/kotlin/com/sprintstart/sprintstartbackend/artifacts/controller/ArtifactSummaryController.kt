package com.sprintstart.sprintstartbackend.artifacts.controller

import com.sprintstart.sprintstartbackend.artifacts.model.ai.AiArtifactSummaryStreamMessage
import com.sprintstart.sprintstartbackend.artifacts.service.ArtifactSummaryService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.flow.Flow
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Endpoints exposing AI-generated artifact summaries.
 */
@RestController
@RequestMapping("/api/v1/artifacts")
@Tag(name = "Artifacts", description = "Artifact-related endpoints")
class ArtifactSummaryController(
    private val artifactSummaryService: ArtifactSummaryService,
) {
    /**
     * Streams a summary of the given artifact, generating and caching one if none is cached yet
     * or the artifact's content has since changed.
     */
    @Operation(
        summary = "Get an artifact summary (streaming)",
        description = "Streams an AI-generated summary of the artifact over Server-Sent Events, " +
            "cached until its content changes.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Stream started successfully, tokens will now come one by one",
                content = [
                    Content(
                        mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                        schema = Schema(
                            examples = [
                                "data: {\"type\": \"stage\", \"name\": \"summary\", " +
                                    "\"detail\": \"Generating summary\"}",
                                "data: {\"type\": \"token\", \"content\": \"## Key points\"}",
                                "data: {\"type\": \"citation\", \"artifact_id\": \"artifact-1\", " +
                                    "\"filename\": \"README.md\"}",
                                "data: {\"type\": \"done\"}",
                                "data: {\"type\": \"error\", \"message\": \"LLM backend unreachable\"}",
                            ],
                        ),
                    ),
                ],
            ),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "404", description = "No artifact found for the given id"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/{artifactId}/summary", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @PreAuthorize("hasRole('USER')")
    suspend fun getSummary(
        @PathVariable artifactId: UUID,
    ): Flow<AiArtifactSummaryStreamMessage> {
        return artifactSummaryService.getSummary(artifactId)
    }
}
