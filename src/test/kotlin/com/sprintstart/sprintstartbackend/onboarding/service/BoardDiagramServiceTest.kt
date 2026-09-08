package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardOwner
import com.sprintstart.sprintstartbackend.onboarding.external.model.AiProvenanceSchema
import com.sprintstart.sprintstartbackend.onboarding.external.model.CitationRefSchema
import com.sprintstart.sprintstartbackend.onboarding.external.model.DiagramEdgeSchema
import com.sprintstart.sprintstartbackend.onboarding.external.model.DiagramNodeSchema
import com.sprintstart.sprintstartbackend.onboarding.external.model.DiagramOutcome
import com.sprintstart.sprintstartbackend.onboarding.external.model.DiagramSchema
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Board
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardCard
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardDiagram
import com.sprintstart.sprintstartbackend.onboarding.model.exceptions.OnboardingAiException
import com.sprintstart.sprintstartbackend.onboarding.repository.BoardCardRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BoardDiagramRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BoardRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID
import kotlin.test.assertFailsWith

/**
 * The one live card served from a cache, and everything that keeps that honest.
 *
 * Every test here is about the same question: when is a kept picture still true? Age never answers
 * it — only a comparison against the corpus as it is now.
 */
class BoardDiagramServiceTest {
    private val boardRepository: BoardRepository = mockk()
    private val boardCardRepository: BoardCardRepository = mockk()
    private val boardDiagramRepository: BoardDiagramRepository = mockk(relaxed = true)
    private val onboardingAiClient: OnboardingAiClient = mockk()
    private val transactionManager: PlatformTransactionManager = mockk(relaxed = true)

    private val hireId: UUID = UUID.randomUUID()
    private val cardId: UUID = UUID.randomUUID()
    private val boardId: UUID = UUID.randomUUID()
    private val subject = "how a request reaches the database"

    private val service = BoardDiagramService(
        boardRepository,
        boardCardRepository,
        boardDiagramRepository,
        onboardingAiClient,
        transactionManager,
    )

    @BeforeEach
    fun setUp() {
        every { boardCardRepository.findById(cardId) } returns Optional.of(diagramCard())
        every { boardRepository.findById(boardId) } returns
            Optional.of(Board(id = boardId, userId = hireId, projectId = UUID.randomUUID()))
        every { boardDiagramRepository.findById(cardId) } returns Optional.empty()
        every { boardDiagramRepository.save(any()) } answers { firstArg() }
    }

    private fun diagramCard(cardSubject: String? = subject) = BoardCard(
        id = cardId,
        boardId = boardId,
        kind = BoardCardKind.DIAGRAM,
        owner = BoardCardOwner.AI,
        position = 0,
        subject = cardSubject,
    )

    private fun assembled(fingerprint: String = "corpus-1") = DiagramOutcome(
        status = "assembled",
        diagram = DiagramSchema(
            subject = subject,
            summary = "A request lands on the controller and ends at the repository.",
            nodes = listOf(
                DiagramNodeSchema(
                    id = "controller",
                    label = "ReportController",
                    kind = "COMPONENT",
                    citations = listOf(CitationRefSchema(filename = "ReportController.kt", chunkId = "c1")),
                ),
                DiagramNodeSchema(
                    id = "repo",
                    label = "ReportRepository",
                    kind = "COMPONENT",
                    citations = listOf(CitationRefSchema(filename = "ReportRepository.kt", chunkId = "c2")),
                ),
            ),
            edges = listOf(DiagramEdgeSchema(fromId = "controller", toId = "repo", kind = "FLOWS_TO")),
        ),
        provenance = AiProvenanceSchema(corpusFingerprint = fingerprint, model = "stub"),
    )

    private fun cached(fingerprint: String? = "corpus-1", payload: String? = null) = BoardDiagram(
        cardId = cardId,
        corpusFingerprint = fingerprint,
        payload = payload ?: KEPT_PAYLOAD,
    )

    @Test
    fun `draws the picture and keeps it with the corpus it was drawn from`() = runTest {
        coEvery { onboardingAiClient.assembleDiagram(subject, null) } returns assembled()

        val content = service.refresh(hireId, cardId)

        assertThat(content.subject).isEqualTo(subject)
        assertThat(content.nodes.map { it.label })
            .containsExactly("ReportController", "ReportRepository")
        assertThat(content.edges)
            .singleElement()
            .satisfies({ assertThat(it.fromId).isEqualTo("controller") })
        assertThat(content.assembledAt).isNotNull()
        assertThat(content.reason).isNull()
        // Kept against the corpus it describes, which is what the next revalidation compares.
        verify { boardDiagramRepository.save(match<BoardDiagram> { it.corpusFingerprint == "corpus-1" }) }
    }

    @Test
    fun `every box carries the source that proves it`() = runTest {
        coEvery { onboardingAiClient.assembleDiagram(subject, null) } returns assembled()

        val content = service.refresh(hireId, cardId)

        assertThat(content.nodes).allSatisfy { assertThat(it.citations).isNotEmpty() }
        assertThat(
            content.nodes
                .first()
                .citations
                .first()
                .filename,
        ).isEqualTo("ReportController.kt")
    }

