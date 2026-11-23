package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluenceCodeBlockCommand
import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluencePageArtifactCommand
import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluencePageMetadataCommand
import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluenceRelationshipCommand
import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluenceRelationshipType
import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluenceSectionCommand
import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluenceSourceAclCommand
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactCodeBlock
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactRelationship
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactRelationshipType
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactSection
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ConfluenceArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.model.exceptions.IngestionRunNotFoundException
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.ArtifactMetadataJsonMapper
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import com.sprintstart.sprintstartbackend.shared.model.SourceSystem
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import java.util.Optional
import java.util.UUID

class ConfluenceArtifactItemPersistenceServiceTest {
    private val ingestionRunRepository = mockk<IngestionRunRepository>()
    private val artifactRepository = mockk<ArtifactRepository>()
    private val metadataJsonMapper = mockk<ArtifactMetadataJsonMapper>()
    private val service = ConfluenceArtifactItemPersistenceService(
        ingestionRunRepository,
        artifactRepository,
        metadataJsonMapper,
        jacksonObjectMapper(),
    )

    private val projectId = UUID.randomUUID()
    private val connectionId = UUID.randomUUID()
    private val run = IngestionRun(
        id = UUID.randomUUID(),
        sourceSystem = SourceSystem.CONFLUENCE,
        status = IngestionRunStatus.RUNNING,
    )
    private val metadataSlot = slot<ConfluenceArtifactMetadata>()

    @Test
    fun `creates then leaves unchanged then updates the same page artifact`() {
        val command = command()
        val artifactSlot = slot<Artifact>()
        givenRunAndMapper()
        every { artifactRepository.findBySourceSystemAndSourceId(any(), command.sourceId) } returns null
        every { artifactRepository.saveAndFlush(capture(artifactSlot)) } answers { firstArg() }
        every { artifactRepository.flush() } just Runs

        val created = service.persist(run.id, projectId, command)
        val artifact = artifactSlot.captured
        every {
            artifactRepository.findBySourceSystemAndSourceId(SourceSystem.CONFLUENCE, command.sourceId)
        } returns artifact
        val unchanged = service.persist(run.id, projectId, command)
        val edited = command.copy(title = "Renamed", bodyText = "changed body", sourceUrl = "https://example/new")
        val updated = service.persist(run.id, projectId, edited)

        assertThat(created).isEqualTo(ConfluenceArtifactItemPersistenceResult.CREATED)
        assertThat(unchanged).isEqualTo(ConfluenceArtifactItemPersistenceResult.UNCHANGED)
        assertThat(updated).isEqualTo(ConfluenceArtifactItemPersistenceResult.UPDATED)
        assertThat(run.ingestedCount).isEqualTo(1)
        assertThat(run.updatedCount).isEqualTo(1)
        assertThat(artifact.sourceSystem).isEqualTo(SourceSystem.CONFLUENCE)
        assertThat(artifact.artifactType).isEqualTo(ArtifactType.PAGE)
        assertThat(artifact.mime).isEqualTo("text/markdown")
        assertThat(artifact.title).isEqualTo("Renamed")
        assertThat(artifact.content).isEqualTo("changed body")
        assertThat(artifact.sourceUrl).isEqualTo("https://example/new")
        assertThat(artifact.sourceVersion).isEqualTo(command.sourceVersion)
        assertThat(artifact.updatedAtSource).isEqualTo(command.versionCreatedAt)
        assertThat(artifact.metadata).isEqualTo("{}")
        assertThat(artifact.ingestionRun).isSameAs(run)
        assertThat(artifact.hash).hasSize(64)
        assertThat(artifact.projectIds).containsExactly(projectId)
    }

    @Test
    fun `any change to the page content changes the hash`() {
        val command = command()
        val hashes = mutableListOf<String?>()
        givenRunAndMapper()
        every { artifactRepository.findBySourceSystemAndSourceId(any(), any()) } returns null
        every { artifactRepository.saveAndFlush(any<Artifact>()) } answers {
            firstArg<Artifact>().also { hashes += it.hash }
        }

        listOf(
            command,
            command.copy(sourceUrl = "https://example/other"),
            command.copy(sourceVersion = "4"),
            command.copy(title = "Other title"),
            command.copy(bodyText = "other body"),
            command.copy(versionCreatedAt = Instant.parse("2026-10-01T10:00:00Z")),
        ).forEach { service.persist(run.id, projectId, it) }

        assertThat(hashes).doesNotHaveDuplicates()
    }

