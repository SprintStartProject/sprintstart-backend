package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ChangeAuthor
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FileChange
import com.sprintstart.sprintstartbackend.ingestion.model.entity.LineCounts
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
 * A changed file must disappear with its commit and a re-record must replace only that commit's
 * rows. The first depends on the foreign key the schema gets, the second on a bulk query, so both
 * have to meet a real database.
 */
@ActiveProfiles("test")
@DataJpaTest
// A JPA slice loads no @Configuration of its own, but the entity graph reaches an
// AttributeConverter that needs the encryptor.
@Import(CryptoConfiguration::class)
class FileChangeRepositoryTest {
    @Autowired
    private lateinit var repository: FileChangeRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    private lateinit var artifacts: CommitArtifacts

    @BeforeEach
    fun setUp() {
        artifacts = CommitArtifacts(entityManager)
    }

    private fun change(artifact: Artifact, path: String) = FileChange(
        artifact = artifact,
        repository = "github:acme/backend",
        path = path,
        renamedFrom = null,
        author = ChangeAuthor(name = "Ada", email = "ada@example.com"),
        changedAt = Instant.parse("2026-03-04T05:06:07Z"),
        lines = LineCounts(added = 1, removed = 0),
    )

    private fun pathsOf(artifact: Artifact): List<String> {
        entityManager.flush()
        entityManager.clear()
        return repository.findAll().filter { it.artifact.id == artifact.id }.map { it.path }
    }

    @Test
    fun `deleting a commit's changes removes only that commit's rows`() {
        val first = artifacts.commit("github:acme/backend:COMMIT:one")
        val second = artifacts.commit("github:acme/backend:COMMIT:two")
        repository.saveAll(listOf(change(first, "a.kt"), change(first, "b.kt"), change(second, "c.kt")))
        entityManager.flush()

        val removed = repository.deleteAllByArtifactId(first.id)

        assertThat(removed).isEqualTo(2)
        assertThat(pathsOf(first)).isEmpty()
        assertThat(pathsOf(second)).containsExactly("c.kt")
    }

    @Test
    fun `deleting a commit artifact removes its changed files`() {
        val commit = artifacts.commit("github:acme/backend:COMMIT:one")
        repository.saveAll(listOf(change(commit, "a.kt"), change(commit, "b.kt")))
        entityManager.flush()
        entityManager.clear()

        entityManager.remove(entityManager.find(Artifact::class.java, commit.id))
        entityManager.flush()
        entityManager.clear()

        assertThat(repository.count()).isZero()
    }

    @Test
    fun `stores every field of a change`() {
        val commit = artifacts.commit("github:acme/backend:COMMIT:one")
        repository.save(
            FileChange(
                artifact = commit,
                repository = "github:acme/backend",
                path = "src/New.kt",
                renamedFrom = "src/Old.kt",
                author = ChangeAuthor(name = "Ada Lovelace", email = "ada@math.org"),
                changedAt = Instant.parse("2026-03-04T05:06:07Z"),
                lines = LineCounts(added = 7, removed = 3),
            ),
        )
        entityManager.flush()
        entityManager.clear()

        val stored = repository.findAll().single()

        assertThat(stored.repository).isEqualTo("github:acme/backend")
        assertThat(stored.path).isEqualTo("src/New.kt")
        assertThat(stored.renamedFrom).isEqualTo("src/Old.kt")
        assertThat(stored.author.name).isEqualTo("Ada Lovelace")
        assertThat(stored.author.email).isEqualTo("ada@math.org")
        assertThat(stored.changedAt).isEqualTo(Instant.parse("2026-03-04T05:06:07Z"))
        assertThat(stored.lines.added).isEqualTo(7)
        assertThat(stored.lines.removed).isEqualTo(3)
    }
}
