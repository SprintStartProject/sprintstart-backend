package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluenceArtifactBatchCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluenceArtifactBatchResult
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
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FailedArtifact
import com.sprintstart.sprintstartbackend.ingestion.model.exceptions.IngestionRunNotFoundException
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.ArtifactMetadataJsonMapper
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import com.sprintstart.sprintstartbackend.ingestion.util.sha256
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

/** Atomically upserts one prepared Confluence page batch and its item failures. */
@Service
internal class ConfluenceArtifactBatchService(
    private val ingestionRunRepository: IngestionRunRepository,
    private val artifactRepository: ArtifactRepository,
    private val metadataJsonMapper: ArtifactMetadataJsonMapper,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun persist(command: ConfluenceArtifactBatchCommand): ConfluenceArtifactBatchResult {
        val sourceIds = command.artifacts.map { artifact -> artifact.sourceId }
        require(sourceIds.distinct().size == command.artifacts.size) {
            "Confluence ingestion batch contains duplicate source identities"
        }
        val run = ingestionRunRepository.findByIdForUpdate(command.runId).orElseThrow {
            IngestionRunNotFoundException(command.runId)
        }
        val existingBySourceId = artifactRepository
            .findAllBySourceSystemAndSourceIdIn(SourceSystem.CONFLUENCE, sourceIds)
            .associateBy { artifact -> artifact.sourceId }
        val newArtifacts = mutableListOf<Artifact>()
        var updated = 0
        var unchanged = 0

        command.artifacts.forEach { artifactCommand ->
            val metadataJson = metadataJson(artifactCommand)
            val hash = contentHash(artifactCommand, metadataJson)
            val existing = existingBySourceId[artifactCommand.sourceId]
            if (existing == null) {
                newArtifacts += artifactCommand.toArtifact(command.projectId, run, metadataJson, hash)
            } else if (existing.hash == hash) {
                unchanged++
            } else {
                existing.sourceUrl = artifactCommand.sourceUrl
                existing.sourceVersion = artifactCommand.sourceVersion
                existing.title = artifactCommand.title
                existing.content = artifactCommand.bodyText
                existing.metadata = metadataJson
                existing.updatedAtSource = artifactCommand.versionCreatedAt
                existing.ingestionRun = run
                existing.hash = hash
                existing.addProjectId(command.projectId)
                updated++
            }
        }

        if (newArtifacts.isNotEmpty()) {
            artifactRepository.saveAll(newArtifacts)
        }
        command.failures.forEach { failure ->
            run.failedItems += FailedArtifact(
                sourceId = failure.pageId,
                artifactType = ArtifactType.PAGE,
                sourceUrl = failure.sourceUrl,
                reason = failure.reason,
            )
        }
        run.ingestedCount += newArtifacts.size
        run.updatedCount += updated
        run.failedCount += command.failures.size

        return ConfluenceArtifactBatchResult(
            created = newArtifacts.size,
            updated = updated,
            unchanged = unchanged,
            failed = command.failures.size,
        )
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
        projectId: java.util.UUID,
        run: com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun,
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
