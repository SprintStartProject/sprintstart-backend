package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.CommitFileChangesCommand
import com.sprintstart.sprintstartbackend.ingestion.model.exceptions.EvidenceArtifactNotFoundException
import com.sprintstart.sprintstartbackend.ingestion.repository.CommitArtifacts
import com.sprintstart.sprintstartbackend.ingestion.repository.FileChangeRepository
import com.sprintstart.sprintstartbackend.shared.crypto.CryptoConfiguration
import com.sprintstart.sprintstartbackend.shared.git.CommitFile
import com.sprintstart.sprintstartbackend.shared.git.CommitFiles
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
 * Recording is a replace, and a replace is only right if the old rows really go and the new ones
 * really arrive, so this runs against a database instead of a mocked repository.
 */
@ActiveProfiles("test")
@DataJpaTest
// A JPA slice loads no @Configuration of its own, but the entity graph reaches an
// AttributeConverter that needs the encryptor.
@Import(CryptoConfiguration::class, CommitFileChangeService::class)
class CommitFileChangeServiceTest {
    @Autowired
    private lateinit var service: CommitFileChangeService

    @Autowired
    private lateinit var repository: FileChangeRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    private lateinit var artifacts: CommitArtifacts

    private val sourceId = "github:acme/backend:COMMIT:abc123"
    private val committedAt = Instant.parse("2026-03-04T05:06:07Z")

    @BeforeEach
    fun setUp() {
        artifacts = CommitArtifacts(entityManager)
    }

    private fun command(files: CommitFiles, id: String = sourceId) = CommitFileChangesCommand(
        sourceId = id,
        repository = "github:acme/backend",
        authorName = "Ada Lovelace",
        authorEmail = "ada@example.com",
        changedAt = committedAt,
        files = files,
    )

    private fun listed(vararg paths: String) =
        CommitFiles.Listed(paths.map { CommitFile(it, linesAdded = 2, linesRemoved = 1, renamedFrom = null) })

    private fun storedPaths(): List<String> {
        entityManager.flush()
        entityManager.clear()
        return repository.findAll().map { it.path }
    }

    @Test
    fun `stores a row per file with the commit's author and time`() {
        artifacts.commit(sourceId)

        service.record(command(CommitFiles.Listed(listOf(CommitFile("src/New.kt", 7, 3, renamedFrom = "src/Old.kt")))))
        entityManager.flush()
        entityManager.clear()

        val stored = repository.findAll().single()
        assertThat(stored.path).isEqualTo("src/New.kt")
        assertThat(stored.renamedFrom).isEqualTo("src/Old.kt")
        assertThat(stored.lines.added).isEqualTo(7)
        assertThat(stored.lines.removed).isEqualTo(3)
        assertThat(stored.repository).isEqualTo("github:acme/backend")
        assertThat(stored.author.name).isEqualTo("Ada Lovelace")
        assertThat(stored.author.email).isEqualTo("ada@example.com")
        assertThat(stored.changedAt).isEqualTo(committedAt)
        assertThat(stored.artifact.sourceId).isEqualTo(sourceId)
    }

    @Test
    fun `recording the same commit again replaces its files instead of adding to them`() {
        artifacts.commit(sourceId)
        service.record(command(listed("a.kt", "b.kt")))
        entityManager.flush()

        service.record(command(listed("a.kt", "c.kt")))

        assertThat(storedPaths()).containsExactlyInAnyOrder("a.kt", "c.kt")
    }

    @Test
    fun `leaves other commits' files alone`() {
        artifacts.commit(sourceId)
        artifacts.commit("github:acme/backend:COMMIT:other")
        service.record(command(listed("other.kt"), id = "github:acme/backend:COMMIT:other"))
        entityManager.flush()

        service.record(command(listed("mine.kt")))

        assertThat(storedPaths()).containsExactlyInAnyOrder("other.kt", "mine.kt")
    }

    @Test
    fun `stores nothing for a commit that changed too many files`() {
        artifacts.commit(sourceId)

        service.record(command(CommitFiles.TooMany(500)))

        assertThat(storedPaths()).isEmpty()
    }

    @Test
    fun `clears the files a commit had when it is later recorded with too many`() {
        artifacts.commit(sourceId)
        service.record(command(listed("a.kt")))
        entityManager.flush()

        service.record(command(CommitFiles.TooMany(500)))

        assertThat(storedPaths()).isEmpty()
    }

    @Test
    fun `stores nothing for a commit without files`() {
        artifacts.commit(sourceId)

        service.record(command(listed()))

        assertThat(storedPaths()).isEmpty()
    }

    @Test
    fun `fails when the commit artifact is not stored`() {
        val error = assertThrows<EvidenceArtifactNotFoundException> { service.record(command(listed("a.kt"))) }

        assertThat(error.message).contains(sourceId)
        assertThat(storedPaths()).isEmpty()
    }
}
