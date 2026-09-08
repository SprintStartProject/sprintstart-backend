package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.onboarding.external.model.DiagramOutcome
import com.sprintstart.sprintstartbackend.onboarding.external.model.DiagramSchema
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardDiagram
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardDiagramCitationPayload
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardDiagramEdgePayload
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardDiagramNodePayload
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardDiagramPayload
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardDiagramSourcePayload
import com.sprintstart.sprintstartbackend.onboarding.model.exceptions.OnboardingAiException
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardDiagramCitationResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardDiagramEdgeResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardDiagramNodeResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardDiagramSourceResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.DiagramContent
import com.sprintstart.sprintstartbackend.onboarding.repository.BoardCardRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BoardDiagramRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BoardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.util.UUID

/**
 * The picture on a hire's diagram card: kept, checked against the corpus, and redrawn when it moved.
 *
 * A diagram costs a generation and a board card hydrates on every page load, so the picture is
 * kept — validated, never trusted, the same three rules `TaskOrientationService` keeps:
 *
 * * Every revalidation sends the fingerprint of the corpus the picture was drawn from. An
 *   unchanged corpus comes back `unchanged` with no retrieval and no generation, and the kept
 *   picture is served. Age is not staleness.
 * * `skipped` deletes the cache. The AI service looked at the *current* corpus and could not
 *   ground a picture, so whatever is kept describes a corpus that is gone.
 * * A transport failure serves the cache. Unlike `skipped`, an unreachable AI service is no
 *   evidence at all about staleness.
 * * Nothing is ever fabricated. "No picture, and here is why" is an ordinary returned state.
 *
 * Reading a board and revalidating a card are split. [contentFor] serves the kept picture
 * and never calls anything; the suspend [refresh] is what checks it. [DiagramContent.assembledAt]
 * travels to the client because a picture served from the cache is a claim about code as it was at
 * a moment.
 */
