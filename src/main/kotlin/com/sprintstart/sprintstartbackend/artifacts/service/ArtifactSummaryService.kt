package com.sprintstart.sprintstartbackend.artifacts.service

import com.sprintstart.sprintstartbackend.artifacts.ArtifactSummaryAiClient
import com.sprintstart.sprintstartbackend.artifacts.model.ai.AiArtifactSummaryRequest
import com.sprintstart.sprintstartbackend.artifacts.model.ai.AiArtifactSummaryStreamMessage
import com.sprintstart.sprintstartbackend.artifacts.model.entity.ArtifactSummary
import com.sprintstart.sprintstartbackend.artifacts.model.entity.ArtifactSummaryCitation
import com.sprintstart.sprintstartbackend.artifacts.repository.ArtifactSummaryRepository
import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.upload.external.UploadApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Streams artifact summaries, generating and caching them via the AI service.
 *
 * Generating a summary is a real (and now streamed) LLM call, so a fresh one is only requested
 * from the AI service when there is no cached summary for the artifact, or the cached one was
 * generated from different content (its stored hash no longer matches the artifact's current
 * hash). A cache hit is still delivered as an SSE stream -- a single `token` event carrying the
 * whole cached text -- so the wire contract is the same shape regardless of cache hit/miss. An
 * artifact with no content hash (a legacy/edge-case ingested artifact) cannot be cached and is
 * summarized fresh on every call.
 *
 * An artifact can be either ingested (via a connector) or directly uploaded; both are checked
 * through their modules' exported read-only APIs rather than reaching into their repositories.
 */
@Service
class ArtifactSummaryService(
    private val artifactSummaryRepository: ArtifactSummaryRepository,
    private val artifactSummaryAiClient: ArtifactSummaryAiClient,
    private val artifactIngestionApi: ArtifactIngestionApi,
    private val uploadApi: UploadApi,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Streams the summary of [artifactId] over SSE.
     *
     * @throws ResponseStatusException 404 if no artifact with [artifactId] exists (ingested or
     *   uploaded). Thrown synchronously, before the returned [Flow] is collected, so it surfaces
     *   as a real HTTP 404 rather than an `error` SSE event.
     * @throws com.sprintstart.sprintstartbackend.artifacts.model.exceptions.ArtifactSummaryAiException
     *   if the AI service fails mid-stream (surfaces as an `error` SSE event, since the response
     *   has already committed to 200 by then).
     */
    fun getSummary(artifactId: UUID): Flow<AiArtifactSummaryStreamMessage> {
        val currentHash = resolveHash(artifactId)

        val cached = currentHash?.let { hash ->
            artifactSummaryRepository.findById(artifactId).orElse(null)?.takeIf { it.sourceHash == hash }
        }
        if (cached != null) {
            return cachedSummaryStream(cached)
        }

        return generateAndCacheStream(artifactId, currentHash)
    }

    private fun cachedSummaryStream(cached: ArtifactSummary): Flow<AiArtifactSummaryStreamMessage> = flow {
        emit(AiArtifactSummaryStreamMessage(type = "token", content = cached.summary))
        cached.citations.forEach { citation ->
            emit(
                AiArtifactSummaryStreamMessage(
                    type = "citation",
                    artifactId = citation.citedArtifactId.toString(),
                    filename = citation.filename,
                    sourceUrl = citation.sourceUrl,
                ),
            )
        }
        emit(AiArtifactSummaryStreamMessage(type = "done"))
    }

    private fun generateAndCacheStream(
        artifactId: UUID,
        currentHash: String?,
    ): Flow<AiArtifactSummaryStreamMessage> {
        val summaryText = StringBuilder()
        val citations = mutableListOf<PendingCitation>()

        return artifactSummaryAiClient
            .summarizeStream(artifactId, AiArtifactSummaryRequest())
            .map { event ->
                when (event.type) {
                    "token" -> {
                        event.content?.let(summaryText::append)
                        event
                    }

                    "citation" -> collectCitation(event, citations)

                    else -> event
                }
            }.filterNotNull()
            .onCompletion { cause ->
                if (cause == null && currentHash != null) {
                    artifactSummaryRepository.save(
                        buildCacheEntity(artifactId, currentHash, summaryText.toString(), citations),
                    )
                }
            }
    }

    /**
     * Records a citation for caching and returns it unchanged to forward downstream, or drops it
     * (returns null) if the AI sent an artifact id that isn't a valid UUID -- degrading just that
     * one citation instead of the whole summary, in both the live stream and the cache.
     */
    private fun collectCitation(
        event: AiArtifactSummaryStreamMessage,
        citations: MutableList<PendingCitation>,
    ): AiArtifactSummaryStreamMessage? {
        val citedArtifactId = event.artifactId?.let(::parseUuidOrNull)
        if (citedArtifactId == null) {
            logger.warn("Dropping citation with non-UUID artifact id {}", event.artifactId)
            return null
        }
        citations += PendingCitation(
            citedArtifactId = citedArtifactId,
            filename = event.filename ?: "",
            sourceUrl = event.sourceUrl,
        )
        return event
    }

    private fun buildCacheEntity(
        artifactId: UUID,
        sourceHash: String,
        summaryText: String,
        citations: List<PendingCitation>,
    ): ArtifactSummary {
        val entity = ArtifactSummary(artifactId = artifactId, summary = summaryText, sourceHash = sourceHash)
        entity.citations = citations
            .map { pending ->
                ArtifactSummaryCitation(
                    artifactSummary = entity,
                    citedArtifactId = pending.citedArtifactId,
                    filename = pending.filename,
                    sourceUrl = pending.sourceUrl,
                )
            }.toMutableList()
        return entity
    }

    /**
     * Resolves the current content hash of [artifactId] (null means "exists, but no hash on
     * record", which disables caching for it -- see class docs).
     *
     * @throws ResponseStatusException 404 if no artifact with [artifactId] exists at all (ingested
     *   or uploaded).
     */
    private fun resolveHash(artifactId: UUID): String? {
        val uploadedHash = uploadApi.getHash(artifactId)
        if (uploadedHash != null) {
            return uploadedHash
        }

        if (artifactIngestionApi.exists(artifactId)) {
            return artifactIngestionApi.getHash(artifactId)
        }

        throw ResponseStatusException(HttpStatus.NOT_FOUND, "Artifact $artifactId not found")
    }

    private fun parseUuidOrNull(value: String): UUID? =
        try {
            UUID.fromString(value)
        } catch (_: IllegalArgumentException) {
            null
        }

    private data class PendingCitation(
        val citedArtifactId: UUID,
        val filename: String,
        val sourceUrl: String?,
    )
}
