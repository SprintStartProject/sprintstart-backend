package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.ArtifactIngestionClient
import com.sprintstart.sprintstartbackend.ingestion.model.dto.request.RunArtifactsAiSyncRequest
import com.sprintstart.sprintstartbackend.ingestion.model.exceptions.IngestionRunNotFoundException
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.ingestion.ArtifactAiMapper
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
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
) {
    /**
     * Loads the run output, skips empty runs, and dispatches the batched ingest/deindex request.
     *
     * @param runId The completed ingestion run whose artifacts should be synced to AI.
     * @throws IngestionRunNotFoundException when the run id does not exist.
     */
    suspend fun ingestRunArtifacts(runId: UUID) {
        val request = withContext(Dispatchers.IO) {
            val run = ingestionRunRepository
                .findById(runId)
                .getOrElse { throw IngestionRunNotFoundException(runId) }

            val artifactsToIngest = artifactRepository.findAllByIngestionRunId(runId)
            val artifactsToDeindex = run.artifactIdsToDeindex

            if (artifactsToIngest.isEmpty() && artifactsToDeindex.isEmpty()) {
                return@withContext null
            }

            RunArtifactsAiSyncRequest(
                artifactsToIngest = artifactsToIngest.map { artifactAiMapper.toIngestRequest(it) },
                artifactsToDeindex = run.artifactIdsToDeindex,
            )
        } ?: return
        artifactIngestionClient.ingest(request)
    }
}
