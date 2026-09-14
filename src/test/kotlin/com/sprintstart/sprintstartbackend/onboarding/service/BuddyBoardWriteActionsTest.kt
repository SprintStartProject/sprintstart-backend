package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.AuthoredCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.ChecklistCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.LinkCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.NoteCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.buddy.BuddyActionRequest
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.dto.ProjectDto
import com.sprintstart.sprintstartbackend.user.external.dto.UserDto
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
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
 * The four actions that put the mentor's own words on a board.
 *
 * Split from `BuddyActionServiceTest` when its production counterpart was: what these assert is a
 * different promise from the rest of the catalog — not "the right operation ran" but "nothing was
 * written that the hire had not read, and nothing of theirs changed underneath it".
 */
class BuddyBoardWriteActionsTest {
    private val boardService: BoardService = mockk(relaxed = true)
    private val userApi: UserApi = mockk()
    private val boardWrites = BuddyBoardWriteActions(boardService)

    private val service = BuddyActionService(
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        mockk(relaxed = true),
        userApi,
        mockk(relaxed = true),
        boardService,
        mockk(relaxed = true),
        boardWrites,
    )

    private val userId = UUID.randomUUID()
    private val projectId = UUID.randomUUID()
    private val authId = "auth|hire"
    private val jwt: Jwt = mockk<Jwt>().also { every { it.subject } returns authId }

    private fun onOneProject() {
        every { userApi.getUsersByIds(listOf(userId)) } returns listOf(
            UserDto(
                id = userId,
                username = "hire",
                firstname = "Sam",
                lastname = "Hire",
                avatarUrl = null,
                profileIcon = null,
                projects = setOf(ProjectDto(projectId, "Checkout", null)),
                projectRoles = emptyList(),
            ),
        )
    }

    private fun asHire() {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
    }

    // -- place_checklist ---------------------------------------------------------------------------

    private fun checklistCall(title: String, vararg items: String) = BuddyToolCallDto(
        id = "c0",
        name = "place_checklist",
        arguments = buildJsonObject {
            put("title", title)
            putJsonArray("items") { items.forEach { add(it) } }
        },
    )

    /**
     * The only action whose payload is content the model wrote, so the proposal has to carry the
     * lines themselves — a confirm that re-derived them could keep words the hire never read.
     */
    @Test
    fun `proposes a checklist carrying the lines it offered to keep`() {
        onOneProject()

        val outcome = service.propose(
            checklistCall("Getting started", "Find the component", "Run it locally"),
            userId,
        )

        assertThat(outcome.proposal?.action).isEqualTo("place_checklist")
        assertThat(outcome.proposal?.checklistTitle).isEqualTo("Getting started")
        assertThat(outcome.proposal?.checklistItems)
            .containsExactly("Find the component", "Run it locally")
        verify(exactly = 0) { boardService.addAuthoredCard(any(), any(), any()) }
    }

    /** One bullet is how a model emphasises a sentence; a card of it repeats the reply above it. */
    @Test
    fun `refuses to call a single line a checklist`() {
        onOneProject()

        val outcome = service.propose(checklistCall("Getting started", "Find the component"), userId)

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("at least 2")
    }

    @Test
    fun `confirming keeps the proposed lines as a card the hire owns`() = runTest {
        asHire()
        onOneProject()

        val result = service.perform(
            BuddyActionRequest(
                action = "place_checklist",
                checklistTitle = "Getting started",
                checklistItems = listOf("Find the component", "Run it locally"),
            ),
            jwt,
        )

        assertThat(result.ok).isTrue()
        val request = slot<AuthoredCardRequest>()
        verify { boardService.addAuthoredCard(userId, projectId, capture(request)) }
        val checklist = request.captured as ChecklistCardRequest
        assertThat(checklist.title).isEqualTo("Getting started")
        assertThat(checklist.items.map { it.text })
            .containsExactly("Find the component", "Run it locally")
        assertThat(checklist.items).allMatch { !it.done }
    }

    /** Free text from the client, so the caps are re-applied at confirm rather than trusted. */
    @Test
    fun `a confirm stripped of its list writes nothing`() = runTest {
        asHire()
        onOneProject()

        val result = service.perform(
            BuddyActionRequest(
                action = "place_checklist",
                checklistTitle = "Getting started",
                checklistItems = listOf("   ", ""),
            ),
            jwt,
        )

        assertThat(result.ok).isFalse()
        verify(exactly = 0) { boardService.addAuthoredCard(any(), any(), any()) }
    }

    // -- amend_checklist / place_link / place_note -------------------------------------------------

    /**
     * Only the new lines cross the wire. The mentor is never handed the whole list to send back,
     * which is what makes it impossible for it to reword or drop one on the way — the guarantee
     * lives in `BoardService.appendChecklistItems`, and this is the half that keeps it honest.
     */
    @Test
    fun `proposes an amendment carrying the card and only the new lines`() {
        onOneProject()
        val cardId = UUID.randomUUID()

        val outcome = service.propose(
            BuddyToolCallDto(
                id = "c0",
                name = "amend_checklist",
                arguments = buildJsonObject {
                    put("card_id", cardId.toString())
                    putJsonArray("items") {
                        add("Write the test")
                        add("Open a draft PR")
                    }
                },
            ),
            userId,
        )

        assertThat(outcome.proposal?.cardId).isEqualTo(cardId)
        assertThat(outcome.proposal?.checklistItems).containsExactly("Write the test", "Open a draft PR")
        verify(exactly = 0) { boardService.appendChecklistItems(any(), any(), any()) }
    }

