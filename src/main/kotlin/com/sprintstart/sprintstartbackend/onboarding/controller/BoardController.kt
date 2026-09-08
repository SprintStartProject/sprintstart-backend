package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.request.board.AuthoredCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.ReorderBoardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardCardResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.DiagramContent
import com.sprintstart.sprintstartbackend.onboarding.service.BoardDiagramService
import com.sprintstart.sprintstartbackend.onboarding.service.BoardService
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
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
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
 * The hire's own board.
 *
 * Self-serve only, and the caller is always resolved from the token rather than taken as a
 * parameter: there is no "read somebody else's board" here to get the authorisation wrong on. A PM
 * view of a hire's board was considered and deferred — the board is the hire's working surface, and
 * the PM's read of the same facts already exists on the metrics readout.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(
    name = "Onboarding - Board",
    description = "Your persistent board: the cards the mentor curates and you own",
)
class BoardController(
    private val boardService: BoardService,
    private val boardDiagramService: BoardDiagramService,
    private val userApi: UserApi,
) {
    @Operation(
        summary = "My board on a project",
        description = "Every active card on your board for this project, in board order, each " +
            "with its content read live — so a card and the buddy tool behind it can never say " +
            "different things. The board is created on first read, holding the cards relevant to " +
            "your track.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Board returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "404", description = "You are not a member of that project"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/me/board")
    @PreAuthorize("hasAnyRole('USER', 'PM', 'HR', 'ADMIN')")
    fun getMyBoard(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam projectId: UUID,
    ): BoardResponse =
        boardService.getBoard(resolveUserId(jwt), projectId)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "You are not a member of that project",
            )

    @Operation(
        summary = "Redraw a diagram card if the project has changed",
        description = "Checks this diagram against the project's material as it is *now*. An " +
            "unchanged project returns the same picture without redrawing anything, so calling " +
            "this on every board load is cheap; a project that has moved is redrawn, and one that " +
            "no longer supports the subject returns no picture and says so.\n\n" +
            "Separate from the board read on purpose: assembling a diagram costs a generation, and " +
            "a page that waits on one to open is a page nobody opens. The board serves the last " +
            "picture drawn, dated; this is what makes sure it is still true.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "The current picture, redrawn if it needed to be"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "404", description = "No such diagram card on a board of yours"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/me/board/cards/{cardId}/diagram")
    @PreAuthorize("hasAnyRole('USER', 'PM', 'HR', 'ADMIN')")
    suspend fun refreshDiagram(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable cardId: UUID,
    ): DiagramContent = boardDiagramService.refresh(resolveUserId(jwt), cardId)

    @Operation(
        summary = "Put a card of my own on my board",
        description = "Adds a note, a link or a checklist. It is yours: you can edit it, the buddy " +
            "never touches it, and a board can hold as many of them as you like — unlike the live " +
            "cards, of which there is one each.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Card added"),
            ApiResponse(responseCode = "400", description = "The card would say nothing"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "404", description = "You are not a member of that project"),
        ],
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/me/board/cards")
    @PreAuthorize("hasAnyRole('USER', 'PM', 'HR', 'ADMIN')")
    fun addCard(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam projectId: UUID,
        @RequestBody request: AuthoredCardRequest,
    ): BoardCardResponse = boardService.addAuthoredCard(resolveUserId(jwt), projectId, request)

    @Operation(
        summary = "Change what a card of mine says",
        description = "Replaces the content of a card you wrote — including ticking a checklist " +
            "item, which is an edit to that item. Only your own cards: the live ones read your " +
            "onboarding and have nothing to edit.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Card updated"),
            ApiResponse(responseCode = "400", description = "Empty content, or the wrong kind for that card"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "404", description = "No such card of yours"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PatchMapping("/me/board/cards/{cardId}")
    @PreAuthorize("hasAnyRole('USER', 'PM', 'HR', 'ADMIN')")
    fun editCard(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable cardId: UUID,
        @RequestBody request: AuthoredCardRequest,
    ): BoardCardResponse = boardService.editAuthoredCard(resolveUserId(jwt), cardId, request)

    @Operation(
        summary = "Arrange my board",
        description = "Sets the order of your cards. Send the whole order rather than one move — a " +
            "drag is a statement about the board. Cards you leave out keep their relative order " +
            "after the ones you list.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Board rearranged"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "404", description = "You have no board on that project"),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PutMapping("/me/board/order")
    @PreAuthorize("hasAnyRole('USER', 'PM', 'HR', 'ADMIN')")
    fun reorder(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam projectId: UUID,
        @RequestBody request: ReorderBoardRequest,
    ) {
        boardService.reorder(resolveUserId(jwt), projectId, request.cardIds)
    }

    @Operation(
        summary = "Take a card off my board",
        description = "Removes a card from your board for good. The buddy will not put it back — " +
            "a dismissal is a decision, not a gesture the next page load undoes. Dismissing a " +
            "card that is already gone is not an error.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Card dismissed"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "404", description = "No such card on a board of yours"),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/me/board/cards/{cardId}")
    @PreAuthorize("hasAnyRole('USER', 'PM', 'HR', 'ADMIN')")
    fun dismissCard(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @PathVariable cardId: UUID,
    ) {
        // A card on somebody else's board and a card that does not exist answer the same, on
        // purpose: a 403 here would confirm that a given id is a real card of somebody's.
        if (!boardService.dismiss(resolveUserId(jwt), cardId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No such card on your board")
        }
    }

    private fun resolveUserId(jwt: Jwt): UUID =
        userApi.getUserIdByAuthId(jwt.subject).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: ${jwt.subject}")
        }
}
