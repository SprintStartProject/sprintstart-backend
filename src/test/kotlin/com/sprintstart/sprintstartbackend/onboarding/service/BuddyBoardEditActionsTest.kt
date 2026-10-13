package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardActor
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardOwner
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.model.entity.NotePayload
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.AuthoredCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.ChecklistCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.LinkCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.NoteCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.buddy.BuddyActionRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardCardContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardCardResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.ChecklistContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.ChecklistItemResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.CurrentTaskContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.LinkContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.NoteContent
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.dto.ProjectDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserDto
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID

/**
 * The board actions that reach past adding: editing, clearing and rearranging what the hire has.
 *
 * What these pin is the bargain that lets the buddy do this at all: nothing is written on
 * proposal, the names a confirm shows come from the board rather than from the model, the
 * one-project scoping still refuses, and every confirmed write lands as the buddy's.
 */
class BuddyBoardEditActionsTest {
    private val boardService: BoardService = mockk(relaxed = true)
    private val userApi: UserApi = mockk()

    private val service = BuddyActionService(
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        userApi,
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        BuddyBoardWriteActions(boardService, BuddyBoardEditActions(boardService)),
    )

    private val userId = UUID.randomUUID()
    private val projectId = UUID.randomUUID()
    private val authId = "auth|hire"
    private val jwt: Jwt = mockk<Jwt>().also { every { it.subject } returns authId }

    private val noteId = UUID.randomUUID()
    private val linkId = UUID.randomUUID()
    private val checklistId = UUID.randomUUID()
    private val taskCardId = UUID.randomUUID()

    private val cards = listOf(
        card(noteId, BoardCardKind.NOTE, BoardCardOwner.HIRE, NoteContent(text = "Deploys run on Thursdays")),
        card(linkId, BoardCardKind.LINK, BoardCardOwner.HIRE, LinkContent(url = "https://wiki", label = "Runbook")),
        card(
            checklistId,
            BoardCardKind.CHECKLIST,
            BoardCardOwner.HIRE,
            ChecklistContent(
                title = "Getting started",
                items = listOf(
                    ChecklistItemResponse(id = UUID.randomUUID(), text = "Run it locally", done = true),
                    ChecklistItemResponse(id = UUID.randomUUID(), text = "Fix it", done = false),
                ),
            ),
        ),
        card(
            taskCardId,
            BoardCardKind.CURRENT_TASK,
            BoardCardOwner.AI,
            CurrentTaskContent(
                taskId = null,
                title = null,
                summary = null,
                url = null,
                closedAtSource = false,
            ),
        ),
    )

    /** What the board holds *now*; a test reassigns it to change a card between propose and confirm. */
    private var boardCards = cards

    private fun card(
        id: UUID,
        kind: BoardCardKind,
        owner: BoardCardOwner,
        content: BoardCardContent,
    ) = BoardCardResponse(id = id, kind = kind, owner = owner, position = 0, placedAt = null, content = content)

    private fun onProjects(vararg projects: ProjectDto) {
        every { userApi.getUsersByIds(listOf(userId)) } returns listOf(
            UserDto(
                id = userId,
                username = "hire",
                firstname = "Sam",
                lastname = "Hire",
                avatarUrl = null,
                profileIcon = null,
                projects = projects.toSet(),
                projectRoles = emptyList(),
            ),
        )
    }

    private fun onOneProjectWithBoard() {
        onProjects(ProjectDto(projectId, "Checkout", null))
        every { boardService.hasBoard(userId, projectId) } returns true
        every { boardService.getBoard(userId, projectId) } answers {
            BoardResponse(UUID.randomUUID(), projectId, boardCards)
        }
    }

    /** The hire confirming: who they are, and the board as it stands. */
    private fun asHireOnBoard() {
        onOneProjectWithBoard()
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
    }

    private fun proposeEdit(name: String, arguments: JsonObject): BuddyActionService.BuddyActionProposal =
        service.propose(call(name, arguments), userId).proposal ?: error("no proposal for $name")

    private fun editedNote(text: String) = buildJsonObject {
        put("card_id", noteId.toString())
        put("text", text)
    }

