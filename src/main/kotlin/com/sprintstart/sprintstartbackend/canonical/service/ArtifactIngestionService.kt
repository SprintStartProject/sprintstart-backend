package com.sprintstart.sprintstartbackend.canonical.service

import com.sprintstart.sprintstartbackend.canonical.model.dto.command.ArtifactCommand
import com.sprintstart.sprintstartbackend.canonical.model.dto.command.ArtifactFailedCommand
import com.sprintstart.sprintstartbackend.canonical.model.dto.response.FailedArtifactResponse
import com.sprintstart.sprintstartbackend.canonical.model.entity.Artifact
import com.sprintstart.sprintstartbackend.canonical.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.canonical.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.canonical.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.canonical.model.entity.SourceSystem
import com.sprintstart.sprintstartbackend.canonical.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.canonical.repository.IngestionRunRepository
import jakarta.transaction.Transactional
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ArtifactIngestionService(
    private val ingestionRunRepository : IngestionRunRepository,
    private val artifactRepository : ArtifactRepository,
) {
    @Transactional
    fun ingest(command : ArtifactCommand){
        var artifact : Artifact?
        val ingestionRun = ingestionRunRepository
            .findByIdOrNull(command.ingestionRunId)
            ?:throw IllegalArgumentException("Run with id ${command.ingestionRunId} not found")
        when(command.artifactType){
            ArtifactType.COMMIT //TODO updating artifact
                -> { artifact = artifactRepository.findBySourceId(command.sourceId)
                    if(artifact != null){
                        ingestionRun.ingestedCount++
                        return
                    }
                }
            ArtifactType.FILE
                -> { artifact = artifactRepository.findBySourceId(command.sourceId)
                if(artifact != null){
                    if(!artifact.hash.equals(command.hash)){
                        ingestionRun.updatedCount++
                        artifact.bodyText = command.bodyText
                        artifact.hash = command.hash
                        return
                    }
                    ingestionRun.ingestedCount++

                }
            }
            ArtifactType.ISSUE
                -> { artifact = artifactRepository.findBySourceId(command.sourceId)
                if(artifact != null){
                    artifact.title = command.title
                    artifact.bodyText = command.bodyText
                    ingestionRun.updatedCount++
                    return
                }
            }
            ArtifactType.PULL_REQUEST
                -> { artifact = artifactRepository.findBySourceId(command.sourceId)
                if(artifact != null){
                    artifact.title = command.title
                    artifact.bodyText = command.bodyText
                    ingestionRun.updatedCount++
                    return
                }
            }
        }

        artifact = Artifact(
            sourceSystem = command.sourceSystem,
            sourceId = command.sourceId,
            sourceUrl = command.sourceUrl,
            artifactType = command.artifactType,
            title = command.title,
            bodyText = command.bodyText,
            mime = command.mime,
            language = command.language,
            ingestionRun = ingestionRun,
            hash = command.hash,
            createdAtSource = null,
            updatedAtSource = null
        )
        artifactRepository.save(artifact)

        ingestionRun.ingestedCount++
    }

    @Transactional
    fun startRun(transactionId: UUID, sourceSystem: SourceSystem, status: IngestionRunStatus) {
            val ingestionRun = IngestionRun(
                id = transactionId,
                sourceSystem = sourceSystem,
                status = status,
            )
        ingestionRunRepository.save(ingestionRun)
    }

    fun updateRunStatus(transactionId: UUID, status : IngestionRunStatus) {
        val run = ingestionRunRepository
            .findByIdOrNull(transactionId)
            ?:throw IllegalArgumentException("Run with id $transactionId not found")
        run.status = status
    }

    fun addFailedArtifact(command: ArtifactFailedCommand) {

        val run = ingestionRunRepository
            .findByIdOrNull(command.transactionId)
            ?:throw IllegalArgumentException("Run with id ${command.transactionId} not found")
        run.failedItems.add(FailedArtifactResponse(
            sourceId = command.sourceId,
            reason = command.reason,
            artifactType = command.artifactType,
            sourceUrl = command.sourceUrl
        ))
    }


}