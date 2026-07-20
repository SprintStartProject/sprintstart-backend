package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.ArtifactIngestionClient
import com.sprintstart.sprintstartbackend.ingestion.model.dto.request.AiArtifactSummaryRequest
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.AiArtifactSummaryStreamMessage
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactSummary
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactSummaryCitation
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactSummaryRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.upload.external.api.UploadApi
import com.sprintstart.sprintstartbackend.user.external.UserApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Streams artifact summaries, generating and caching them via the AI service.
 *
 * Generating a summary is a real (and now streamed) LLM call, so a fresh one is only requested
 * from the AI service when there is no cached summary for the artifact, or the cached one was
 * generated from different content. (Its stored hash no longer matches the artifact's current
 * hash.) A cache hit is still delivered as an SSE stream -- a single `token` event carrying the
 * whole cached text -- so the wire contract is the same shape regardless of cache hit/miss. An
 * artifact with no content hash (a legacy/edge-case ingested artifact) cannot be cached and is
 * summarized fresh on every call.
 *
 * An artifact can be either ingested (via a connector) or directly uploaded; both are checked
 * through their modules' exported read-only APIs rather than reaching into their repositories.
 */
@Service
internal class ArtifactSummaryService(
    private val artifactIngestionApiService: ArtifactIngestionApiService,
    private val artifactSummaryRepository: ArtifactSummaryRepository,
    private val artifactIngestionClient: ArtifactIngestionClient,
    private val uploadApi: UploadApi,
    private val userApi: UserApi,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Streams the summary of [artifactId] over SSE.
     *
     * Retrieves a summary stream for a specified artifact within a project. Ensures proper authorization and validates
     * artifact existence within the project before proceeding.
     *
     * @param projectId The unique identifier of the project whose artifact's summary is being requested.
     * @param artifactId The unique identifier of the artifact for which the summary is generated.
     * @param authId The identifier of the user requesting access, used for authorization validation.
     * @return A flow stream of [AiArtifactSummaryStreamMessage] representing the artifact's summary,
     *         either retrieved from the cache or generated dynamically.
     * @throws ResponseStatusException with status `FORBIDDEN` if the user does not have access to the project.
     * @throws ResponseStatusException with status `NOT_FOUND` if the artifact does not exist in the project.
     */
    @Transactional(readOnly = true)
    @Tracked("Streaming artifact summary for project")
    fun getSummary(projectId: UUID, artifactId: UUID, authId: String): Flow<AiArtifactSummaryStreamMessage> {
        if (!userApi.userHasAccessToProject(authId, projectId)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "No access to project")
        }

        if (!artifactIngestionApiService.existsInProject(projectId, artifactId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Artifact $artifactId not found in project $projectId")
        }

        val currentHash = resolveHash(artifactId)

        val cached = currentHash?.let { hash ->
            artifactSummaryRepository.findById(artifactId).orElse(null)?.takeIf { it.sourceHash == hash }
        }
        if (cached != null) {
            return cachedSummaryStream(cached)
        }

        return generateAndCacheStream(artifactId, currentHash)
    }

    /**
     * Generates a stream of summary messages based on the cached artifact summary and its citations.
     *
     * @param cached The cached artifact summary containing the summary text and citation details.
     * @return A flow emitting a series of AiArtifactSummaryStreamMessage objects that include the summary,
     * citations, and a final "done" message to signal completion.
     */
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

    /**
     * Generates a stream of AI artifact summary messages while caching the final result upon completion.
     *
     * This method interacts with an AI client to produce a live stream of summary events for the specified artifact ID.
     * The data within the stream is processed to build a consolidated summary text and citations, which are cached for
     * future use if an existing hash is provided and the stream completes successfully.
     *
     * @param artifactId The unique identifier of the artifact for which the summary is generated.
     * @param currentHash The optional current hash associated with the artifact, used to determine cache storage.
     * @return A flow of AI artifact summary stream messages that represent live updates generated by the AI client.
     */
    private fun generateAndCacheStream(
        artifactId: UUID,
        currentHash: String?,
    ): Flow<AiArtifactSummaryStreamMessage> {
        val summaryText = StringBuilder()
        val citations = mutableListOf<PendingCitation>()

        return artifactIngestionClient
            .summarizeStream(artifactId, AiArtifactSummaryRequest())
            .map { event ->
                when (event.type) {
                    "token" -> {
                        event.content?.let(summaryText::append)
                        event
                    }

                    "citation" -> {
                        collectCitation(event, citations)
                    }

                    else -> {
                        event
                    }
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

    /**
     * Builds an instance of the `ArtifactSummary` class with its associated citations.
     *
     * @param artifactId Unique identifier of the artifact for which the summary is being built.
     * @param sourceHash Hash representing the source of the artifact.
     * @param summaryText Summary description of the artifact.
     * @param citations List of pending citations to be associated with the artifact summary.
     * @return An instance of `ArtifactSummary` populated with the provided details and citations.
     */
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

        if (artifactIngestionApiService.exists(artifactId)) {
            return artifactIngestionApiService.getHash(artifactId)
        }

        throw ResponseStatusException(HttpStatus.NOT_FOUND, "Artifact $artifactId not found")
    }

    /**
     * Attempts to parse the given string into a UUID.
     * If the string is not a valid UUID, returns null.
     *
     * @param value the string representation of the UUID to be parsed.
     * @return the parsed UUID object if the string is valid, or null if parsing fails.
     */
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