    private fun editedChecklist(vararg lines: String) = buildJsonObject {
        put("card_id", checklistId.toString())
        putJsonArray("items") { lines.forEach { add(it) } }
    }

    private fun withCard(id: UUID, content: BoardCardContent) {
        boardCards = boardCards.map { if (it.id == id) it.copy(content = content) else it }
    }

    private fun asHire() {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        onProjects(ProjectDto(projectId, "Checkout", null))
    }

    private fun call(name: String, arguments: JsonObject) = BuddyToolCallDto(
        id = "c0",
        name = name,
        arguments = arguments,
    )

    private fun ids(vararg ids: UUID) = buildJsonObject {
        putJsonArray(
            "card_ids",
        ) { ids.forEach { add(it.toString()) } }
    }

    private fun assertNothingWritten() {
        verify(exactly = 0) { boardService.dismissForBuddy(any(), any(), any()) }
        verify(exactly = 0) { boardService.reorderForBuddy(any(), any(), any()) }
        verify(exactly = 0) { boardService.editAuthoredCardForBuddy(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { boardService.addAuthoredCard(any(), any(), any(), any()) }
    }

    // -- dismiss_cards / reorder_cards -------------------------------------------------------------

    /**
     * "Clean up my board": the confirm names each card as the board calls it — never as the model
     * described it — and nothing leaves until the hire clicks.
     */
    @Test
    fun `proposing a clean-up names the cards from the board and writes nothing`() {
        onOneProjectWithBoard()

        val outcome = service.propose(call("dismiss_cards", ids(noteId, taskCardId)), userId)

        val proposal = outcome.proposal
        assertThat(proposal?.action).isEqualTo("dismiss_cards")
        assertThat(proposal?.cardIds).containsExactly(noteId, taskCardId)
        assertThat(proposal?.cardNames).containsExactly("Deploys run on Thursdays", "current task")
        assertThat(proposal?.preview).contains("Take 2 cards off your board").contains("brought back")
        assertNothingWritten()
    }

    /** One wrong id is not quietly dropped: the model would describe the proposal as covering it. */
    @Test
    fun `a clean-up naming a card that is not on the board is refused whole`() {
        onOneProjectWithBoard()

        val outcome = service.propose(call("dismiss_cards", ids(noteId, UUID.randomUUID())), userId)

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("1 of those ids are not on the hire's board")
    }

    /** The same rule for an id that is not an id at all: dropping it would shrink the proposal silently. */
    @Test
    fun `a clean-up naming a malformed id is refused whole`() {
        onOneProjectWithBoard()
        val arguments = buildJsonObject {
            putJsonArray("card_ids") {
                add(noteId.toString())
                add("bad-id")
            }
        }

        val outcome = service.propose(call("dismiss_cards", arguments), userId)

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("1 of those ids are not card ids")
        assertNothingWritten()
    }

    @Test
    fun `a clean-up whose list holds a blank or an object entry is refused whole`() {
        onOneProjectWithBoard()
        val blank = buildJsonObject {
            putJsonArray("card_ids") {
                add(noteId.toString())
                add(" ")
            }
        }
        val objectEntry = buildJsonObject {
            putJsonArray("card_ids") {
                add(noteId.toString())
                add(buildJsonObject { put("id", "bad-id") })
            }
        }

        listOf(blank, objectEntry).forEach { arguments ->
            val outcome = service.propose(call("dismiss_cards", arguments), userId)

            assertThat(outcome.proposal).isNull()
            assertThat(outcome.toolResult).contains("1 of those ids are not card ids")
        }
        assertNothingWritten()
    }

    /** A cap that cut the list would drop the ids past it, unknown ones included, without a word. */
    @Test
    fun `a clean-up naming more cards than one proposal covers is refused, not cut short`() {
        onOneProjectWithBoard()
        val arguments = buildJsonObject {
            putJsonArray("card_ids") {
                add(noteId.toString())
                repeat(60) { add(UUID.randomUUID().toString()) }
            }
        }

        val outcome = service.propose(call("dismiss_cards", arguments), userId)

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("more than one proposal can cover")
        assertNothingWritten()
    }

    /** Asking about a board must not be what brings one into existence. */
    @Test
    fun `proposing against a board the hire never opened reads nothing into existence`() {
        onProjects(ProjectDto(projectId, "Checkout", null))
        every { boardService.hasBoard(userId, projectId) } returns false

        val outcome = service.propose(call("reorder_cards", ids(noteId)), userId)

        assertThat(outcome.proposal).isNull()
        verify(exactly = 0) { boardService.getBoard(any(), any()) }
    }

    /** Scoping is unchanged: a hire on two projects is asked which, before any board is touched. */
    @Test
    fun `a hire on more than one project is asked which before anything is proposed`() {
        onProjects(ProjectDto(projectId, "Checkout", null), ProjectDto(UUID.randomUUID(), "Search", null))

        val outcome = service.propose(call("dismiss_cards", ids(noteId)), userId)

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("Ask which one")
        verify(exactly = 0) { boardService.hasBoard(any(), any()) }
    }

    @Test
    fun `proposing an order carries the cards in the order given`() {
        onOneProjectWithBoard()

        val outcome = service.propose(call("reorder_cards", ids(checklistId, noteId)), userId)

        assertThat(outcome.proposal?.cardIds).containsExactly(checklistId, noteId)
        assertThat(outcome.proposal?.preview).contains("Getting started, Deploys run on Thursdays")
    }

    @Test
    fun `confirming a clean-up dismisses exactly the confirmed cards on the resolved project`() = runTest {
        asHire()
        every { boardService.dismissForBuddy(userId, projectId, listOf(noteId, linkId)) } returns 2

        val result = service.perform(
            BuddyActionRequest(action = "dismiss_cards", cardIds = listOf(noteId, linkId)),
            jwt,
        )

        assertThat(result.ok).isTrue()
        assertThat(result.message).contains("Took 2 off your board")
        verify { boardService.dismissForBuddy(userId, projectId, listOf(noteId, linkId)) }
    }

    @Test
    fun `confirming an order rearranges the board on the resolved project`() = runTest {
        asHire()
        every { boardService.reorderForBuddy(userId, projectId, listOf(checklistId)) } returns 3

        val result = service.perform(BuddyActionRequest(action = "reorder_cards", cardIds = listOf(checklistId)), jwt)

        assertThat(result.ok).isTrue()
        assertThat(result.message).contains("3 cards moved")
    }

    // -- edit_note / edit_link / edit_checklist ----------------------------------------------------

    /** "Can you edit this note": the confirm carries the whole new text, and nothing is written yet. */
    @Test
    fun `proposing a note edit carries the new text for the named note`() {
        onOneProjectWithBoard()

        val outcome = service.propose(
            call(
                "edit_note",
                buildJsonObject {
                    put("card_id", noteId.toString())
                    put("text", "Deploys run on Tuesdays")
                },
            ),
            userId,
        )

        assertThat(outcome.proposal?.cardId).isEqualTo(noteId)
        assertThat(outcome.proposal?.noteText).isEqualTo("Deploys run on Tuesdays")
        assertNothingWritten()
    }

    /** The id must be a note of the hire's: a link's id, or a live card's, is refused at proposal. */
    @Test
    fun `a note edit aimed at a card that is not one of their notes is refused`() {
        onOneProjectWithBoard()

        listOf(linkId, taskCardId).forEach { id ->
            val outcome = service.propose(
                call(
                    "edit_note",
                    buildJsonObject {
                        put("card_id", id.toString())
                        put("text", "Something else")
                    },
                ),
                userId,
            )
            assertThat(outcome.proposal).isNull()
            assertThat(outcome.toolResult).contains("not a note on the hire's board")
        }
    }

    /**
     * read_board shows only the start of a long note, so a rewrite would drop the rest — a loss the
     * hire could not see on the offer. Refused at proposal rather than trusted to the prompt.
     */
    @Test
    fun `a note longer than read_board shows is never offered for a rewrite`() {
        onProjects(ProjectDto(projectId, "Checkout", null))
        val longNote = card(
            noteId,
            BoardCardKind.NOTE,
            BoardCardOwner.HIRE,
            NoteContent(text = "x".repeat(BoardReading.NOTE_PREVIEW + 1)),
        )
        every { boardService.hasBoard(userId, projectId) } returns true
        every { boardService.getBoard(userId, projectId) } returns
            BoardResponse(UUID.randomUUID(), projectId, listOf(longNote))

        val outcome = service.propose(
            call(
                "edit_note",
                buildJsonObject {
                    put("card_id", noteId.toString())
                    put("text", "A shorter note")
                },
            ),
            userId,
        )

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("longer than read_board shows")
    }

    /** A link retargeted without a name keeps the one it had, rather than losing it to a whole-card write. */
    @Test
    fun `a link edit that leaves out the name keeps the current one`() {
        onOneProjectWithBoard()

        val outcome = service.propose(
            call(
                "edit_link",
                buildJsonObject {
                    put("card_id", linkId.toString())
                    put("url", "https://wiki/runbook-v2")
                },
            ),
            userId,
        )

        assertThat(outcome.proposal?.linkUrl).isEqualTo("https://wiki/runbook-v2")
        assertThat(outcome.proposal?.linkLabel).isEqualTo("Runbook")
        assertThat(outcome.proposal?.preview).contains("Runbook — https://wiki/runbook-v2")
    }

    /** The preview counts what the edit will really remove: one line kept once of two is one gone. */
    @Test
    fun `a checklist edit that keeps a repeated line once says the other copy goes`() {
        onProjects(ProjectDto(projectId, "Checkout", null))
        val repeated = card(
            checklistId,
            BoardCardKind.CHECKLIST,
            BoardCardOwner.HIRE,
            ChecklistContent(
                title = "Setup",
                items = List(2) { ChecklistItemResponse(id = UUID.randomUUID(), text = "Set up laptop", done = false) },
            ),
        )
        every { boardService.hasBoard(userId, projectId) } returns true
        every { boardService.getBoard(userId, projectId) } returns
            BoardResponse(UUID.randomUUID(), projectId, listOf(repeated))

        val outcome = service.propose(
            call(
                "edit_checklist",
                buildJsonObject {
                    put("card_id", checklistId.toString())
                    putJsonArray("items") { add("set up laptop") }
                },
            ),
            userId,
        )

        assertThat(outcome.proposal?.preview).contains("Lines that would go: “Set up laptop”")
    }

    /** A line silently dropping out of a long list is the change a hire would not notice. */
    @Test
    fun `a checklist edit names every line that would go`() {
        onOneProjectWithBoard()

        val outcome = service.propose(
            call(
                "edit_checklist",
                buildJsonObject {
                    put("card_id", checklistId.toString())
                    put("title", "Getting started")
                    putJsonArray("items") {
                        add("run it locally")
                        add("Open a PR")
                    }
                },
            ),
            userId,
        )

        assertThat(outcome.proposal?.checklistItems).containsExactly("run it locally", "Open a PR")
        assertThat(outcome.proposal?.preview).contains("Lines that would go: “Fix it”")
        assertThat(outcome.proposal?.preview).doesNotContain("“Run it locally”")
    }

    @Test
    fun `confirming a note edit writes it as the buddy's change to that card`() = runTest {
        asHireOnBoard()
        val proposal = proposeEdit("edit_note", editedNote("Deploys run on Tuesdays"))

        val result = service.perform(
            BuddyActionRequest(
                action = "edit_note",
                cardId = noteId,
                noteText = "Deploys run on Tuesdays",
                basedOn = proposal.basedOn,
            ),
            jwt,
        )

        assertThat(result.ok).isTrue()
        val request = slot<AuthoredCardRequest>()
        verify { boardService.editAuthoredCardForBuddy(userId, projectId, noteId, capture(request), proposal.basedOn) }
        assertThat(request.captured).isEqualTo(NoteCardRequest(text = "Deploys run on Tuesdays"))
    }

    @Test
    fun `confirming a checklist edit sends the lines without ids, for the service to match back`() = runTest {
        asHireOnBoard()
        val proposal = proposeEdit("edit_checklist", editedChecklist("Run it locally", "Open a PR"))

        service.perform(
            BuddyActionRequest(
                action = "edit_checklist",
                cardId = checklistId,
                checklistTitle = "First week",
                checklistItems = listOf("Run it locally", "  ", "Open a PR"),
                basedOn = proposal.basedOn,
            ),
            jwt,
        )

        val request = slot<AuthoredCardRequest>()
        verify {
            boardService.editAuthoredCardForBuddy(
                userId,
                projectId,
                checklistId,
                capture(request),
                proposal.basedOn,
            )
        }
        val checklist = request.captured as ChecklistCardRequest
        assertThat(checklist.title).isEqualTo("First week")
        assertThat(checklist.items.map { it.text }).containsExactly("Run it locally", "Open a PR")
        assertThat(checklist.items).allMatch { it.id == null }
    }

    /**
     * Whether the card is still the one the proposal read is decided by `BoardService`, under the
     * card's lock. What is owed here is carrying the fingerprint there, and saying the refusal.
     */
    @Test
    fun `a stale edit is refused by the service and comes back as the reason`() = runTest {
        asHireOnBoard()
        val proposal = proposeEdit("edit_checklist", editedChecklist("Run it locally", "Fix it"))
        every { boardService.editAuthoredCardForBuddy(any(), any(), any(), any(), any()) } throws
            ResponseStatusException(HttpStatus.CONFLICT, BoardCardVersion.CARD_CHANGED)

        val result = service.perform(
            BuddyActionRequest(
                action = "edit_checklist",
                cardId = checklistId,
                checklistItems = listOf("Run it locally", "Fix it"),
                basedOn = proposal.basedOn,
            ),
            jwt,
        )

        assertThat(result.ok).isFalse()
        assertThat(result.message).contains("changed after this was proposed")
    }

    /** The fingerprint is of the card as read, so the same words give the same one from either side. */
    @Test
    fun `the proposal's fingerprint is the one the stored card gives`() = runTest {
        asHireOnBoard()
        val note = proposeEdit("edit_note", editedNote("Deploys run on Tuesdays"))
        val checklist = proposeEdit("edit_checklist", editedChecklist("Run it locally", "Fix it"))

        assertThat(note.basedOn)
            .isEqualTo(BoardCardVersion.of(NotePayload(text = "Deploys run on Thursdays")))
        assertThat(checklist.basedOn).isNotNull()
    }

    /** A card the hire has since removed or changed comes back as a sentence, not a failed confirm. */
    @Test
    fun `a confirm whose card has gone comes back as the reason`() = runTest {
        asHire()
        every { boardService.editAuthoredCardForBuddy(any(), any(), any(), any(), any()) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND, "No such note on your board")

        val result = service.perform(BuddyActionRequest(action = "edit_note", cardId = noteId, noteText = "New"), jwt)

        assertThat(result.ok).isFalse()
        assertThat(result.message).isEqualTo("No such note on your board")
    }

    // -- place_link / edit_link --------------------------------------------------------------------

    /** A link the buddy writes is one the hire will click trusting it. */
    @Test
    fun `a link that is not a web address is never offered`() {
        onOneProjectWithBoard()

        val outcome = service.propose(
            call("place_link", buildJsonObject { put("url", "javascript:alert(1)") }),
            userId,
        )

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("not a web address")
    }

    @Test
    fun `confirming a link keeps it as the hire's card, written by the buddy`() = runTest {
        asHire()

        val result = service.perform(
            BuddyActionRequest(action = "place_link", linkUrl = "https://wiki/runbook", linkLabel = "Runbook"),
            jwt,
        )

        assertThat(result.ok).isTrue()
        val request = slot<AuthoredCardRequest>()
        verify { boardService.addAuthoredCard(userId, projectId, capture(request), BoardActor.BUDDY) }
        assertThat(request.captured).isEqualTo(LinkCardRequest(url = "https://wiki/runbook", label = "Runbook"))
    }

    /** Re-checked at confirm: a client that swaps the address for a script gets nothing written. */
    @Test
    fun `a confirmed link edit to a non-web address writes nothing`() = runTest {
        asHire()

        val result = service.perform(
            BuddyActionRequest(action = "edit_link", cardId = linkId, linkUrl = "file:///etc/passwd"),
            jwt,
        )

        assertThat(result.ok).isFalse()
        assertNothingWritten()
    }
}
