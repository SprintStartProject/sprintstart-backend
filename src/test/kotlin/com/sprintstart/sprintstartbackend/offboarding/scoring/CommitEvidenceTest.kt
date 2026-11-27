package com.sprintstart.sprintstartbackend.offboarding.scoring

import com.sprintstart.sprintstartbackend.ingestion.external.ContributionEvidenceApi
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.CommitTicket
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileAuthorshipReading
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileComplexityReading
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileContribution
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileReview
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.IssueActivityReading
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.PageEditReading
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class CommitEvidenceTest {
    private val project = ProjectId(UUID.randomUUID())
    private val recorded = listOf(contribution("a.kt", "ada@x.org", daysAgo(3)))

    private val api = object : ContributionEvidenceApi {
        override fun fileContributions(projectId: ProjectId, asOf: Instant): List<FileContribution> {
            val asked = projectId == project && asOf == NOW
            return if (asked) recorded else emptyList()
        }

        override fun fileReviews(projectId: ProjectId, asOf: Instant): List<FileReview> = emptyList()

        override fun pageEdits(projectId: ProjectId, asOf: Instant): List<PageEditReading> = emptyList()

        override fun issueActivities(projectId: ProjectId, asOf: Instant): List<IssueActivityReading> = emptyList()

        override fun commitTickets(projectId: ProjectId): List<CommitTicket> = emptyList()

        override fun fileComplexities(repositories: Collection<String>): List<FileComplexityReading> = emptyList()

        override fun fileAuthorship(repositories: Collection<String>): List<FileAuthorshipReading> = emptyList()
    }

    @Test
    fun `commits become histories whose touches name the commit source`() {
        val history = CommitEvidence(api).histories(project, NOW).single()

        assertThat(history.path).isEqualTo("a.kt")
        assertThat(history.touches.map { it.source }).containsExactly(SourceId.COMMITS)
    }

    @Test
    fun `the source is named commits`() {
        assertThat(CommitEvidence(api).id).isEqualTo(SourceId.COMMITS)
    }
}