@Service
class BoardDiagramService(
    private val boardRepository: BoardRepository,
    private val boardCardRepository: BoardCardRepository,
    private val boardDiagramRepository: BoardDiagramRepository,
    private val onboardingAiClient: OnboardingAiClient,
    transactionManager: PlatformTransactionManager,
    private val clock: Clock = Clock.systemUTC(),
) {
    // The AI call is a long-running suspend operation, so it must not run inside a transaction --
    // the same read-tx -> AI -> write-tx split TaskOrientationService uses.
    private val txTemplate = TransactionTemplate(transactionManager)
    private val readTxTemplate = TransactionTemplate(transactionManager).apply { isReadOnly = true }
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * The kept picture for a card, without calling anything.
     *
     * Used by the board read, which must stay a database read: a page that waits on a model to open
     * is a page nobody opens. [cached] is passed in rather than looked up so a board with several
     * diagram cards costs one query, not one per card.
     */
    fun contentFor(subject: String, cached: BoardDiagram?): DiagramContent {
        val payload = cached?.let { decode(it) }
        return DiagramContent(
            subject = subject,
            summary = payload?.summary?.takeIf { it.isNotBlank() },
            nodes = payload?.nodes.orEmpty().map { it.toResponse() },
            edges = payload?.edges.orEmpty().map { it.toResponse() },
            sources = payload?.sources.orEmpty().map { it.toResponse() },
            assembledAt = cached?.assembledAt.takeIf { payload != null },
            reason = if (payload == null) NOT_DRAWN_YET else null,
        )
    }

    /**
     * Checks this card's picture against the current corpus, redrawing it if the corpus has moved.
     *
     * What the client calls after the board has rendered, so the first paint costs nothing and the
     * revalidation costs a fingerprint comparison unless something actually changed.
     *
     * @param userId The caller, who must own the board the card is on.
     * @param cardId The diagram card to revalidate.
     * @throws ResponseStatusException 404 when the card is not a diagram card of theirs — a card
     *   belonging to somebody else and one that does not exist answer identically, on purpose.
     */
    suspend fun refresh(userId: UUID, cardId: UUID): DiagramContent {
        val context = withContext(Dispatchers.IO) {
            readTxTemplate.execute { loadContext(userId, cardId) }
        } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No such diagram card on your board")

        val outcome = try {
            onboardingAiClient.assembleDiagram(
                subject = context.subject,
                lastFingerprint = context.fingerprint,
            )
        } catch (e: OnboardingAiException) {
            logger.warn("Diagram assembly unavailable for card {}: {}", cardId, e.message)
            // No evidence about staleness, so the last picture drawn stands.
            return context.served(UNAVAILABLE)
        }

        return withContext(Dispatchers.IO) { txTemplate.execute { apply(context, outcome) }!! }
    }

    private fun apply(context: DiagramContext, outcome: DiagramOutcome): DiagramContent {
        val diagram = outcome.diagram
        return when {
            outcome.status == ASSEMBLED && diagram != null -> store(context, outcome, diagram)

            outcome.status == UNCHANGED -> context.served(null)

            else -> {
                // The AI service looked at the *current* corpus and could not ground a picture, so
                // anything kept describes a corpus that is gone.
                boardDiagramRepository.deleteById(context.cardId)
                DiagramContent(
                    subject = context.subject,
                    summary = null,
                    nodes = emptyList(),
                    edges = emptyList(),
                    sources = emptyList(),
                    assembledAt = null,
                    reason = outcome.notes.firstOrNull() ?: NOT_ASSEMBLED,
                )
            }
        }
    }

    private fun store(
        context: DiagramContext,
        outcome: DiagramOutcome,
        schema: DiagramSchema,
    ): DiagramContent {
        val payload = BoardDiagramPayload(
            summary = schema.summary.takeIf { it.isNotBlank() },
            nodes = schema.nodes.map { node ->
                BoardDiagramNodePayload(
                    id = node.id,
                    label = node.label,
                    kind = node.kind,
                    summary = node.summary.takeIf { it.isNotBlank() },
                    citations = node.citations.map {
                        BoardDiagramCitationPayload(
                            filename = it.filename,
                            chunkId = it.chunkId,
                            sourceUrl = it.sourceUrl,
                        )
                    },
                )
            },
            edges = schema.edges.map {
                BoardDiagramEdgePayload(
                    fromId = it.fromId,
                    toId = it.toId,
                    kind = it.kind,
                    label = it.label.takeIf { label -> label.isNotBlank() },
                )
            },
            sources = schema.sources.map {
                BoardDiagramSourcePayload(
                    filename = it.filename,
                    sourceUrl = it.sourceUrl,
                    artifactType = it.artifactType,
                )
            },
        )

        // Replaced wholesale rather than merged: a picture is derived, holds no human edits, and
        // there is nothing a merge would protect.
        val now = clock.instant()
        val stored = boardDiagramRepository.save(
            BoardDiagram(
                cardId = context.cardId,
                corpusFingerprint = outcome.provenance?.corpusFingerprint,
                model = outcome.provenance?.model,
                payload = json.encodeToString(payload),
                assembledAt = now,
            ),
        )
        return contentFor(context.subject, stored)
    }

    /**
     * A cached picture that will not decode is a cache miss, not a failure.
     *
     * The opposite of how `BoardService` treats an undecodable authored payload, and deliberately:
     * a note is the hire's own work and silently blanking it would look like the board lost
     * something, while everything in a diagram is derivable and was nobody's work. So this redraws
     * on the next revalidation instead of taking the board down with it.
     */
    private fun decode(cached: BoardDiagram): BoardDiagramPayload? =
        runCatching { json.decodeFromString<BoardDiagramPayload>(cached.payload) }
            .onFailure { logger.warn("Discarding undecodable diagram cache for card {}", cached.cardId) }
            .getOrNull()

    private fun loadContext(userId: UUID, cardId: UUID): DiagramContext? {
        val card = boardCardRepository.findById(cardId).orElse(null) ?: return null
        if (card.kind != BoardCardKind.DIAGRAM) return null
        val board = boardRepository.findById(card.boardId).orElse(null) ?: return null
        if (board.userId != userId) return null
        val subject = card.subject?.takeIf { it.isNotBlank() } ?: return null

        val cached = boardDiagramRepository.findById(cardId).orElse(null)
        return DiagramContext(
            cardId = cardId,
            subject = subject,
            // A cache that will not decode must not be revalidated as if it were current, or an
            // unchanged corpus would answer `unchanged` and leave the card permanently empty.
            fingerprint = cached?.takeIf { decode(it) != null }?.corpusFingerprint,
            cached = cached,
        )
    }

    private fun BoardDiagramNodePayload.toResponse() = BoardDiagramNodeResponse(
        id = id,
        label = label,
        kind = kind,
        summary = summary,
        citations = citations.map {
            BoardDiagramCitationResponse(filename = it.filename, sourceUrl = it.sourceUrl)
        },
    )

    private fun BoardDiagramEdgePayload.toResponse() =
        BoardDiagramEdgeResponse(fromId = fromId, toId = toId, kind = kind, label = label)

    private fun BoardDiagramSourcePayload.toResponse() = BoardDiagramSourceResponse(
        filename = filename,
        sourceUrl = sourceUrl,
        artifactType = artifactType,
    )

    private data class DiagramContext(
        val cardId: UUID,
        val subject: String,
        val fingerprint: String?,
        val cached: BoardDiagram?,
    )

    /** The kept picture, with [reason] only when there is nothing to serve. */
    private fun DiagramContext.served(reason: String?): DiagramContent {
        val content = contentFor(subject, cached)
        return if (content.nodes.isEmpty() && reason != null) content.copy(reason = reason) else content
    }

    private companion object {
        const val ASSEMBLED = "assembled"
        const val UNCHANGED = "unchanged"
        const val NOT_DRAWN_YET = "This diagram has not been drawn yet"
        const val NOT_ASSEMBLED = "Nothing in this project's material describes that closely enough to draw"
        const val UNAVAILABLE = "Diagrams are temporarily unavailable"

        /** Lenient on unknown keys so a picture written by a newer version still reads back. */
        val json = Json { ignoreUnknownKeys = true }
    }
}
