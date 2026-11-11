package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.ReviewActivityCommand
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.SubmittedReview
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ReviewState
import com.sprintstart.sprintstartbackend.ingestion.model.exceptions.EvidenceArtifactNotFoundException
import com.sprintstart.sprintstartbackend.ingestion.repository.CommitArtifacts
import com.sprintstart.sprintstartbackend.ingestion.repository.ReviewActivityRepository
import com.sprintstart.sprintstartbackend.shared.crypto.CryptoConfiguration
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Instant

/**
 * Recording is a replace that keeps one row per reviewer and file, so it runs against a database
 * instead of a mocked repository.
 */
@ActiveProfiles("test")
@DataJpaTest
// A JPA slice loads no @Configuration of its own, but the entity graph reaches an
// AttributeConverter that needs the encryptor.
@Import(CryptoConfiguration::class, ReviewActivityService::class)
class ReviewActivityServiceTest {
    @Autowired
    private lateinit var service: ReviewActivityService

    @Autowired
    private lateinit var repository: ReviewActivityRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    private lateinit var artifacts: CommitArtifacts

    private val sourceId = "github:acme/backend:PULL_REQUEST:7"
    private val early = Instant.parse("2024-01-03T00:00:00Z")
    private val late = Instant.parse("2024-01-05T00:00:00Z")

    @BeforeEach
    fun setUp() {
        artifacts = CommitArtifacts(entityManager)
    }

    private fun review(reviewer: String, state: ReviewState = ReviewState.APPROVED, at: Instant = early) =
        SubmittedReview(reviewer, state, at)

    private fun command(reviews: List<SubmittedReview>, vararg paths: String, id: String = sourceId) =
        ReviewActivityCommand(
            sourceId = id,
            repository = "github:acme/backend",
            reviews = reviews,
            paths = paths.toList(),
        )

    private fun stored(): List<Pair<String, String>> {
        entityManager.flush()
        entityManager.clear()
        return repository.findAll().map { it.reviewerLogin to it.path }
    }

    @Test
    fun `stores a row for every reviewer and file of the pull request`() {
        artifacts.pullRequest(sourceId)

        service.record(command(listOf(review("alice"), review("carol")), "a.kt", "b.kt"))

        assertThat(stored()).containsExactlyInAnyOrder(
            "alice" to "a.kt",
            "alice" to "b.kt",
            "carol" to "a.kt",
            "carol" to "b.kt",
        )
    }

    @Test
    fun `stores the details of a review on its rows`() {
        artifacts.pullRequest(sourceId)

        service.record(command(listOf(review("alice", ReviewState.CHANGES_REQUESTED, late)), "a.kt"))
        entityManager.flush()
        entityManager.clear()

        val row = repository.findAll().single()
        assertThat(row.repository).isEqualTo("github:acme/backend")
        assertThat(row.state).isEqualTo(ReviewState.CHANGES_REQUESTED)
        assertThat(row.reviewedAt).isEqualTo(late)
        assertThat(row.artifact.sourceId).isEqualTo(sourceId)
    }

    @Test
    fun `keeps only a reviewer's latest review`() {
        artifacts.pullRequest(sourceId)

        service.record(
            command(
                listOf(
                    review("alice", ReviewState.CHANGES_REQUESTED, early),
                    review("alice", ReviewState.APPROVED, late),
                ),
                "a.kt",
            ),
        )
        entityManager.flush()
        entityManager.clear()

        val row = repository.findAll().single()
        assertThat(row.state).isEqualTo(ReviewState.APPROVED)
        assertThat(row.reviewedAt).isEqualTo(late)
    }

    @Test
    fun `keeps the latest review whatever order the reviews arrive in`() {
        artifacts.pullRequest(sourceId)

        service.record(
            command(
                listOf(
                    review("alice", ReviewState.APPROVED, late),
                    review("alice", ReviewState.CHANGES_REQUESTED, early),
                ),
                "a.kt",
            ),
        )
        entityManager.flush()
        entityManager.clear()

        assertThat(repository.findAll().single().state).isEqualTo(ReviewState.APPROVED)
    }

    @Test
    fun `recording the same pull request again replaces its reviews`() {
        artifacts.pullRequest(sourceId)
        service.record(command(listOf(review("alice")), "a.kt", "b.kt"))
        entityManager.flush()

        service.record(command(listOf(review("carol")), "a.kt"))

        assertThat(stored()).containsExactly("carol" to "a.kt")
    }

    @Test
    fun `leaves other pull requests' reviews alone`() {
        artifacts.pullRequest(sourceId)
        artifacts.pullRequest("github:acme/backend:PULL_REQUEST:8")
        service.record(command(listOf(review("alice")), "other.kt", id = "github:acme/backend:PULL_REQUEST:8"))
        entityManager.flush()

        service.record(command(listOf(review("carol")), "mine.kt"))

        assertThat(stored()).containsExactlyInAnyOrder("alice" to "other.kt", "carol" to "mine.kt")
    }

    @Test
    fun `stores nothing when the pull request has no reviews`() {
        artifacts.pullRequest(sourceId)

        service.record(command(emptyList(), "a.kt"))

        assertThat(stored()).isEmpty()
    }

    @Test
    fun `stores nothing when the files are not all known`() {
        artifacts.pullRequest(sourceId)

        service.record(command(listOf(review("alice"))))

        assertThat(stored()).isEmpty()
    }

    @Test
    fun `clears the reviews a pull request had when its files later become unknown`() {
        artifacts.pullRequest(sourceId)
        service.record(command(listOf(review("alice")), "a.kt"))
        entityManager.flush()

        service.record(command(listOf(review("alice"))))

        assertThat(stored()).isEmpty()
    }

    @Test
    fun `fails when the pull request artifact is not stored`() {
        val error = assertThrows<EvidenceArtifactNotFoundException> {
            service.record(command(listOf(review("alice")), "a.kt"))
        }

        assertThat(error.message).contains(sourceId)
        assertThat(stored()).isEmpty()
    }
}
