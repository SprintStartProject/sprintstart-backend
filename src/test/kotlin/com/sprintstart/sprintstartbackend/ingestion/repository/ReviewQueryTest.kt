package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileReview
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ReviewActivity
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ReviewState
import com.sprintstart.sprintstartbackend.shared.crypto.CryptoConfiguration
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.UUID

/** The project and date filters of the review read are database features, so they meet a real one. */
@ActiveProfiles("test")
@DataJpaTest
@Import(CryptoConfiguration::class)
class ReviewQueryTest {
    @Autowired
    private lateinit var repository: ReviewActivityRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    private lateinit var artifacts: CommitArtifacts
    private val project = UUID.randomUUID()
    private val otherProject = UUID.randomUUID()
    private val everything = Instant.parse("2030-01-01T00:00:00Z")

    @BeforeEach
    fun setUp() {
        artifacts = CommitArtifacts(entityManager)
    }

    private fun pullRequestOf(vararg projects: UUID, sourceId: String): Artifact =
        artifacts.pullRequest(sourceId).also { pullRequest -> projects.forEach(pullRequest::addProjectId) }

    private fun review(
        pullRequest: Artifact,
        path: String,
        at: String,
        reviewer: String = "alice",
    ) = ReviewActivity(
        artifact = pullRequest,
        repository = "github:acme/backend",
        path = path,
        reviewerLogin = reviewer,
        state = ReviewState.APPROVED,
        reviewedAt = Instant.parse(at),
    )

    private fun store(vararg reviews: ReviewActivity) {
        repository.saveAll(reviews.toList())
        entityManager.flush()
        entityManager.clear()
    }

    @Test
    fun `reads every field the exported review carries`() {
        val pullRequest = pullRequestOf(project, sourceId = "github:acme/backend:PULL_REQUEST:1")
        store(review(pullRequest, "src/Core.kt", "2026-03-04T05:06:07Z", reviewer = "bob"))

        val read = repository.findReviews(project, everything).single()

        assertThat(read).isEqualTo(
            FileReview(
                repository = "github:acme/backend",
                path = "src/Core.kt",
                reviewerLogin = "bob",
                reviewedAt = Instant.parse("2026-03-04T05:06:07Z"),
            ),
        )
    }

    @Test
    fun `reads only the pull requests linked to the project`() {
        val mine = pullRequestOf(project, sourceId = "github:acme/backend:PULL_REQUEST:1")
        val theirs = pullRequestOf(otherProject, sourceId = "github:acme/backend:PULL_REQUEST:2")
        val nobody = pullRequestOf(sourceId = "github:acme/backend:PULL_REQUEST:3")
        store(
            review(mine, "mine.kt", "2026-01-01T00:00:00Z"),
            review(theirs, "theirs.kt", "2026-01-02T00:00:00Z"),
            review(nobody, "nobody.kt", "2026-01-03T00:00:00Z"),
        )

        assertThat(repository.findReviews(project, everything).map { it.path }).containsExactly("mine.kt")
    }

    @Test
    fun `a pull request linked to two projects is read for both`() {
        val shared = pullRequestOf(project, otherProject, sourceId = "github:acme/backend:PULL_REQUEST:1")
        store(review(shared, "shared.kt", "2026-01-01T00:00:00Z"))

        assertThat(repository.findReviews(project, everything)).hasSize(1)
        assertThat(repository.findReviews(otherProject, everything)).hasSize(1)
    }

    @Test
    fun `leaves out reviews submitted after the date but keeps one submitted exactly on it`() {
        val pullRequest = pullRequestOf(project, sourceId = "github:acme/backend:PULL_REQUEST:1")
        store(
            review(pullRequest, "before.kt", "2026-01-01T00:00:00Z"),
            review(pullRequest, "on.kt", "2026-02-01T00:00:00Z"),
            review(pullRequest, "after.kt", "2026-03-01T00:00:00Z"),
        )

        val read = repository.findReviews(project, Instant.parse("2026-02-01T00:00:00Z"))

        assertThat(read.map { it.path }).containsExactly("before.kt", "on.kt")
    }

    @Test
    fun `returns the reviews oldest first whatever order they were stored in`() {
        val pullRequest = pullRequestOf(project, sourceId = "github:acme/backend:PULL_REQUEST:1")
        store(
            review(pullRequest, "third.kt", "2026-03-01T00:00:00Z"),
            review(pullRequest, "first.kt", "2026-01-01T00:00:00Z"),
            review(pullRequest, "second.kt", "2026-02-01T00:00:00Z"),
        )

        assertThat(repository.findReviews(project, everything).map { it.path })
            .containsExactly("first.kt", "second.kt", "third.kt")
    }

    @Test
    fun `a project without reviews reads nothing`() {
        assertThat(repository.findReviews(project, everything)).isEmpty()
    }
}