    @Test
    fun `does not propose an amendment without a card to amend`() {
        onOneProject()

        val outcome = service.propose(
            BuddyToolCallDto(
                id = "c0",
                name = "amend_checklist",
                arguments = buildJsonObject { putJsonArray("items") { add("Write the test") } },
            ),
            userId,
        )

        assertThat(outcome.proposal).isNull()
        assertThat(outcome.toolResult).contains("read_board")
    }

    @Test
    fun `confirming an amendment appends and says nothing else changed`() = runTest {
        asHire()
        onOneProject()
        val cardId = UUID.randomUUID()

        val result = service.perform(
            BuddyActionRequest(
                action = "amend_checklist",
                cardId = cardId,
                checklistItems = listOf("Write the test"),
            ),
            jwt,
        )

        assertThat(result.ok).isTrue()
        verify { boardService.appendChecklistItems(userId, cardId, listOf("Write the test")) }
    }

    /** A card that is not theirs, or not a checklist: a sentence, not a failed confirm. */
    @Test
    fun `an amendment the board refuses comes back as its own reason`() = runTest {
        asHire()
        onOneProject()
        val cardId = UUID.randomUUID()
        every { boardService.appendChecklistItems(any(), any(), any()) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND, "No such card on your board")

        val result = service.perform(
            BuddyActionRequest(
                action = "amend_checklist",
                cardId = cardId,
                checklistItems = listOf("Write the test"),
            ),
            jwt,
        )

        assertThat(result.ok).isFalse()
        assertThat(result.message).contains("No such card")
    }

    /** A card is a promise that the address works, so only a real one may be offered. */
    @Test
    fun `refuses a link that is not an http address`() {
        onOneProject()

        val outcome = service.propose(
            BuddyToolCallDto(
                id = "c0",
                name = "place_link",
                arguments = buildJsonObject {
                    put("url", "javascript:alert(1)")
                    put("label", "The runbook")
                },
            ),
            userId,
        )

        assertThat(outcome.proposal).isNull()
    }

    @Test
    fun `confirming a link keeps it as a card the hire owns`() = runTest {
        asHire()
        onOneProject()

        val result = service.perform(
            BuddyActionRequest(
                action = "place_link",
                linkUrl = "https://example.test/runbook",
                linkLabel = "The deploy runbook",
            ),
            jwt,
        )

        assertThat(result.ok).isTrue()
        val request = slot<AuthoredCardRequest>()
        verify { boardService.addAuthoredCard(userId, projectId, capture(request)) }
        val link = request.captured as LinkCardRequest
        assertThat(link.url).isEqualTo("https://example.test/runbook")
        assertThat(link.label).isEqualTo("The deploy runbook")
    }

    /** Every reply already carries a button that keeps the whole answer. */
    @Test
    fun `refuses a note too short to be worth its own card`() {
        onOneProject()

        val outcome = service.propose(
            BuddyToolCallDto(
                id = "c0",
                name = "place_note",
                arguments = buildJsonObject { put("text", "Deploys happen on Thursdays.") },
            ),
            userId,
        )

        assertThat(outcome.proposal).isNull()
    }

    @Test
    fun `confirming a note keeps its words as the hire's card`() = runTest {
        asHire()
        onOneProject()
        val text = "Deploys run on Thursdays, behind a feature flag that the release lead flips " +
            "once the smoke tests are green. Nobody deploys on a Friday."

        val result = service.perform(
            BuddyActionRequest(action = "place_note", noteText = text),
            jwt,
        )

        assertThat(result.ok).isTrue()
        val request = slot<AuthoredCardRequest>()
        verify { boardService.addAuthoredCard(userId, projectId, capture(request)) }
        assertThat((request.captured as NoteCardRequest).text).isEqualTo(text)
    }

    // -- tick_checklist_items ----------------------------------------------------------------------

    /**
     * The hire's own statement about their own work. Offered when they say it, never concluded —
     * the tool description carries that half; this asserts the mechanics under it.
     */
    @Test
    fun `proposes ticks carrying the card and the lines as written`() {
        onOneProject()
        val cardId = UUID.randomUUID()

        val outcome = service.propose(
            BuddyToolCallDto(
                id = "c0",
                name = "tick_checklist_items",
                arguments = buildJsonObject {
                    put("card_id", cardId.toString())
                    putJsonArray("items") {
                        add("Reproduce it locally")
                        add("Add a failing test")
                    }
                },
            ),
            userId,
        )

        assertThat(outcome.proposal?.cardId).isEqualTo(cardId)
        assertThat(outcome.proposal?.checklistItems)
            .containsExactly("Reproduce it locally", "Add a failing test")
        verify(exactly = 0) { boardService.tickChecklistItems(any(), any(), any()) }
    }

    @Test
    fun `confirming ticks the named lines and says how many`() = runTest {
        asHire()
        onOneProject()
        val cardId = UUID.randomUUID()
        every { boardService.tickChecklistItems(userId, cardId, listOf("Reproduce it locally")) } returns 1

        val result = service.perform(
            BuddyActionRequest(
                action = "tick_checklist_items",
                cardId = cardId,
                checklistItems = listOf("Reproduce it locally"),
            ),
            jwt,
        )

        assertThat(result.ok).isTrue()
        assertThat(result.message).contains("1")
    }

    /**
     * Silence would let a line the mentor paraphrased look like a line it ticked, and the hire
     * would find out by looking at a card that had not changed.
     */
    @Test
    fun `a line that matches nothing comes back as nothing changed`() = runTest {
        asHire()
        onOneProject()
        val cardId = UUID.randomUUID()
        every { boardService.tickChecklistItems(any(), any(), any()) } returns 0

        val result = service.perform(
            BuddyActionRequest(
                action = "tick_checklist_items",
                cardId = cardId,
                checklistItems = listOf("something it made up"),
            ),
            jwt,
        )

        assertThat(result.ok).isFalse()
        assertThat(result.message).contains("Nothing changed")
    }
}
