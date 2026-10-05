package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.external.model.NotionArtifactWriteOutcome
import com.sprintstart.sprintstartbackend.ingestion.external.model.NotionArtifactWriteResult
import com.sprintstart.sprintstartbackend.ingestion.external.model.NotionPageArtifactCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactCodeBlock
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactSection
import com.sprintstart.sprintstartbackend.ingestion.model.dto.NotionArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FailedArtifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.exceptions.IngestionRunNotFoundException
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.ArtifactMetadataJsonMapper
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import com.sprintstart.sprintstartbackend.ingestion.util.sha256
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

private const val NOTION_ARTIFACT_MIME = "text/markdown"

/** Upserts one Notion page as one canonical PAGE artifact. */
@Service
internal class NotionArtifactItemPersistenceService(
    private val ingestionRunRepository: IngestionRunRepository,
    private val artifactRepository: ArtifactRepository,
    private val metadataJsonMapper: ArtifactMetadataJsonMapper,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun persist(
        runId: UUID,
        projectId: UUID,
        command: NotionPageArtifactCommand,
    ): NotionArtifactWriteResult {
        val run = ingestionRunRepository.findByIdForUpdate(runId).orElseThrow {
            IngestionRunNotFoundException(runId)
        }
        val metadataJson = metadataJson(command)
        val contentHash = contentHash(command, metadataJson)
        val existing = artifactRepository.findBySourceSystemAndSourceId(SourceSystem.NOTION, command.sourceId)

        if (existing == null) {
            artifactRepository.saveAndFlush(command.toArtifact(projectId, run, metadataJson, contentHash))
            run.ingestedCount++
            return NotionArtifactWriteResult(NotionArtifactWriteOutcome.CREATED, contentHash)
        }
        if (existing.hash == contentHash && projectId in existing.projectIds) {
            return NotionArtifactWriteResult(NotionArtifactWriteOutcome.UNCHANGED, contentHash)
        }

        updateExisting(existing, command, projectId, run, metadataJson, contentHash)
        artifactRepository.flush()
        run.updatedCount++
        run.artifactIdsToReingest.add(existing.id)
        return NotionArtifactWriteResult(NotionArtifactWriteOutcome.UPDATED, contentHash)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordFailure(runId: UUID, sourceId: String, sourceUrl: String?, reason: String) {
        val run = ingestionRunRepository.findByIdForUpdate(runId).orElseThrow { IngestionRunNotFoundException(runId) }
        run.failedCount++
        run.failedItems.add(FailedArtifact(sourceId, ArtifactType.PAGE, sourceUrl, reason))
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordUnlinked(runId: UUID) {
        val run = ingestionRunRepository.findByIdForUpdate(runId).orElseThrow { IngestionRunNotFoundException(runId) }
        run.deletedCount++
    }

    private fun metadataJson(command: NotionPageArtifactCommand): String {
        return metadataJsonMapper.toJson(
            NotionArtifactMetadata(
                notionConnectionId = command.metadata.connectionId.toString(),
                notionPageId = command.metadata.pageId,
                sections = command.metadata.sections.map { section ->
                    ArtifactSection(section.heading, section.level)
                },
                tables = command.metadata.tables,
                codeBlocks = command.metadata.codeBlocks.map { block ->
                    ArtifactCodeBlock(block.language, block.code)
                },
            ),
        )
    }

    private fun contentHash(command: NotionPageArtifactCommand, metadataJson: String): String {
        val canonical = buildString {
            appendCanonical(command.sourceUrl)
            appendCanonical(command.sourceVersion)
            appendCanonical(command.title)
            appendCanonical(command.bodyText)
            appendCanonical(command.lastEditedTime.toString())
            appendCanonical(metadataJson)
        }
        return canonical.toByteArray(Charsets.UTF_8).sha256()
    }

    private fun StringBuilder.appendCanonical(value: String?) {
        val safeValue = value.orEmpty()
        append(safeValue.length).append(':').append(safeValue).append('|')
    }

    private fun updateExisting(
        existing: Artifact,
        command: NotionPageArtifactCommand,
        projectId: UUID,
        run: IngestionRun,
        metadataJson: String,
        contentHash: String,
    ) {
        existing.sourceUrl = command.sourceUrl
        existing.sourceVersion = command.sourceVersion
        existing.title = command.title
        existing.content = command.bodyText
        existing.mime = NOTION_ARTIFACT_MIME
        existing.metadata = metadataJson
        existing.updatedAtSource = command.lastEditedTime
        existing.ingestionRun = run
        existing.hash = contentHash
        existing.addProjectId(projectId)
    }

    private fun NotionPageArtifactCommand.toArtifact(
        projectId: UUID,
        run: IngestionRun,
        metadataJson: String,
        contentHash: String,
    ): Artifact {
        return Artifact(
            sourceSystem = SourceSystem.NOTION,
            sourceId = sourceId,
            sourceUrl = sourceUrl,
            sourceVersion = sourceVersion,
            artifactType = ArtifactType.PAGE,
            title = title,
            content = bodyText,
            mime = NOTION_ARTIFACT_MIME,
            language = null,
            metadata = metadataJson,
            projectIdsInternal = mutableSetOf(projectId),
            createdAtSource = null,
            updatedAtSource = lastEditedTime,
            ingestionRun = run,
            hash = contentHash,
        )
    }
}
