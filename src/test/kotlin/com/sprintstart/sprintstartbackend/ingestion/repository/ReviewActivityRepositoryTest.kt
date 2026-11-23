package com.sprintstart.sprintstartbackend.ingestion.repository

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

/**
 * A review must disappear with its pull request and a re-record must replace only that pull
 * request's rows. The first depends on the foreign key the schema gets, the second on a bulk query,
 * so both have to meet a real database.
 */
@ActiveProfiles("test")
@DataJpaTest
// A JPA slice loads no @Configuration of its own, but the entity graph reaches an
// AttributeConverter that needs the encryptor.
@Import(CryptoConfiguration::class)
class ReviewActivityRepositoryTest {
    @Autowired
    private lateinit var repository: ReviewActivityRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    private lateinit var artifacts: CommitArtifacts

    @BeforeEach
    fun setUp() {
        artifacts = CommitArtifacts(entityManager)
    }

    private fun review(artifact: Artifact, path: String) = ReviewActivity(
        artifact = artifact,
        repository = "github:acme/backend",
        path = path,
        reviewerLogin = "alice",
        state = ReviewState.APPROVED,
        reviewedAt = Instant.parse("2026-03-04T05:06:07Z"),
    )

    private fun pathsOf(artifact: Artifact): List<String> {
        entityManager.flush()
        entityManager.clear()
        return repository.findAll().filter { it.artifact.id == artifact.id }.map { it.path }
    }

    @Test
    fun `deleting a pull request's reviews removes only that pull request's rows`() {
        val first = artifacts.pullRequest("github:acme/backend:PULL_REQUEST:1")
        val second = artifacts.pullRequest("github:acme/backend:PULL_REQUEST:2")
        repository.saveAll(listOf(review(first, "a.kt"), review(first, "b.kt"), review(second, "c.kt")))
        entityManager.flush()

        val removed = repository.deleteAllByArtifactId(first.id)

        assertThat(removed).isEqualTo(2)
        assertThat(pathsOf(first)).isEmpty()
        assertThat(pathsOf(second)).containsExactly("c.kt")
    }

    @Test
    fun `deleting a pull request artifact removes its reviews`() {
        val pullRequest = artifacts.pullRequest("github:acme/backend:PULL_REQUEST:1")
        repository.saveAll(listOf(review(pullRequest, "a.kt"), review(pullRequest, "b.kt")))
        entityManager.flush()
        entityManager.clear()

        entityManager.remove(entityManager.find(Artifact::class.java, pullRequest.id))
        entityManager.flush()
        entityManager.clear()

        assertThat(repository.count()).isZero()
    }

    @Test
    fun `stores every field of a review`() {
        val pullRequest = artifacts.pullRequest("github:acme/backend:PULL_REQUEST:1")
        repository.save(
            ReviewActivity(
                artifact = pullRequest,
                repository = "github:acme/backend",
                path = "src/Main.kt",
                reviewerLogin = "carol",
                state = ReviewState.CHANGES_REQUESTED,
                reviewedAt = Instant.parse("2026-03-04T05:06:07Z"),
            ),
        )
        entityManager.flush()
        entityManager.clear()

        val stored = repository.findAll().single()

        assertThat(stored.repository).isEqualTo("github:acme/backend")
        assertThat(stored.path).isEqualTo("src/Main.kt")
        assertThat(stored.reviewerLogin).isEqualTo("carol")
        assertThat(stored.state).isEqualTo(ReviewState.CHANGES_REQUESTED)
        assertThat(stored.reviewedAt).isEqualTo(Instant.parse("2026-03-04T05:06:07Z"))
    }
}
