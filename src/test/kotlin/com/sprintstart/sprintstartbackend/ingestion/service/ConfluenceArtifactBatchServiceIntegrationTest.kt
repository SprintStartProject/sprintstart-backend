package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluenceArtifactBatchCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluenceCodeBlockCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluencePageArtifactCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluencePageMetadataCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluenceRelationshipCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluenceRelationshipType
import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluenceSectionCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.ConfluenceSourceAclCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

@ActiveProfiles("test")
@SpringBootTest
class ConfluenceArtifactBatchServiceIntegrationTest {
    @Autowired
    private lateinit var batchService: ConfluenceArtifactBatchService

    @Autowired
    private lateinit var artifactRepository: ArtifactRepository

    @Autowired
    private lateinit var runRepository: IngestionRunRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun `identical second run performs no update and preserves artifact identity and ingestion time`() {
        val projectId = UUID.randomUUID()
        val command = pageCommand()
        val firstRun = createRun()

        val firstResult = batchService.persist(batch(firstRun.id, projectId, command))
        val firstArtifact = requireNotNull(artifactRepository.findBySourceId(command.sourceId))
        val firstId = firstArtifact.id
        val firstIngestedAt = firstArtifact.ingestedAt
        val firstHash = firstArtifact.hash

        val secondRun = createRun()
        val secondResult = batchService.persist(
            batch(
                secondRun.id,
                projectId,
                command.copy(
                    metadata = command.metadata.copy(
                        sourceAcl = ConfluenceSourceAclCommand(
                            userAccountIds = listOf("account-b", "account-a", "account-a"),
                            groupIds = listOf("group-b", "group-a"),
                        ),
                    ),
                ),
            ),
        )
        val unchangedArtifact = requireNotNull(artifactRepository.findBySourceId(command.sourceId))

        assertThat(firstResult.created).isEqualTo(1)
        assertThat(secondResult.unchanged).isEqualTo(1)
        assertThat(secondResult.created).isZero()
        assertThat(secondResult.updated).isZero()
        assertThat(unchangedArtifact.id).isEqualTo(firstId)
        assertThat(unchangedArtifact.ingestedAt).isEqualTo(firstIngestedAt)
        assertThat(unchangedArtifact.hash).isEqualTo(firstHash)
        assertThat(unchangedArtifact.ingestionRun.id).isEqualTo(firstRun.id)
    }

    @Test
    fun `canonical field changes update the same artifact`() {
        val variants = listOf<(ConfluencePageArtifactCommand) -> ConfluencePageArtifactCommand>(
            { command -> command.copy(bodyText = "changed body") },
            { command -> command.copy(title = "Changed title") },
            { command -> command.copy(sourceVersion = "8") },
            { command ->
                command.copy(
                    metadata = command.metadata.copy(
                        relationships = listOf(
                            ConfluenceRelationshipCommand(ConfluenceRelationshipType.PARENT_OF, "300"),
                        ),
                    ),
                )
            },
            { command ->
                command.copy(
                    metadata = command.metadata.copy(
                        sourceAcl = ConfluenceSourceAclCommand(listOf("account-c"), listOf("group-a")),
                    ),
                )
            },
        )

        variants.forEachIndexed { index, change ->
            val projectId = UUID.randomUUID()
            val base = pageCommand(sourceId = "confluence:${UUID.randomUUID()}:page:$index")
            val firstRun = createRun()
            batchService.persist(batch(firstRun.id, projectId, base))
            val originalId = requireNotNull(artifactRepository.findBySourceId(base.sourceId)).id

            val updateRun = createRun()
            val result = batchService.persist(batch(updateRun.id, projectId, change(base)))
            val updated = requireNotNull(artifactRepository.findBySourceId(base.sourceId))

            assertThat(result.updated).isEqualTo(1)
            assertThat(result.created).isZero()
            assertThat(updated.id).isEqualTo(originalId)
            assertThat(updated.ingestionRun.id).isEqualTo(updateRun.id)
        }
    }

