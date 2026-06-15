package com.sprintstart.sprintstartbackend.canonical.service

import com.sprintstart.sprintstartbackend.canonical.model.dto.ArtifactCommand
import com.sprintstart.sprintstartbackend.canonical.model.entity.Artifact
import com.sprintstart.sprintstartbackend.canonical.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.canonical.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.canonical.repository.IngestionRunRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
class ArtifactIngestionService(
    private val ingestionRunRepository : IngestionRunRepository,
    private val artifactRepository : ArtifactRepository,
) {
    @Transactional
    fun ingest(command : ArtifactCommand){
        val ingestionRun = ingestionRunRepository
            .findById(command.ingestionRunId)
            .orElse(IngestionRun(
                id = command.ingestionRunId,
                sourceSystem = command.sourceSystem,

            ))
        ingestionRunRepository.save(ingestionRun)
        val artifact = Artifact(
            sourceSystem = command.sourceSystem,
            sourceId = command.sourceId,
            sourceUrl = command.sourceUrl,
            artifactType = command.artifactType,
            title = command.title,
            bodyText = command.bodyText,
            mime = command.mime,
            language = command.language,
            createdAtSource = command.createdAtSource,
            updatedAtSource = command.updatedAtSource,
            ingestionRun = ingestionRun,
            hash = command.hash,
            version = command.version?:"1"

        )
        artifactRepository.save(artifact)
    }

}