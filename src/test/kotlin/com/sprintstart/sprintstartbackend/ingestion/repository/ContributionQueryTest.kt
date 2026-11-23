package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileContribution
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
import java.util.UUID

/** The project and date filters of the contribution read are database features, so they meet a real one. */
@ActiveProfiles("test")
@DataJpaTest
@Import(CryptoConfiguration::class)
class ContributionQueryTest {
    @Autowired
    private lateinit var repository: FileChangeRepository

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

    private fun commitOf(vararg projects: UUID, sourceId: String): Artifact =
        artifacts.commit(sourceId).also { commit -> projects.forEach(commit::addProjectId) }

    private fun change(
        commit: Artifact,
        path: String,
        at: String,
        renamedFrom: String? = null,
        email: String = "ada@example.com",
    ) = FileChange(
        artifact = commit,
        repository = "github:acme/backend",
        path = path,
        renamedFrom = renamedFrom,
        author = ChangeAuthor(name = "Ada", email = email),
        changedAt = Instant.parse(at),
        lines = LineCounts(added = 1, removed = 0),
    )

    private fun store(vararg changes: FileChange) {
        repository.saveAll(changes.toList())
        entityManager.flush()
        entityManager.clear()
    }

    @Test
    fun `reads every field the exported change carries`() {
        val commit = commitOf(project, sourceId = "github:acme/backend:COMMIT:one")
        store(change(commit, "src/New.kt", "2026-03-04T05:06:07Z", renamedFrom = "src/Old.kt", email = "ada@math.org"))

        val read = repository.findContributions(project, everything).single()

        assertThat(read).isEqualTo(
            FileContribution(
                repository = "github:acme/backend",
                path = "src/New.kt",
                renamedFrom = "src/Old.kt",
                authorEmail = "ada@math.org",
                changedAt = Instant.parse("2026-03-04T05:06:07Z"),
            ),
        )
    }

    @Test
    fun `reads only the commits linked to the project`() {
        val mine = commitOf(project, sourceId = "github:acme/backend:COMMIT:mine")
        val theirs = commitOf(otherProject, sourceId = "github:acme/backend:COMMIT:theirs")
        val nobody = commitOf(sourceId = "github:acme/backend:COMMIT:nobody")
        store(
            change(mine, "mine.kt", "2026-01-01T00:00:00Z"),
            change(theirs, "theirs.kt", "2026-01-02T00:00:00Z"),
            change(nobody, "nobody.kt", "2026-01-03T00:00:00Z"),
        )

        assertThat(repository.findContributions(project, everything).map { it.path }).containsExactly("mine.kt")
    }

    @Test
    fun `a commit linked to two projects is read for both`() {
        val shared = commitOf(project, otherProject, sourceId = "github:acme/backend:COMMIT:shared")
        store(change(shared, "shared.kt", "2026-01-01T00:00:00Z"))

        assertThat(repository.findContributions(project, everything)).hasSize(1)
        assertThat(repository.findContributions(otherProject, everything)).hasSize(1)
    }

    @Test
    fun `leaves out changes made after the date but keeps one made exactly on it`() {
        val commit = commitOf(project, sourceId = "github:acme/backend:COMMIT:one")
        store(
            change(commit, "before.kt", "2026-01-01T00:00:00Z"),
            change(commit, "on.kt", "2026-02-01T00:00:00Z"),
            change(commit, "after.kt", "2026-03-01T00:00:00Z"),
        )

        val read = repository.findContributions(project, Instant.parse("2026-02-01T00:00:00Z"))

        assertThat(read.map { it.path }).containsExactly("before.kt", "on.kt")
    }

    @Test
    fun `returns the changes oldest first whatever order they were stored in`() {
        val commit = commitOf(project, sourceId = "github:acme/backend:COMMIT:one")
        store(
            change(commit, "third.kt", "2026-03-01T00:00:00Z"),
            change(commit, "first.kt", "2026-01-01T00:00:00Z"),
            change(commit, "second.kt", "2026-02-01T00:00:00Z"),
        )

        assertThat(repository.findContributions(project, everything).map { it.path })
            .containsExactly("first.kt", "second.kt", "third.kt")
    }

    @Test
    fun `a project without history reads nothing`() {
        assertThat(repository.findContributions(project, everything)).isEmpty()
    }
}
