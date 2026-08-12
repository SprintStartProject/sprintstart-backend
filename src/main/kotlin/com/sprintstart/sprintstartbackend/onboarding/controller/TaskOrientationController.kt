package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.external.model.AiProgressEvent
import com.sprintstart.sprintstartbackend.onboarding.model.request.orientation.AuthorOrientationRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.orientation.MyOrientationResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.orientation.OrientationPacketResponse
import com.sprintstart.sprintstartbackend.onboarding.service.TaskOrientationService
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.flow.Flow
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Task-scoped orientation: what this project already says about doing the task a hire has.
 *
 * An AI packet is assembled on demand, cached against the corpus it was built from, and thrown away
 * when that corpus moves -- nobody stands between a hire and their orientation, and there is no
 * approval lifecycle.
 *
 * A packet can also be **human-authored**, and this controller carries both surfaces for that:
 * a PM (or ADMIN) authors any task's orientation via `/orientation/tasks/{taskId}`, and a hire fixes
 * their *own* task's orientation in place via `PUT`/`DELETE /me/orientation`. A human packet is served
 * as-is and never AI-re-assembled -- the AI is the quick way to a packet, not the only author of one.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(
    name = "Onboarding - Orientation",
    description = "What the project already says about doing the task you have",
)
class TaskOrientationController(
    private val taskOrientationService: TaskOrientationService,
    private val userApi: UserApi,
) {
    @Operation(
        summary = "Orientation for my current task",
        description = "Assembled from the project's own material, segmented by step and cited " +
            "throughout. Having no current task, and having a task the corpus cannot ground a " +
            "packet for, are both ordinary states rather than errors — the response says which, " +
            "and no packet is ever invented to fill the gap. Reading this is never a prerequisite " +
            "for starting the task, and never assigns one.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Orientation state returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "404", description = "You are not a member of that project"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/me/orientation")
    @PreAuthorize("hasAnyRole('USER', 'PM', 'HR', 'ADMIN')")
    suspend fun getMyOrientation(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam projectId: UUID,
    ): MyOrientationResponse = taskOrientationService.getForHire(resolveUserId(jwt), projectId)

    @Operation(
        summary = "Watch my orientation being assembled (streaming)",
        description = "The same assembly as `GET /me/orientation`, streamed as Server-Sent Events so " +
            "a hire can watch it happen: a stage per retrieval step, a section as it clears grounding, " +
            "and a terminal `done` once the packet is cached. The cached packet is identical to what " +
            "the plain read returns — the stream is a view, so a client re-reads `GET /me/orientation` " +
            "on `done` for the authoritative result. Having no task, or a human-written packet, is a " +
            "single `done` with no AI call.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Progress stream started"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "404", description = "You are not a member of that project"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/me/orientation/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @PreAuthorize("hasAnyRole('USER', 'PM', 'HR', 'ADMIN')")
    suspend fun streamMyOrientation(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam projectId: UUID,
    ): Flow<AiProgressEvent> = taskOrientationService.streamForHire(resolveUserId(jwt), projectId)

    @Operation(
        summary = "Fix my current task's orientation in place",
        description = "Replaces the orientation for the task the caller currently has with their own " +
            "words, pinning it as human-authored so it is served as-is and never AI-regenerated. " +
            "This is what the 'this is wrong / out of date' affordance does.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Orientation saved"),
            ApiResponse(responseCode = "400", description = "No section, or a section with a blank title or body"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "404", description = "Not a member of that project, or have no current task"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/me/orientation")
    @PreAuthorize("hasAnyRole('USER', 'PM', 'HR', 'ADMIN')")
    fun authorMyOrientation(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam projectId: UUID,
        @RequestBody request: AuthorOrientationRequest,
    ): OrientationPacketResponse = taskOrientationService.authorForHire(resolveUserId(jwt), projectId, request)

    @Operation(
        summary = "Restore AI orientation for my current task",
        description = "Drops my hand-authored packet for my current task so it is assembled from the " +
            "project's material again on the next read.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Reverted to AI assembly"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "404", description = "Not a member of that project, or have no current task"),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/me/orientation")
    @PreAuthorize("hasAnyRole('USER', 'PM', 'HR', 'ADMIN')")
    fun revertMyOrientation(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam projectId: UUID,
    ) = taskOrientationService.revertForHire(resolveUserId(jwt), projectId)

    @Operation(
        summary = "Orientation for a task, for authoring (PM)",
        description = "Returns the current packet for a task to edit, or a shell (task title and link) " +
            "to author from blank. Never assembles: opening the editor does not trigger AI generation.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Current orientation returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No task found with that id"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/orientation/tasks/{taskId}")
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    fun getTaskOrientation(
        @Parameter(description = "UUID of the starter-work task to author orientation for")
        @PathVariable taskId: UUID,
        @RequestParam projectId: UUID,
    ): MyOrientationResponse = taskOrientationService.getForAuthoring(taskId, projectId)

    @Operation(
        summary = "Author a task's orientation (PM)",
        description = "Replaces a task's orientation with a hand-authored packet, pinned as " +
            "human-authored so it is served as-is and never AI-regenerated.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Orientation saved"),
            ApiResponse(responseCode = "400", description = "No section, or a section with a blank title or body"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No task found with that id"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/orientation/tasks/{taskId}")
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    fun authorTaskOrientation(
        @Parameter(description = "UUID of the starter-work task to author orientation for")
        @PathVariable taskId: UUID,
        @RequestParam projectId: UUID,
        @RequestBody request: AuthorOrientationRequest,
    ): OrientationPacketResponse = taskOrientationService.authorPacket(taskId, projectId, request)

    @Operation(
        summary = "Restore AI orientation for a task (PM)",
        description = "Drops the hand-authored packet for a task so it is assembled from the project's " +
            "material again on the next read.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Reverted to AI assembly"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No task found with that id"),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/orientation/tasks/{taskId}")
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    fun revertTaskOrientation(
        @Parameter(description = "UUID of the starter-work task to revert")
        @PathVariable taskId: UUID,
        @RequestParam projectId: UUID,
    ) = taskOrientationService.revertToAi(taskId, projectId)

    private fun resolveUserId(jwt: Jwt): UUID =
        userApi.getUserIdByAuthId(jwt.subject).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: ${jwt.subject}")
        }
}
