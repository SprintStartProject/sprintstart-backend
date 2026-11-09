package com.sprintstart.sprintstartbackend.board.assistant

import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.HireActionContext
import com.sprintstart.sprintstartbackend.assistant.external.HireActionRequest
import com.sprintstart.sprintstartbackend.assistant.external.HireProject
import com.sprintstart.sprintstartbackend.board.external.BoardReading
import com.sprintstart.sprintstartbackend.board.external.HireBoardActionsApi
import com.sprintstart.sprintstartbackend.board.external.HireBoardApi
import com.sprintstart.sprintstartbackend.board.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.board.external.enums.BoardCardOwner
import com.sprintstart.sprintstartbackend.board.model.response.board.BoardCardContent
import com.sprintstart.sprintstartbackend.board.model.response.board.BoardCardResponse
import com.sprintstart.sprintstartbackend.board.model.response.board.BoardResponse
import com.sprintstart.sprintstartbackend.board.model.response.board.ChecklistContent
import com.sprintstart.sprintstartbackend.board.model.response.board.ChecklistItemResponse
import com.sprintstart.sprintstartbackend.board.model.response.board.NoteContent
import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * The limits the board actions draw, each pinned at its edge.
 *
 * The routed cases in `BuddyBoardWritesTest` and `BuddyBoardEditsTest` show each action doing its job;
 * these pin where a limit sits, so moving one by a character or a card is a failing test rather than a
 * silent change to what the mentor may put on somebody's board.
 */
class BoardActionBoundariesTest {
    private val hireBoard: HireBoardApi = mockk()
    private val boardActions: HireBoardActionsApi = mockk(relaxed = true)
    private val context =
        HireActionContext(UserId(UUID.randomUUID()), HireProject(ProjectId(UUID.randomUUID()), "Checkout"))

    private fun call(name: String, arguments: JsonObject) = BuddyToolCallDto(
        id = "c1",
        name = name,
        arguments = arguments,
    )

    private fun onBoard(vararg cards: BoardCardResponse) {
        every { hireBoard.hasBoard(context.board) } returns true
        every { hireBoard.boardOf(context.board) } returns
            BoardResponse(UUID.randomUUID(), UUID.randomUUID(), cards.toList())
    }

    private fun hireCard(kind: BoardCardKind, content: BoardCardContent) =
        BoardCardResponse(
            UUID.randomUUID(),
            kind,
            BoardCardOwner.HIRE,
            position = 0,
            placedAt = null,
            content = content,
        )

    private fun cardIds(vararg ids: String) = buildJsonObject { putJsonArray("card_ids") { ids.forEach { add(it) } } }

    @Test
    fun `an offer tells the mentor it is an offer, on the hire's project`() {
        val outcome = PlaceNoteAction(boardActions).propose(
            call("place_note", buildJsonObject { put("text", "n".repeat(MIN_NOTE_LENGTH)) }),
            context,
        )

        assertThat(outcome.toolResult).contains("on Checkout", "“Keep this as a note”", "do not claim it is done")
    }

    @Test
    fun `a note is worth a card from exactly the minimum length`() {
        val action = PlaceNoteAction(boardActions)

        val atMinimum = action.propose(
            call(
                "place_note",
                buildJsonObject {
                    put("text", "n".repeat(MIN_NOTE_LENGTH))
                },
            ),
            context,
        )
        val short = action.propose(
            call(
                "place_note",
                buildJsonObject {
                    put("text", "n".repeat(MIN_NOTE_LENGTH - 1))
                },
            ),
            context,
        )

        assertThat(atMinimum.proposal).isNotNull()
        assertThat(short.proposal).isNull()
    }

    @Test
    fun `a link is only ever a web address, and only up to the length cap`() {
        val action = PlaceLinkAction(boardActions)

        fun offered(url: String) = action
            .propose(
                call("place_link", buildJsonObject { put("url", url) }),
                context,
            ).proposal

        val longest = "https://x.dev/" + "a".repeat(MAX_URL_LENGTH - "https://x.dev/".length)
        assertThat(offered(longest)).isNotNull()
        assertThat(offered(longest + "a")).isNull()
        assertThat(offered("javascript:alert(1)")).isNull()
        assertThat(offered("HTTP://example.com")).isNotNull()
    }

