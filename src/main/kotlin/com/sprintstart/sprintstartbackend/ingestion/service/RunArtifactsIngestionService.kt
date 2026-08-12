package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.ArtifactIngestionClient
import com.sprintstart.sprintstartbackend.ingestion.model.dto.request.RunArtifactsAiSyncRequest
import com.sprintstart.sprintstartbackend.ingestion.model.exceptions.IngestionRunNotFoundException
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
 * Removes a completed run's deleted artifacts from the AI index.
 *
 * Indexing itself is no longer this service's job: artifacts are synced incrementally during the
 * crawl by [ArtifactAiSyncService], so by the time a run finishes there is nothing left to batch.
 * Deletions are the exception -- a deleted artifact has no row left to carry outbox state, so the
 * ids are collected on the run (`artifactIdsToDeindex`) and flushed here, once, when the run ends.
 */
@Service
class RunArtifactsIngestionService(
    private val ingestionRunRepository: IngestionRunRepository,
    private val artifactIngestionClient: ArtifactIngestionClient,
    transactionManager: PlatformTransactionManager,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val readTxTemplate = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    /**
     * Sends the run's deindex list to the AI service, if it has one.
     *
     * @param runId The finished ingestion run.
     * @throws IngestionRunNotFoundException when the run id does not exist.
     * @throws com.sprintstart.sprintstartbackend.upload.model.exceptions.IngestionResponseException
     * when the AI ingestion service rejects the request.
     */
    @Tracked("Dispatching deindex for run")
    suspend fun deindexRunArtifacts(runId: UUID) {
        val artifactIdsToDeindex = withContext(Dispatchers.IO) {
            readTxTemplate.execute { loadDeindexIds(runId) }
        }.orEmpty()

        if (artifactIdsToDeindex.isEmpty()) {
            logger.info("Run {} deleted nothing, no deindex request needed", runId)
            return
        }

        logger.info("Deindexing {} deleted artifact(s) for run {}", artifactIdsToDeindex.size, runId)
        artifactIngestionClient.ingest(
            RunArtifactsAiSyncRequest(artifactsToIngest = emptyList(), artifactsToDeindex = artifactIdsToDeindex),
        )
    }

    private fun loadDeindexIds(runId: UUID): List<String> {
        val run = ingestionRunRepository
            .findWithArtifactIdsToDeindexById(runId)
            .getOrElse { throw IngestionRunNotFoundException(runId) }

        return run.artifactIdsToDeindex.toList()
    }
}
