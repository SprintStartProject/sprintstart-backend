package com.sprintstart.sprintstartbackend.canonical.service

import com.sprintstart.sprintstartbackend.canonical.model.entity.Artifact
import com.sprintstart.sprintstartbackend.canonical.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.canonical.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.canonical.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.canonical.model.entity.SourceSystem
import com.sprintstart.sprintstartbackend.canonical.model.mapper.ArtifactMapper
import com.sprintstart.sprintstartbackend.canonical.repository.ArtifactRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.time.Instant
import java.util.UUID

class ArtifactQueryServiceTest {
    private val artifactRepository = mockk<ArtifactRepository>()
    private val service = ArtifactQueryService(
        artifactRepository = artifactRepository,
        artifactMapper = ArtifactMapper(),
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
        assertThat(result.items.single().repositoryFullName).isEqualTo("owner/repo")
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

    private fun artifact() = Artifact(
        id = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6"),
        sourceSystem = SourceSystem.GITHUB,
        sourceId = "github:owner/repo:FILE:README.md",
        sourceUrl = "https://github.com/owner/repo/blob/main/README.md",
        repositoryFullName = "owner/repo",
        artifactType = ArtifactType.FILE,
        title = "README.md",
        bodyText = "content",
        mime = "text/markdown",
        language = "Markdown",
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
