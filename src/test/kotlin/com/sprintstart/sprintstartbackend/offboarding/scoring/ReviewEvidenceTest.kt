package com.sprintstart.sprintstartbackend.offboarding.scoring

import com.sprintstart.sprintstartbackend.ingestion.external.ContributionEvidenceApi
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileContribution
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileReview
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ReviewEvidenceTest {
    private val project = ProjectId(UUID.randomUUID())

    private fun review(path: String, login: String, daysAgo: Long, repository: String = REPOSITORY) =
        FileReview(repository, path, login, daysAgo(daysAgo))

    private fun evidenceOf(vararg reviews: FileReview) = ReviewEvidence(
        object : ContributionEvidenceApi {
            override fun fileContributions(projectId: ProjectId, asOf: Instant): List<FileContribution> = emptyList()

            override fun fileReviews(projectId: ProjectId, asOf: Instant): List<FileReview> =
                if (projectId == project && asOf == NOW) reviews.toList() else emptyList()
        },
    )

    @Test
    fun `the source is named reviews`() {
        assertThat(evidenceOf().id).isEqualTo(SourceId.REVIEWS)
    }

    @Test
    fun `reviews of one file become one history in the order they were read`() {
        val histories = evidenceOf(
            review("core.kt", "Ada", 30),
            review("core.kt", "ben", 10),
        ).histories(project, NOW)

        val history = histories.single()
        assertThat(history.path).isEqualTo("core.kt")
        assertThat(history.touches.map { it.person }).containsExactly(PersonKey("ada"), PersonKey("ben"))
        assertThat(history.touches.map { it.at }).isSorted()
    }

    @Test
    fun `a review is a reviews touch that cannot found the file`() {
        val touch = evidenceOf(review("core.kt", "ada", 5))
            .histories(project, NOW)
            .single()
            .touches
            .single()

        assertThat(touch.source).isEqualTo(SourceId.REVIEWS)
        assertThat(touch.founding).isFalse()
    }

    @Test
    fun `the same path in two repositories stays two files`() {
        val histories = evidenceOf(
            review("core.kt", "ada", 5),
            review("core.kt", "ada", 4, repository = "github:acme/other"),
        ).histories(project, NOW)

        assertThat(histories.map { it.repository }).containsExactlyInAnyOrder(REPOSITORY, "github:acme/other")
    }

    @Test
    fun `a reviewer is matched to commit authors by their login`() {
        val reviewer = evidenceOf(
            review("core.kt", "ada-lovelace", 5),
        ).histories(project, NOW).single().touches.single()

        assertThat(reviewer.person).isEqualTo(PersonKey.ofEmail("12345+Ada-Lovelace@users.noreply.github.com"))
    }

    @Test
    fun `a project without reviews has no histories`() {
        assertThat(evidenceOf().histories(project, NOW)).isEmpty()
    }
}
