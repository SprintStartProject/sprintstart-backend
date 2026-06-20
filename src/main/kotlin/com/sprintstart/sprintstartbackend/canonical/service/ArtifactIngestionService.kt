package com.sprintstart.sprintstartbackend.canonical.service

import com.sprintstart.sprintstartbackend.canonical.model.dto.ArtifactCommand
import com.sprintstart.sprintstartbackend.canonical.model.entity.Artifact
import com.sprintstart.sprintstartbackend.canonical.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.canonical.model.entity.IngestionRun
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
        var ingestNeeded = false
        val ingestionRun = ingestionRunRepository
            .findByIdOrNull(command.ingestionRunId)
            ?:throw IllegalArgumentException("Run not found")
        when(command.artifactType){
            ArtifactType.COMMIT //TODO updating artifact
                -> { artifact = artifactRepository.findBySourceId(command.sourceId)
                    if(artifact != null){
                        artifact.ingestionRun.ingestedCount++
                        ingestionRun.processedArtifacts += artifact.id.toString()
                        return
                    }
                }
            ArtifactType.FILE
                -> { artifact = artifactRepository.findBySourceId(command.sourceId)
                if(artifact != null){
                    if(!artifact.hash.equals(command.hash)){
                        artifact.ingestionRun.updatedCount++
                        artifact.bodyText = command.bodyText
                        artifact.hash = command.hash
                        ingestionRun.processedArtifacts += artifact.id.toString()
                        return
                    }
                    artifact.ingestionRun.ingestedCount++

                }
            }
            ArtifactType.ISSUE
                -> { artifact = artifactRepository.findBySourceId(command.sourceId)
                if(artifact != null){
                    artifact.title = command.title
                    artifact.bodyText = command.bodyText
                    artifact.ingestionRun.ingestedCount++
                    ingestionRun.processedArtifacts += artifact.id.toString()
                    return
                }
            }
            ArtifactType.PULL_REQUEST
                -> { artifact = artifactRepository.findBySourceId(command.sourceId)
                if(artifact != null){
                    artifact.title = command.title
                    artifact.bodyText = command.bodyText
                    artifact.ingestionRun.ingestedCount++
                    ingestionRun.processedArtifacts += artifact.id.toString()
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
            version = command.version ?: "1",
            createdAtSource = null,
            updatedAtSource = null
        )
        artifactRepository.save(artifact)

        ingestionRun.processedArtifacts += artifact.id.toString()

        ingestionRun.ingestedCount++
    }


    fun startRun(transactionId: UUID, expectedArtifacts: List<String>, sourceSystem: SourceSystem) {
            val ingestionRun = IngestionRun(
                    id = transactionId,
                    expectedArtifacts = expectedArtifacts,
                    sourceSystem = sourceSystem,
                    )
    }

}