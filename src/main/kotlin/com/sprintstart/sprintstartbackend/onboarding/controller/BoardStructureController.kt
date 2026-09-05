package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.request.board.SaveBoardStructureRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardStructureResponse
import com.sprintstart.sprintstartbackend.onboarding.service.BoardStructureService
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * How the hire has arranged their own board.
 *
 * Self-serve only, and the caller is always resolved from the token — same as [BoardController],
 * and for the same reason: there is no "read somebody else's arrangement" here to get the
 * authorisation wrong on.
 *
 * Its own controller rather than four more methods on [BoardController]. That one is about what is
 * *on* a board — which cards exist, what they say, what order they are in — and every one of its
 * endpoints can create or remove a card. Nothing here can. Keeping the one endpoint that accepts a
 * whole client-supplied document away from the ones that mutate cards is worth a file.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(
    name = "Onboarding - Board structure",
    description = "How you have arranged your board: stages, sequences, areas, sizes, marks",
)
class BoardStructureController(
    private val boardStructureService: BoardStructureService,
    private val userApi: UserApi,
) {
    @Operation(
        summary = "How I have arranged my board",
        description = "Stages, what waits on what, hand-set ticks, areas, folds, pins, card " +
            "widths, where a card came from, and what is highlighted in it.\n\n" +
            "A board nobody has arranged answers with an empty arrangement and a null " +
            "`updatedAt` rather than a 404 — having arranged nothing is a normal first day.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Arrangement returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "404", description = "You are not a member of that project"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/me/board/structure")
    @PreAuthorize("hasAnyRole('USER', 'PM', 'HR', 'ADMIN')")
    fun getMyStructure(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam projectId: UUID,
    ): BoardStructureResponse =
        boardStructureService.read(resolveUserId(jwt), projectId)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "You are not a member of that project",
            )

    @Operation(
        summary = "Arrange my board",
        description = "Replaces the whole arrangement. Send all of it rather than the part that " +
            "changed: an arrangement is a statement about the board, and half of one tab's and " +
            "half of another's is an arrangement nobody made.\n\n" +
            "Card ids are taken as given and not checked against the cards that exist — an entry " +
            "for a card dismissed on another device is ordinary, and refusing the write over one " +
            "stale id would lose the other forty entries. The client drops what it cannot resolve.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Arrangement stored"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "404", description = "You are not a member of that project"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/me/board/structure")
    @PreAuthorize("hasAnyRole('USER', 'PM', 'HR', 'ADMIN')")
    fun saveMyStructure(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam projectId: UUID,
        @RequestBody request: SaveBoardStructureRequest,
    ): BoardStructureResponse =
        boardStructureService.write(resolveUserId(jwt), projectId, request.structure)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "You are not a member of that project",
            )

    private fun resolveUserId(jwt: Jwt): UUID =
        userApi.getUserIdByAuthId(jwt.subject).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: ${jwt.subject}")
        }
}
