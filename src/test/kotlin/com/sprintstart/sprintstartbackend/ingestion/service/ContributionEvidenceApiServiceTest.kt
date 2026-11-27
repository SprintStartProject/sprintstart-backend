package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileAuthorshipReading
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileComplexityReading
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileContribution
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileKind
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileReview
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.PageEditReading
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FileComplexity
import com.sprintstart.sprintstartbackend.ingestion.repository.FileAuthorshipRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.FileChangeRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.FileComplexityRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.PageEditRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.ReviewActivityRepository
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ContributionEvidenceApiServiceTest {
    private val repository = mockk<FileChangeRepository>()
    private val reviews = mockk<ReviewActivityRepository>()
    private val complexities = mockk<FileComplexityRepository>()
    private val authorship = mockk<FileAuthorshipRepository>()
    private val pages = mockk<PageEditRepository>()
    private val service = ContributionEvidenceApiService(repository, reviews, complexities, authorship, pages)

    @Test
    fun `reads the project's page versions up to the date from the repository`() {
        val project = UUID.randomUUID()
        val asOf = Instant.parse("2026-05-01T00:00:00Z")
        val rows = listOf(PageEditReading("confluence:ENG", "Setup", "acc-1", asOf))
        every { pages.findEdits(project, asOf) } returns rows

        assertThat(service.pageEdits(ProjectId(project), asOf)).isEqualTo(rows)
    }

    @Test
    fun `reads the project's changes up to the date from the repository`() {
        val project = UUID.randomUUID()
        val asOf = Instant.parse("2026-05-01T00:00:00Z")
        val rows = listOf(FileContribution("github:acme/app", "a.kt", null, "ada@x.org", asOf))
        every { repository.findContributions(project, asOf) } returns rows

        assertThat(service.fileContributions(ProjectId(project), asOf)).isEqualTo(rows)
    }

    @Test
    fun `reads the project's reviews up to the date from the repository`() {
        val project = UUID.randomUUID()
        val asOf = Instant.parse("2026-05-01T00:00:00Z")
        val rows = listOf(FileReview("github:acme/app", "a.kt", "ada", asOf))
        every { reviews.findReviews(project, asOf) } returns rows

        assertThat(service.fileReviews(ProjectId(project), asOf)).isEqualTo(rows)
    }

    private fun measured(path: String, kind: FileKind? = null, complexity: Double? = null) =
        FileComplexity(null, "github:acme/app", path, 2.5, 10, Instant.parse("2026-05-01T00:00:00Z"), kind, complexity)

    @Test
    fun `reads the complexity of the files of the given repositories in the unit of their kind`() {
        every { complexities.readAll(listOf("github:acme/app")) } returns
            listOf(measured("a.kt", FileKind.CODE, 2.5), measured("a.md", FileKind.PROSE, 300.0))

        assertThat(service.fileComplexities(listOf("github:acme/app"))).containsExactly(
            FileComplexityReading("github:acme/app", "a.kt", FileKind.CODE, 2.5, 10),
            FileComplexityReading("github:acme/app", "a.md", FileKind.PROSE, 300.0, 10),
        )
    }

    @Test
    fun `a file measured before kinds counts by indentation only when it is code`() {
        every { complexities.readAll(listOf("github:acme/app")) } returns
            listOf(measured("a.kt"), measured("a.md"), measured("app.yaml"))

        assertThat(service.fileComplexities(listOf("github:acme/app"))).containsExactly(
            FileComplexityReading("github:acme/app", "a.kt", FileKind.CODE, 2.5, 10),
        )
    }

    @Test
    fun `asks nothing when no repository is given`() {
        assertThat(service.fileComplexities(emptyList())).isEmpty()
    }

    @Test
    fun `reads who wrote the lines of the files of the given repositories`() {
        val rows = listOf(FileAuthorshipReading("github:acme/app", "a.kt", "ada@x.org", 12))
        every { authorship.readAll(listOf("github:acme/app")) } returns rows

        assertThat(service.fileAuthorship(listOf("github:acme/app"))).isEqualTo(rows)
    }

    @Test
    fun `asks nothing about authors when no repository is given`() {
        assertThat(service.fileAuthorship(emptyList())).isEmpty()
    }
}
