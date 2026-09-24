package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactFilterCriteria
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactSort
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactFacetsResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.FacetCountResponse
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.ArtifactMapper
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class ArtifactQueryServiceTest {
    private val artifactRepository = mockk<ArtifactRepository>()
    private val userApi = mockk<UserApi>()
    private val service = ArtifactQueryService(
        artifactRepository = artifactRepository,
        artifactMapper = ArtifactMapper(),
        userApi = userApi,
    )

    @Test
    fun `getAllArtifacts returns unfiltered page when filter is blank`() {
        val pageable = slot<Pageable>()
        val artifact = artifact()
        val page = PageImpl(
            listOf(artifact),
            PageRequest.of(1, 10),
            21,
        )
        every { artifactRepository.findAll(capture(pageable)) } returns page

        val result = service.getAllArtifacts(page = 2, size = 10, filter = "  ")

        assertThat(pageable.captured.pageNumber).isEqualTo(1)
        assertThat(pageable.captured.pageSize).isEqualTo(10)
        assertThat(
            pageable.captured.sort
                .getOrderFor("ingestedAt")
                ?.isDescending,
        ).isTrue()
        assertThat(result.items).hasSize(1)
        assertThat(result.items.single().id).isEqualTo(artifact.id)
        assertThat(result.items.single().metadata).isEqualTo("""{"repositoryFullName":"owner/repo"}""")
        assertThat(result.items.single().sourceVersion).isEqualTo("v1")
        assertThat(result.page.number).isEqualTo(2)
        assertThat(result.page.size).isEqualTo(10)
        assertThat(result.page.totalElements).isEqualTo(21)
        assertThat(result.page.totalPages).isEqualTo(3)
        assertThat(result.page.hasNext).isTrue()
        assertThat(result.page.hasPrevious).isTrue()
        verify(exactly = 0) { artifactRepository.search(any(), any()) }
    }

    @Test
    fun `getAllArtifacts trims non-blank filter and searches`() {
        val pageable = slot<Pageable>()
        every {
            artifactRepository.search("github", capture(pageable))
        } returns PageImpl(emptyList(), PageRequest.of(0, 20), 0)

        val result = service.getAllArtifacts(page = 1, size = 20, filter = " github ")

        assertThat(result.items).isEmpty()
        assertThat(pageable.captured.pageNumber).isZero()
        assertThat(pageable.captured.pageSize).isEqualTo(20)
        verify(exactly = 0) { artifactRepository.findAll(any<Pageable>()) }
    }

    @Test
    fun `getProjectArtifacts forwards criteria and enforces access`() {
        val projectId = UUID.randomUUID()
        val authId = "auth-1"
        val criteria = com.sprintstart.sprintstartbackend.ingestion.model.dto
            .ArtifactFilterCriteria(search = "doc")
        val pageable = slot<Pageable>()
        val responseItem = com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactResponse(
            id = UUID.randomUUID(),
            title = "doc.md",
            sourceSystem = SourceSystem.GITHUB,
            sourceId = "github:owner/repo:FILE:doc.md",
            sourceUrl = null,
            artifactType = ArtifactType.FILE,
            ingestedAt = Instant.now(),
            lastChangedAt = null,
            metadata = "{}",
        )
        every { userApi.userHasAccessToProject(authId, projectId) } returns true
        every {
            artifactRepository.findProjectArtifactsWithCriteria(
                projectId,
                criteria,
                ArtifactSort.TITLE_ASC,
                capture(pageable),
            )
        } returns PageImpl(listOf(responseItem), PageRequest.of(0, 20), 1)

        val result = service.getProjectArtifacts(1, 20, criteria, ArtifactSort.TITLE_ASC, projectId, authId)

        assertThat(result.items).hasSize(1)
        assertThat(result.items.single().title).isEqualTo("doc.md")
        // The repository owns ORDER BY, so the Pageable carries only the page window.
        assertThat(pageable.captured.pageNumber).isEqualTo(0)
        assertThat(pageable.captured.pageSize).isEqualTo(20)
        assertThat(pageable.captured.sort.isUnsorted).isTrue()
    }

    @Test
    fun `getProjectArtifactFacets returns facets from repository`() {
        val projectId = UUID.randomUUID()
        val authId = "auth-1"
        val criteria = ArtifactFilterCriteria()
        val facets = ArtifactFacetsResponse(
            types = listOf(FacetCountResponse("FILE", 5)),
            sources = listOf(FacetCountResponse("GITHUB", 5)),
            formats = emptyList(),
            repositories = listOf(FacetCountResponse("owner/repo", 5)),
        )
        every { userApi.userHasAccessToProject(authId, projectId) } returns true
        every { artifactRepository.findFacets(projectId, criteria) } returns facets

        val result = service.getProjectArtifactFacets(projectId, criteria, authId)

        assertThat(result.types.single().value).isEqualTo("FILE")
        assertThat(result.repositories.single().value).isEqualTo("owner/repo")
    }

    @Test
    fun `getProjectArtifacts rejects from after to with 400 before any lookup`() {
        val criteria = ArtifactFilterCriteria(from = LocalDate.of(2026, 3, 2), to = LocalDate.of(2026, 3, 1))

        val error = assertThrows<ResponseStatusException> {
            service.getProjectArtifacts(1, 20, criteria, ArtifactSort.ADDED_DESC, UUID.randomUUID(), "auth-1")
        }

        assertThat(error.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        verify(exactly = 0) { userApi.userHasAccessToProject(any(), any()) }
        verify(exactly = 0) { artifactRepository.findProjectArtifactsWithCriteria(any(), any(), any(), any()) }
    }

    @Test
    fun `getProjectArtifactFacets rejects from after to with 400 like the list`() {
        val criteria = ArtifactFilterCriteria(from = LocalDate.of(2026, 3, 2), to = LocalDate.of(2026, 3, 1))

        val error = assertThrows<ResponseStatusException> {
            service.getProjectArtifactFacets(UUID.randomUUID(), criteria, "auth-1")
        }

        assertThat(error.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        verify(exactly = 0) { artifactRepository.findFacets(any(), any()) }
    }

    @Test
    fun `getArtifact returns the projected artifact when found`() {
        val projectId = UUID.randomUUID()
        val artifactId = UUID.randomUUID()
        val authId = "auth-1"
        every { userApi.userHasAccessToProject(authId, projectId) } returns true
        every {
            artifactRepository.findProjectArtifactById(projectId, artifactId)
        } returns ArtifactResponse(
            id = artifactId,
            title = "README.md",
            sourceSystem = SourceSystem.GITHUB,
            sourceId = "github:owner/repo:FILE:README.md",
            sourceUrl = "https://github.com/owner/repo/blob/main/README.md",
            artifactType = ArtifactType.FILE,
            ingestedAt = Instant.parse("2026-06-19T09:16:30Z"),
            lastChangedAt = null,
            metadata = """{"repositoryFullName":"owner/repo"}""",
        )

        val result = service.getArtifact(projectId, artifactId, authId)

        assertThat(result.id).isEqualTo(artifactId)
        assertThat(result.title).isEqualTo("README.md")
    }

    @Test
    fun `a one-day window where from equals to is accepted`() {
        val projectId = UUID.randomUUID()
        val day = LocalDate.of(2026, 3, 1)
        val criteria = ArtifactFilterCriteria(from = day, to = day)
        every { userApi.userHasAccessToProject("auth-1", projectId) } returns true
        every { artifactRepository.findFacets(projectId, criteria) } returns
            ArtifactFacetsResponse(emptyList(), emptyList(), emptyList(), emptyList())

        service.getProjectArtifactFacets(projectId, criteria, "auth-1")

        verify(exactly = 1) { artifactRepository.findFacets(projectId, criteria) }
    }

    @Test
    fun `getArtifact throws 404 when not found in project`() {
        val projectId = UUID.randomUUID()
        val artifactId = UUID.randomUUID()
        val authId = "auth-1"
        every { userApi.userHasAccessToProject(authId, projectId) } returns true
        every { artifactRepository.findProjectArtifactById(projectId, artifactId) } returns null

        org.junit.jupiter.api.assertThrows<org.springframework.web.server.ResponseStatusException> {
            service.getArtifact(projectId, artifactId, authId)
        }
    }

    private fun artifact() = Artifact(
        id = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6"),
        sourceSystem = SourceSystem.GITHUB,
        sourceId = "github:owner/repo:FILE:README.md",
        sourceUrl = "https://github.com/owner/repo/blob/main/README.md",
        sourceVersion = "v1",
        artifactType = ArtifactType.FILE,
        title = "README.md",
        content = "content",
        mime = "text/markdown",
        language = "Markdown",
        metadata = """{"repositoryFullName":"owner/repo"}""",
        createdAtSource = null,
        updatedAtSource = Instant.parse("2026-06-19T09:15:30Z"),
        ingestedAt = Instant.parse("2026-06-19T09:16:30Z"),
        ingestionRun = IngestionRun(
            id = UUID.randomUUID(),
            sourceSystem = SourceSystem.GITHUB,
            status = IngestionRunStatus.COMPLETED,
        ),
        hash = "hash",
    )
}