    @Test
    fun `a link's preview names it only when it has a name`() {
        val action = PlaceLinkAction(boardActions)

        val named = action.propose(
            call(
                "place_link",
                buildJsonObject {
                    put("url", "https://x.dev")
                    put("label", "Runbook")
                },
            ),
            context,
        )
        val unnamed = action.propose(
            call(
                "place_link",
                buildJsonObject {
                    put("url", "https://x.dev")
                    put("label", "  ")
                },
            ),
            context,
        )

        assertThat(named.proposal?.preview).isEqualTo("Add a link to your board: Runbook — https://x.dev")
        assertThat(unnamed.proposal?.preview).isEqualTo("Add a link to your board: https://x.dev")
        assertThat(unnamed.proposal?.linkLabel).isNull()
    }

    @Test
    fun `a confirmed link that is not a web address writes nothing`() = runTest {
        val result = PlaceLinkAction(boardActions).perform(
            HireActionRequest(action = "place_link", linkUrl = "file:///etc/passwd"),
            context,
            AuthId("auth|hire"),
        )

        assertThat(result.ok).isFalse()
        verify(exactly = 0) { boardActions.addCard(any(), any()) }
    }

    @Test
    fun `one proposal covers up to the card cap and refuses past it`() {
        every { hireBoard.hasBoard(context.board) } returns false
        val action = DismissCardsAction(hireBoard, boardActions)
        val ids = List(MAX_CARDS + 1) { UUID.randomUUID().toString() }

        val atCap = action.propose(call("dismiss_cards", cardIds(*ids.take(MAX_CARDS).toTypedArray())), context)
        val pastCap = action.propose(call("dismiss_cards", cardIds(*ids.toTypedArray())), context)

        assertThat(atCap.toolResult).startsWith("None of those ids")
        assertThat(pastCap.toolResult).contains("at most $MAX_CARDS")
    }

    @Test
    fun `a blank id in the list is counted as not an id, not quietly dropped`() {
        val card = hireCard(BoardCardKind.NOTE, NoteContent(text = "Deploys"))
        onBoard(card)

        val outcome = ReorderCardsAction(hireBoard, boardActions).propose(
            call("reorder_cards", cardIds(card.id.toString(), "  ")),
            context,
        )

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).startsWith("1 of those ids are not card ids")
    }

    @Test
    fun `naming some cards that are not on the board proposes nothing`() {
        val card = hireCard(BoardCardKind.NOTE, NoteContent(text = "Deploys"))
        onBoard(card)

        val outcome = DismissCardsAction(hireBoard, boardActions).propose(
            call("dismiss_cards", cardIds(card.id.toString(), UUID.randomUUID().toString())),
            context,
        )

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).startsWith("1 of those ids are not on the hire's board")
    }

    @Test
    fun `a note exactly as long as read_board shows can still be rewritten`() {
        val visible = hireCard(BoardCardKind.NOTE, NoteContent(text = "x".repeat(BoardReading.NOTE_PREVIEW)))
        val cut = hireCard(BoardCardKind.NOTE, NoteContent(text = "x".repeat(BoardReading.NOTE_PREVIEW + 1)))
        onBoard(visible, cut)
        val action = EditNoteAction(hireBoard, boardActions)

        fun edit(id: UUID) = action.propose(
            call(
                "edit_note",
                buildJsonObject {
                    put("card_id", id.toString())
                    put("text", "Shorter")
                },
            ),
            context,
        )

        assertThat(edit(visible.id).proposal).isNotNull()
        assertThat(edit(cut.id).proposal).isNull()
    }

    @Test
    fun `a line kept once from a list that had it twice says one copy goes`() {
        val list = hireCard(
            BoardCardKind.CHECKLIST,
            ChecklistContent(
                title = "Setup",
                items = listOf(
                    "Clone",
                    "Clone",
                    "Run tests",
                ).map { ChecklistItemResponse(UUID.randomUUID(), it, false) },
            ),
        )
        onBoard(list)

        val outcome = EditChecklistAction(hireBoard, boardActions).propose(
            call(
                "edit_checklist",
                buildJsonObject {
                    put("card_id", list.id.toString())
                    put("title", "  ")
                    putJsonArray("items") {
                        add("clone ")
                        add("Run tests")
                    }
                },
            ),
            context,
        )

        assertThat(outcome.proposal?.preview).contains("Lines that would go: “Clone”.")
        assertThat(outcome.proposal?.checklistTitle).isNull()
    }
}
