package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileContribution
import com.sprintstart.sprintstartbackend.ingestion.repository.FileChangeRepository
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ContributionEvidenceApiServiceTest {
    private val repository = mockk<FileChangeRepository>()
    private val service = ContributionEvidenceApiService(repository)

    @Test
    fun `reads the project's changes up to the date from the repository`() {
        val project = UUID.randomUUID()
        val asOf = Instant.parse("2026-05-01T00:00:00Z")
        val rows = listOf(FileContribution("github:acme/app", "a.kt", null, "ada@x.org", asOf))
        every { repository.findContributions(project, asOf) } returns rows

        assertThat(service.fileContributions(ProjectId(project), asOf)).isEqualTo(rows)
    }
}
