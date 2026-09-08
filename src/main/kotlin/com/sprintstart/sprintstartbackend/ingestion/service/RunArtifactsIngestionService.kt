package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.ArtifactIngestionClient
import com.sprintstart.sprintstartbackend.ingestion.model.dto.request.RunArtifactsAiSyncRequest
import com.sprintstart.sprintstartbackend.ingestion.model.exceptions.IngestionRunNotFoundException
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.ingestion.ArtifactAiMapper
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import kotlin.jvm.optionals.getOrElse

/**
 * Sends the final AI indexing payload for a completed ingestion run.
 *
 * The service builds one batch containing newly stored run artifacts and artifact ids that were
 * deleted during the same run and must be removed from the AI index. Database reads stay on
 * [Dispatchers.IO]; the HTTP call happens after the request is built.
 */
@Service
class RunArtifactsIngestionService(
    private val ingestionRunRepository: IngestionRunRepository,
    private val artifactRepository: ArtifactRepository,
    private val artifactAiMapper: ArtifactAiMapper,
    private val artifactIngestionClient: ArtifactIngestionClient,
    transactionManager: PlatformTransactionManager,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // The reads below run on a fire-and-forget coroutine after the ingestion run''s own
    // transaction has already committed, so there is no session bound to this thread.
    // `Artifact.projectIds` is a lazy @ElementCollection, and reading it for the AI payload
    // therefore threw LazyInitializationException — which failed the whole sync and left the
    // AI index empty. @Transactional does not apply to suspend functions, so the reads are
    // wrapped explicitly, the same way BlueprintService does it.
    private val readTxTemplate =
        TransactionTemplate(transactionManager).apply { isReadOnly = true }

    /**
     * Loads the run output, skips empty runs, and dispatches the batched ingest/deindex request.
     *
     * Empty runs are intentionally ignored because there is nothing for the AI layer to index or
     * remove. Repository reads are executed on [Dispatchers.IO] before the outbound HTTP call.
     *
     * @param runId The completed ingestion run whose artifacts should be synced to AI.
     * @throws IngestionRunNotFoundException when the run id does not exist.
     * @throws com.sprintstart.sprintstartbackend.upload.model.exceptions.IngestionResponseException
     * when the AI ingestion service rejects the sync request.
     */
    @Tracked("Dispatching AI sync for run")
    suspend fun ingestRunArtifacts(runId: UUID) {
        val request = withContext(Dispatchers.IO) {
            readTxTemplate.execute {
                val run = ingestionRunRepository
                    .findWithArtifactIdsToDeindexById(runId)
                    .getOrElse { throw IngestionRunNotFoundException(runId) }

                val artifactsToIngest = artifactRepository.findAllByIngestionRunId(runId)
                val artifactsToDeindex = run.artifactIdsToDeindex

                if (artifactsToIngest.isEmpty() && artifactsToDeindex.isEmpty()) {
                    logger.info("Run {} has nothing for AI to sync, skipping", runId)
                    return@execute null
                }

                RunArtifactsAiSyncRequest(
                    artifactsToIngest = artifactsToIngest.map { artifactAiMapper.toIngestRequest(it) },
                    artifactsToDeindex = run.artifactIdsToDeindex,
                )
            }
        } ?: return

        logger.info(
            "Dispatching AI sync for run {}: {} to ingest, {} to deindex",
            runId,
            request.artifactsToIngest.size,
            request.artifactsToDeindex.size,
        )
        artifactIngestionClient.ingest(request)
        logger.info("AI sync confirmed for run {}", runId)
    }
}
