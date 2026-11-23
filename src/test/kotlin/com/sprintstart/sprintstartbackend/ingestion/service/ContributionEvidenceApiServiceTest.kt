package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileAuthorshipReading
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileComplexityReading
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileContribution
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileReview
import com.sprintstart.sprintstartbackend.ingestion.repository.FileAuthorshipRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.FileChangeRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.FileComplexityRepository
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
    private val service = ContributionEvidenceApiService(repository, reviews, complexities, authorship)

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

    @Test
    fun `reads the complexity of the files of the given repositories`() {
        val rows = listOf(FileComplexityReading("github:acme/app", "a.kt", 2.5, 10))
        every { complexities.readAll(listOf("github:acme/app")) } returns rows

        assertThat(service.fileComplexities(listOf("github:acme/app"))).isEqualTo(rows)
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