    @Test
    fun `maps the page structure into the metadata with relationships distinct and ordered`() {
        givenRunAndMapper()
        every { artifactRepository.findBySourceSystemAndSourceId(any(), any()) } returns null
        every { artifactRepository.saveAndFlush(any<Artifact>()) } answers { firstArg() }

        service.persist(run.id, projectId, command())

        val metadata = metadataSlot.captured
        assertThat(metadata.connectionId).isEqualTo(connectionId.toString())
        assertThat(metadata.tenantBaseUrl).isEqualTo("https://example.atlassian.net")
        assertThat(metadata.spaceId).isEqualTo("space-1")
        assertThat(metadata.spaceKey).isEqualTo("ENG")
        assertThat(metadata.pageId).isEqualTo("page-1")
        assertThat(metadata.versionNumber).isEqualTo(3)
        assertThat(metadata.versionCreatedAt).isEqualTo("2026-09-27T10:00:00Z")
        assertThat(metadata.parentId).isEqualTo("parent-1")
        assertThat(metadata.parentType).isEqualTo("page")
        assertThat(metadata.webUiPath).isEqualTo("/spaces/ENG/pages/1")
        assertThat(metadata.sections).containsExactly(ArtifactSection("Heading", 1))
        assertThat(metadata.tables).containsExactly("| A |")
        assertThat(metadata.codeBlocks).containsExactly(ArtifactCodeBlock("kotlin", "println(1)"))
        assertThat(metadata.relationships).containsExactly(
            ArtifactRelationship(ArtifactRelationshipType.CHILD_OF, "a-parent"),
            ArtifactRelationship(ArtifactRelationshipType.CHILD_OF, "b-parent"),
            ArtifactRelationship(ArtifactRelationshipType.PARENT_OF, "child-1"),
        )
    }

    @Test
    fun `stores page restrictions sorted and without duplicates`() {
        givenRunAndMapper()
        every { artifactRepository.findBySourceSystemAndSourceId(any(), any()) } returns null
        every { artifactRepository.saveAndFlush(any<Artifact>()) } answers { firstArg() }

        service.persist(run.id, projectId, command())

        assertThat(metadataSlot.captured.sourceAcl).isEqualTo(
            """{"operation":"read","pageRestrictionsEvaluated":true,"hasPageRestrictions":true,""" +
                """"users":["u1","u2"],"groups":["g1"]}""",
        )
    }

    @Test
    fun `a page without restrictions says so`() {
        val command = command()
        val open = command.copy(
            metadata = command.metadata.copy(sourceAcl = ConfluenceSourceAclCommand(emptyList(), emptyList())),
        )
        givenRunAndMapper()
        every { artifactRepository.findBySourceSystemAndSourceId(any(), any()) } returns null
        every { artifactRepository.saveAndFlush(any<Artifact>()) } answers { firstArg() }

        service.persist(run.id, projectId, open)

        assertThat(metadataSlot.captured.sourceAcl).contains(""""hasPageRestrictions":false""")
    }

    @Test
    fun `a page restricted to groups only counts as restricted`() {
        val command = command()
        val groupsOnly = command.copy(
            metadata = command.metadata.copy(sourceAcl = ConfluenceSourceAclCommand(emptyList(), listOf("g1"))),
        )
        givenRunAndMapper()
        every { artifactRepository.findBySourceSystemAndSourceId(any(), any()) } returns null
        every { artifactRepository.saveAndFlush(any<Artifact>()) } answers { firstArg() }

        service.persist(run.id, projectId, groupsOnly)

        assertThat(metadataSlot.captured.sourceAcl).contains(""""hasPageRestrictions":true""")
    }

    @Test
    fun `fails when the ingestion run does not exist`() {
        every { ingestionRunRepository.findByIdForUpdate(run.id) } returns Optional.empty()

        assertThatThrownBy { service.persist(run.id, projectId, command()) }
            .isInstanceOf(IngestionRunNotFoundException::class.java)
    }

    private fun givenRunAndMapper() {
        every { ingestionRunRepository.findByIdForUpdate(run.id) } returns Optional.of(run)
        every { metadataJsonMapper.toJson(capture(metadataSlot)) } returns "{}"
    }

    private fun command(): ConfluencePageArtifactCommand = ConfluencePageArtifactCommand(
        sourceId = "confluence:connection-1:page:page-1",
        sourceUrl = "https://example.atlassian.net/wiki/page-1",
        sourceVersion = "3",
        title = "Sprint Start",
        bodyText = "body",
        versionCreatedAt = Instant.parse("2026-09-27T10:00:00Z"),
        metadata = ConfluencePageMetadataCommand(
            connectionId = connectionId,
            tenantBaseUrl = "https://example.atlassian.net",
            spaceId = "space-1",
            spaceKey = "ENG",
            pageId = "page-1",
            versionNumber = 3,
            versionCreatedAt = Instant.parse("2026-09-27T10:00:00Z"),
            parentId = "parent-1",
            parentType = "page",
            webUiPath = "/spaces/ENG/pages/1",
            sections = listOf(ConfluenceSectionCommand("Heading", 1)),
            tables = listOf("| A |"),
            codeBlocks = listOf(ConfluenceCodeBlockCommand("kotlin", "println(1)")),
            relationships = listOf(
                ConfluenceRelationshipCommand(ConfluenceRelationshipType.PARENT_OF, "child-1"),
                ConfluenceRelationshipCommand(ConfluenceRelationshipType.CHILD_OF, "b-parent"),
                ConfluenceRelationshipCommand(ConfluenceRelationshipType.CHILD_OF, "a-parent"),
                ConfluenceRelationshipCommand(ConfluenceRelationshipType.CHILD_OF, "a-parent"),
            ),
            sourceAcl = ConfluenceSourceAclCommand(
                userAccountIds = listOf("u2", "u1", "u2"),
                groupIds = listOf("g1", "g1"),
            ),
        ),
    )
}
