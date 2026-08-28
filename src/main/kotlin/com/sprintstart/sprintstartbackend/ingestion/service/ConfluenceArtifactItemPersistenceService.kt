package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluencePageArtifactCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluenceRelationshipType
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactCodeBlock
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactRelationship
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactRelationshipType
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactSection
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ConfluenceArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.exceptions.IngestionRunNotFoundException
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.ArtifactMetadataJsonMapper
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import com.sprintstart.sprintstartbackend.ingestion.util.sha256
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.util.UUID

internal enum class ConfluenceArtifactItemPersistenceResult {
    CREATED,
    UPDATED,
    UNCHANGED,
}

/** Upserts one Confluence artifact in an independent transaction. */
@Service
internal class ConfluenceArtifactItemPersistenceService(
    private val ingestionRunRepository: IngestionRunRepository,
    private val artifactRepository: ArtifactRepository,
    private val metadataJsonMapper: ArtifactMetadataJsonMapper,
    private val objectMapper: ObjectMapper,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun persist(
        runId: UUID,
        projectId: UUID,
        command: ConfluencePageArtifactCommand,
    ): ConfluenceArtifactItemPersistenceResult {
        val run = ingestionRunRepository.findByIdForUpdate(runId).orElseThrow {
            IngestionRunNotFoundException(runId)
        }
        val metadataJson = metadataJson(command)
        val hash = contentHash(command, metadataJson)
        val existing = artifactRepository.findBySourceSystemAndSourceId(SourceSystem.CONFLUENCE, command.sourceId)
        if (existing == null) {
            artifactRepository.saveAndFlush(command.toArtifact(projectId, run, metadataJson, hash))
            run.ingestedCount++
            return ConfluenceArtifactItemPersistenceResult.CREATED
        }
        if (existing.hash == hash) {
            return ConfluenceArtifactItemPersistenceResult.UNCHANGED
        }

        updateExisting(existing, command, projectId, run, metadataJson, hash)
        artifactRepository.flush()
        run.updatedCount++
        return ConfluenceArtifactItemPersistenceResult.UPDATED
    }

    private fun updateExisting(
        existing: Artifact,
        command: ConfluencePageArtifactCommand,
        projectId: UUID,
        run: IngestionRun,
        metadataJson: String,
        hash: String,
    ) {
        existing.sourceUrl = command.sourceUrl
        existing.sourceVersion = command.sourceVersion
        existing.title = command.title
        existing.content = command.bodyText
        existing.metadata = metadataJson
        existing.updatedAtSource = command.versionCreatedAt
        existing.ingestionRun = run
        existing.hash = hash
        existing.addProjectId(projectId)
    }

    private fun metadataJson(command: ConfluencePageArtifactCommand): String {
        val sourceAcl = command.metadata.sourceAcl
        val aclJson = objectMapper.writeValueAsString(
            ConfluenceSourceAclPayload(
                hasPageRestrictions = sourceAcl.userAccountIds.isNotEmpty() || sourceAcl.groupIds.isNotEmpty(),
                users = sourceAcl.userAccountIds.distinct().sorted(),
                groups = sourceAcl.groupIds.distinct().sorted(),
            ),
        )
        return metadataJsonMapper.toJson(
            ConfluenceArtifactMetadata(
                connectionId = command.metadata.connectionId.toString(),
                tenantBaseUrl = command.metadata.tenantBaseUrl,
                spaceId = command.metadata.spaceId,
                spaceKey = command.metadata.spaceKey,
                pageId = command.metadata.pageId,
                versionNumber = command.metadata.versionNumber,
                versionCreatedAt = command.metadata.versionCreatedAt.toString(),
                parentId = command.metadata.parentId,
                parentType = command.metadata.parentType,
                webUiPath = command.metadata.webUiPath,
                sections = command.metadata.sections.map { section ->
                    ArtifactSection(section.heading, section.level)
                },
                tables = command.metadata.tables,
                codeBlocks = command.metadata.codeBlocks.map { block ->
                    ArtifactCodeBlock(block.language, block.code)
                },
                relationships = command.metadata.relationships
                    .distinct()
                    .sortedWith(compareBy({ relationship -> relationship.type.name }, { it.targetSourceArtifactId }))
                    .map { relationship ->
                        ArtifactRelationship(
                            type = when (relationship.type) {
                                ConfluenceRelationshipType.PARENT_OF -> ArtifactRelationshipType.PARENT_OF
                                ConfluenceRelationshipType.CHILD_OF -> ArtifactRelationshipType.CHILD_OF
                            },
                            targetSourceArtifactId = relationship.targetSourceArtifactId,
                        )
                    },
                sourceAcl = aclJson,
            ),
        )
    }

    private fun contentHash(command: ConfluencePageArtifactCommand, metadataJson: String): String {
        val canonical = buildString {
            appendCanonical(command.sourceUrl)
            appendCanonical(command.sourceVersion)
            appendCanonical(command.title)
            appendCanonical(command.bodyText)
            appendCanonical(command.versionCreatedAt.toString())
            appendCanonical(metadataJson)
        }
        return canonical.toByteArray(Charsets.UTF_8).sha256()
    }

    private fun StringBuilder.appendCanonical(value: String?) {
        val safeValue = value.orEmpty()
        append(safeValue.length).append(':').append(safeValue).append('|')
    }

    private fun ConfluencePageArtifactCommand.toArtifact(
        projectId: UUID,
        run: IngestionRun,
        metadataJson: String,
        hash: String,
    ): Artifact {
        return Artifact(
            sourceSystem = SourceSystem.CONFLUENCE,
            sourceId = sourceId,
            sourceUrl = sourceUrl,
            sourceVersion = sourceVersion,
            artifactType = ArtifactType.PAGE,
            title = title,
            content = bodyText,
            mime = "text/plain",
            language = null,
            metadata = metadataJson,
            projectIdsInternal = mutableSetOf(projectId),
            createdAtSource = null,
            updatedAtSource = versionCreatedAt,
            ingestionRun = run,
            hash = hash,
        )
    }

    private data class ConfluenceSourceAclPayload(
        val operation: String = "read",
        val pageRestrictionsEvaluated: Boolean = true,
        val hasPageRestrictions: Boolean,
        val users: List<String>,
        val groups: List<String>,
    )
}
