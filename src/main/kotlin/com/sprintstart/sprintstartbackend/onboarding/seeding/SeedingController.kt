package com.sprintstart.sprintstartbackend.onboarding.seeding

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(
    name = "Onboarding Seeding",
    description = "Endpoints for creating and resetting demo onboarding data for users.",
)
@RestController
@RequestMapping("/api/v1/onboarding")
class SeedingController(
    private val seedingService: SeedingService,
) {
    @Operation(
        summary = "Seeds onboarding data for a user",
        description = """
            Creates demo onboarding data for the given user.

            The operation only creates data if the user exists and does not already have
            an onboarding path. One seed file is selected randomly from the available
            onboarding seed data files.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Onboarding data was seeded successfully, or no seeding was needed.",
                content = [
                    Content(
                        mediaType = "text/plain",
                        schema = Schema(implementation = String::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid user id format.",
                content = [Content()],
            ),
        ],
    )
    @PostMapping("/{userId}/seeding")
    fun seed(
        @Parameter(
            description = "Unique identifier of the user for whom onboarding data should be seeded.",
            required = true,
        )
        @PathVariable userId: UUID,
    ): ResponseEntity<String> {
        seedingService.seed(userId)
        return ResponseEntity.ok("Seeded successfully")
    }

    @Operation(
        summary = "Resets onboarding data for a user",
        description = """
            Deletes all onboarding paths and nested onboarding data associated with the given user.

            This endpoint is mainly intended for development, testing, or demo setup.
        """,
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Onboarding data was reset successfully.",
                content = [
                    Content(
                        mediaType = "text/plain",
                        schema = Schema(implementation = String::class),
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid user id format.",
                content = [Content()],
            ),
        ],
    )
    @PostMapping("/{userId}/reset")
    fun reset(
        @Parameter(
            description = "Unique identifier of the user whose onboarding data should be deleted.",
            required = true,
        )
        @PathVariable userId: UUID,
    ): ResponseEntity<String> {
        seedingService.reset(userId)
        return ResponseEntity.ok("Reset successfully")
    }
}
