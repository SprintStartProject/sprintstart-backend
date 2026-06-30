package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.ArtifactIngestionClient
import com.sprintstart.sprintstartbackend.ingestion.model.dto.request.RunArtifactsIngestRequest
import com.sprintstart.sprintstartbackend.ingestion.model.exceptions.IngestionRunNotFoundException
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.ingestion.ArtifactAiMapper
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class RunArtifactsIngestionService(
    private val ingestionRunRepository: IngestionRunRepository,
    private val artifactRepository: ArtifactRepository,
    private val artifactAiMapper: ArtifactAiMapper,
    private val artifactIngestionClient: ArtifactIngestionClient,
) {
    suspend fun ingestRunArtifacts(runId: UUID) {
        val run = ingestionRunRepository.existsById(runId)

        if (!run) throw IngestionRunNotFoundException(runId)

        val artifacts = artifactRepository.findAllByIngestionRunId(runId)

        if (artifacts.isEmpty()) return

        artifactIngestionClient.ingest(
            RunArtifactsIngestRequest(
                artifacts.map { artifactAiMapper.toRequest(it) },
            ),
        )
    }
}
