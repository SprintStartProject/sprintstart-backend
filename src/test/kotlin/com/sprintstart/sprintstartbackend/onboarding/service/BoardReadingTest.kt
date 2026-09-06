package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardOwner
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardStage
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CardDependencySource
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardStructurePayload
import com.sprintstart.sprintstartbackend.onboarding.model.entity.CardDependencyPayload
import com.sprintstart.sprintstartbackend.onboarding.model.entity.CardStructurePayload
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardCardResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.ChecklistContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.ChecklistItemResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.LinkContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.NoteContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.OpenPullRequestsContent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class BoardReadingTest {
    private val one = UUID.randomUUID()
    private val two = UUID.randomUUID()

    @Test
    fun `a card is called what the hire would call it`() {
        assertEquals("Deploys are on Thursdays", BoardReading.nameOf(note(one, "Deploys are on Thursdays\n\nMore.")))
        assertEquals("The runbook", BoardReading.nameOf(link(one, "The runbook")))
        // With no label, the address: worse to read, always true, and never invented from the URL.
        assertEquals("https://example.com", BoardReading.nameOf(link(one, null)))
        assertEquals("a checklist", BoardReading.nameOf(checklist(one, null, done = 0, open = 2)))
    }

    @Test
    fun `a live card is named by what it is, in words rather than in an enum`() {
        // Its real content, not a note wearing the kind: the name is read off the content, because
        // that is the union the catalog closes over — the `kind` beside it is the same fact said
        // twice, and a card whose two halves disagree is not a card the board can produce.
        val card = BoardCardResponse(
            id = one,
            kind = BoardCardKind.OPEN_PULL_REQUESTS,
            owner = BoardCardOwner.AI,
            position = 0,
            placedAt = null,
            content = OpenPullRequestsContent(pullRequests = emptyList(), attributionMissing = false),
        )

        assertEquals("open pull requests", BoardReading.nameOf(card))
    }

    @Test
    fun `an empty checklist is not finished`() {
        // Zero of zero is a list nobody has written yet, and calling it done would let a blank card
        // unblock everything behind it.
        val empty = checklist(one, "Paperwork", done = 0, open = 0)

        assertFalse(BoardReading.isDone(empty, BoardStructurePayload()))
        assertTrue(BoardReading.isDone(checklist(one, "Paperwork", done = 2, open = 0), BoardStructurePayload()))
    }

    @Test
    fun `a card nothing can observe is finished when the hire says so`() {
        val structure = BoardStructurePayload(
            cards = mapOf(one.toString() to CardStructurePayload(markedDone = true)),
        )

        assertTrue(BoardReading.isDone(note(one, "read this"), structure))
        assertFalse(BoardReading.isDone(note(one, "read this"), BoardStructurePayload()))
    }

    @Test
    fun `a dependency on a card that has left the board does not block forever`() {
        val cards = listOf(note(one, "still here"))
        val structure = BoardStructurePayload(
            cards = mapOf(
                one.toString() to CardStructurePayload(
                    dependsOn = listOf(CardDependencyPayload(UUID.randomUUID().toString(), CardDependencySource.HIRE)),
                ),
            ),
        )

        assertTrue(BoardReading.blockedBy(cards.first(), cards, structure).isEmpty())
    }

    @Test
    fun `what they can pick up comes back in their own order, later last`() {
        val first = note(one, "later one")
        val second = note(two, "now one")
        val cards = listOf(first, second)
        val structure = BoardStructurePayload(
            cards = mapOf(one.toString() to CardStructurePayload(stage = BoardStage.LATER)),
        )

        // A LATER card is the hire or their PM having said "not yet", so it comes after — but it is
        // still offered, because putting something aside is not the same as locking it.
        assertEquals(listOf(two, one), BoardReading.actionable(cards, structure).map { it.id })
    }

    @Test
    fun `a blocked card is not offered`() {
        val blocker = note(one, "read the runbook")
        val blocked = note(two, "deploy something")
        val cards = listOf(blocker, blocked)
        val structure = BoardStructurePayload(
            cards = mapOf(
                two.toString() to CardStructurePayload(
                    dependsOn = listOf(CardDependencyPayload(one.toString(), CardDependencySource.TEAM)),
                ),
            ),
        )

        assertEquals(listOf(one), BoardReading.actionable(cards, structure).map { it.id })
    }

    private fun note(id: UUID, text: String) = BoardCardResponse(
        id = id,
        kind = BoardCardKind.NOTE,
        owner = BoardCardOwner.HIRE,
        position = if (id == one) 0 else 1,
        placedAt = null,
        content = NoteContent(text = text),
    )

    private fun link(id: UUID, label: String?) = BoardCardResponse(
        id = id,
        kind = BoardCardKind.LINK,
        owner = BoardCardOwner.HIRE,
        position = 0,
        placedAt = null,
        content = LinkContent(url = "https://example.com", label = label),
    )

    private fun checklist(id: UUID, title: String?, done: Int, open: Int) = BoardCardResponse(
        id = id,
        kind = BoardCardKind.CHECKLIST,
        owner = BoardCardOwner.HIRE,
        position = 0,
        placedAt = null,
        content = ChecklistContent(
            title = title,
            items = List(done) { item(true) } + List(open) { item(false) },
        ),
    )

    private fun item(done: Boolean) =
        ChecklistItemResponse(id = UUID.randomUUID(), text = "a line", done = done)
}