    @Test
    fun `created artifact is canonical PAGE with typed provenance and explicit empty ACL`() {
        val projectId = UUID.randomUUID()
        val run = createRun()
        val command = pageCommand().copy(
            metadata = pageCommand().metadata.copy(
                sourceAcl = ConfluenceSourceAclCommand(emptyList(), emptyList()),
            ),
        )

        batchService.persist(batch(run.id, projectId, command))
        val artifact = requireNotNull(artifactRepository.findBySourceId(command.sourceId))
        val projectIds = TransactionTemplate(transactionManager).execute {
            requireNotNull(artifactRepository.findBySourceId(command.sourceId)).projectIds
        }
        val metadata = objectMapper.readTree(artifact.metadata)
        val acl = objectMapper.readTree(metadata["sourceAcl"].stringValue())

        assertThat(artifact.sourceSystem).isEqualTo(SourceSystem.CONFLUENCE)
        assertThat(artifact.artifactType).isEqualTo(ArtifactType.PAGE)
        assertThat(projectIds).containsExactly(projectId)
        assertThat(artifact.sourceVersion).isEqualTo("7")
        assertThat(metadata["spaceId"].stringValue()).isEqualTo("42")
        assertThat(metadata["pageId"].stringValue()).isEqualTo("200")
        assertThat(metadata["versionNumber"].intValue()).isEqualTo(7)
        assertThat(acl["pageRestrictionsEvaluated"].booleanValue()).isTrue()
        assertThat(acl["hasPageRestrictions"].booleanValue()).isFalse()
        assertThat(acl["users"]).isEmpty()
        assertThat(acl["groups"]).isEmpty()
    }

    @Test
    fun `page-local persistence failure does not roll back a previously committed page`() {
        val projectId = UUID.randomUUID()
        val run = createRun()
        val successful = pageCommand(sourceId = "confluence:${UUID.randomUUID()}:page:successful")
        val rejected = pageCommand(sourceId = "x".repeat(300)).copy(
            metadata = pageCommand().metadata.copy(pageId = "rejected-page"),
        )

        val result = batchService.persist(
            ConfluenceArtifactBatchCommand(
                runId = run.id,
                projectId = projectId,
                artifacts = listOf(successful, rejected),
            ),
        )

        assertThat(result.created).isEqualTo(1)
        assertThat(result.failed).isEqualTo(1)
        assertThat(result.persistenceFailures.single().pageId).isEqualTo("rejected-page")
        assertThat(result.persistenceFailures.single().reason)
            .isEqualTo("Confluence page artifact could not be persisted")
        assertThat(artifactRepository.findBySourceId(successful.sourceId)).isNotNull()
        val persistedFailure = TransactionTemplate(transactionManager).execute {
            val persistedRun = requireNotNull(runRepository.findById(run.id).orElse(null))
            persistedRun.failedItems.single()
        }
        assertThat(persistedFailure?.sourceId).isEqualTo("rejected-page")
        assertThat(persistedFailure?.reason).contains("stage=PERSISTENCE")
    }

    private fun batch(runId: UUID, projectId: UUID, command: ConfluencePageArtifactCommand) =
        ConfluenceArtifactBatchCommand(runId, projectId, listOf(command))

    private fun createRun(): IngestionRun {
        return runRepository.saveAndFlush(
            IngestionRun(
                id = UUID.randomUUID(),
                sourceSystem = SourceSystem.CONFLUENCE,
                status = IngestionRunStatus.RUNNING,
            ),
        )
    }

    private fun pageCommand(
        sourceId: String = "confluence:${UUID.randomUUID()}:page:200",
    ): ConfluencePageArtifactCommand {
        val connectionId = UUID.randomUUID()
        val versionCreatedAt = Instant.parse("2026-08-01T10:00:00Z")
        return ConfluencePageArtifactCommand(
            sourceId = sourceId,
            sourceUrl = "https://tenant.atlassian.net/wiki/spaces/ENG/pages/200",
            sourceVersion = "7",
            title = "Runbook",
            bodyText = "Deploy the service.",
            versionCreatedAt = versionCreatedAt,
            metadata = ConfluencePageMetadataCommand(
                connectionId = connectionId,
                tenantBaseUrl = "https://tenant.atlassian.net",
                spaceId = "42",
                spaceKey = "ENG",
                pageId = "200",
                versionNumber = 7,
                versionCreatedAt = versionCreatedAt,
                parentId = "100",
                parentType = "page",
                webUiPath = "/wiki/spaces/ENG/pages/200",
                sections = listOf(ConfluenceSectionCommand("Deployment", 2)),
                tables = listOf("| Env |\n| --- |\n| prod |"),
                codeBlocks = listOf(ConfluenceCodeBlockCommand("bash", "kubectl apply")),
                relationships = listOf(
                    ConfluenceRelationshipCommand(ConfluenceRelationshipType.CHILD_OF, "100"),
                ),
                sourceAcl = ConfluenceSourceAclCommand(
                    userAccountIds = listOf("account-a", "account-b"),
                    groupIds = listOf("group-a", "group-b"),
                ),
            ),
        )
    }
}