    @Test
    fun `an unchanged corpus serves the kept picture and nothing is redrawn`() = runTest {
        every { boardDiagramRepository.findById(cardId) } returns Optional.of(cached())
        coEvery { onboardingAiClient.assembleDiagram(subject, "corpus-1") } returns
            DiagramOutcome(status = "unchanged")

        val content = service.refresh(hireId, cardId)

        assertThat(content.nodes.map { it.label }).containsExactly("AuthFilter")
        assertThat(content.reason).isNull()
        verify(exactly = 0) { boardDiagramRepository.save(any()) }
    }

    @Test
    fun `skipped deletes the kept picture because it describes a corpus that is gone`() = runTest {
        every { boardDiagramRepository.findById(cardId) } returns Optional.of(cached())
        coEvery { onboardingAiClient.assembleDiagram(subject, "corpus-1") } returns
            DiagramOutcome(status = "skipped", notes = listOf("no grounding evidence retrieved"))

        val content = service.refresh(hireId, cardId)

        assertThat(content.nodes).isEmpty()
        assertThat(content.reason).isEqualTo("no grounding evidence retrieved")
        verify { boardDiagramRepository.deleteById(cardId) }
    }

    @Test
    fun `an unreachable ai service serves the kept picture rather than losing it`() = runTest {
        every { boardDiagramRepository.findById(cardId) } returns Optional.of(cached())
        coEvery { onboardingAiClient.assembleDiagram(subject, "corpus-1") } throws
            OnboardingAiException(503, "", "down")

        val content = service.refresh(hireId, cardId)

        // Unlike `skipped`, an outage is no evidence at all about staleness -- so the last picture
        // drawn is still the most honest thing available, and it is not deleted.
        assertThat(content.nodes.map { it.label }).containsExactly("AuthFilter")
        verify(exactly = 0) { boardDiagramRepository.deleteById(cardId) }
    }

    @Test
    fun `an outage with nothing kept says so instead of pretending`() = runTest {
        coEvery { onboardingAiClient.assembleDiagram(subject, null) } throws
            OnboardingAiException(503, "", "down")

        val content = service.refresh(hireId, cardId)

        assertThat(content.nodes).isEmpty()
        assertThat(content.reason).isEqualTo("Diagrams are temporarily unavailable")
    }

    @Test
    fun `a kept picture that will not decode is a cache miss, not a failure`() = runTest {
        // The opposite of an authored note, which fails the board read on purpose: that is the
        // hire's own work, and this is a copy of something derivable.
        every { boardDiagramRepository.findById(cardId) } returns
            Optional.of(cached(payload = "{ this is not json"))
        coEvery { onboardingAiClient.assembleDiagram(subject, null) } returns assembled("corpus-2")

        val content = service.refresh(hireId, cardId)

        assertThat(content.nodes).hasSize(2)
    }

    @Test
    fun `an undecodable picture is revalidated as if there were none`() = runTest {
        every { boardDiagramRepository.findById(cardId) } returns
            Optional.of(cached(payload = "{ this is not json"))
        coEvery { onboardingAiClient.assembleDiagram(subject, null) } returns assembled("corpus-2")

        service.refresh(hireId, cardId)

        // Sending the fingerprint of a picture nobody can read would answer `unchanged` and leave
        // the card empty for as long as the corpus stands still.
        coVerify { onboardingAiClient.assembleDiagram(subject, null) }
    }

    @Test
    fun `a board read serves the kept picture without calling anything`() {
        val content = service.contentFor(subject, cached())

        assertThat(content.nodes.map { it.label }).containsExactly("AuthFilter")
        assertThat(content.assembledAt).isNotNull()
    }

    @Test
    fun `a card that has never been drawn says so rather than showing an empty frame`() {
        val content = service.contentFor(subject, cached = null)

        assertThat(content.nodes).isEmpty()
        assertThat(content.assembledAt).isNull()
        assertThat(content.reason).isEqualTo("This diagram has not been drawn yet")
    }

    @Test
    fun `somebody else's card answers the same as one that does not exist`() = runTest {
        every { boardRepository.findById(boardId) } returns
            Optional.of(Board(id = boardId, userId = UUID.randomUUID(), projectId = UUID.randomUUID()))

        assertFailsWith<ResponseStatusException> { service.refresh(hireId, cardId) }
    }

    @Test
    fun `a card of another kind is not a diagram to refresh`() = runTest {
        every { boardCardRepository.findById(cardId) } returns Optional.of(
            BoardCard(
                id = cardId,
                boardId = boardId,
                kind = BoardCardKind.CURRENT_TASK,
                owner = BoardCardOwner.AI,
                position = 0,
            ),
        )

        assertFailsWith<ResponseStatusException> { service.refresh(hireId, cardId) }
    }

    private companion object {
        /** A previously kept picture, deliberately different from what a redraw would produce. */
        val KEPT_PAYLOAD =
            """
            {"summary":"How a request is authenticated.",
             "nodes":[{"id":"filter","label":"AuthFilter","kind":"COMPONENT",
                       "citations":[{"filename":"AuthFilter.kt","chunkId":"c9"}]}],
             "edges":[],"sources":[{"filename":"AuthFilter.kt"}]}
            """.trimIndent()
    }
}
