package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.PageEditReading
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.PageEdit
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

/** The project, date and title joins of the page history read are database features, so they meet a real one. */
@ActiveProfiles("test")
@DataJpaTest
@Import(CryptoConfiguration::class)
class PageEditQueryTest {
    @Autowired
    private lateinit var repository: PageEditRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    private lateinit var artifacts: CommitArtifacts
    private val project = UUID.randomUUID()
    private val everything = Instant.parse("2030-01-01T00:00:00Z")

    @BeforeEach
    fun setUp() {
        artifacts = CommitArtifacts(entityManager)
    }

    private fun page(title: String, vararg projects: UUID): Artifact =
        artifacts.commit("confluence:c:page:$title").also { page ->
            page.title = title
            projects.forEach(page::addProjectId)
        }

    private fun edit(page: Artifact, version: Int, author: String, at: String) =
        PageEdit(null, page, "confluence:ENG", version, author, Instant.parse(at))

    private fun store(vararg edits: PageEdit) {
        repository.saveAll(edits.toList())
        entityManager.flush()
        entityManager.clear()
    }

    @Test
    fun `reads the versions of the project's pages oldest first under their titles`() {
        val setup = page("Setup", project)
        val other = page("Other", UUID.randomUUID())
        store(
            edit(setup, 2, "bob", "2026-02-01T00:00:00Z"),
            edit(setup, 1, "ada", "2026-01-01T00:00:00Z"),
            edit(other, 1, "eve", "2026-01-15T00:00:00Z"),
        )

        assertThat(repository.findEdits(project, everything)).containsExactly(
            PageEditReading("confluence:ENG", "Setup", "ada", Instant.parse("2026-01-01T00:00:00Z")),
            PageEditReading("confluence:ENG", "Setup", "bob", Instant.parse("2026-02-01T00:00:00Z")),
        )
    }

    @Test
    fun `leaves out versions saved after the moment`() {
        val setup = page("Setup", project)
        store(edit(setup, 1, "ada", "2026-01-01T00:00:00Z"), edit(setup, 2, "bob", "2026-03-01T00:00:00Z"))

        val read = repository.findEdits(project, Instant.parse("2026-02-01T00:00:00Z"))

        assertThat(read.map { it.authorAccountId }).containsExactly("ada")
    }

    @Test
    fun `replacing the versions of one page leaves the others`() {
        val first = page("First", project)
        val second = page("Second", project)
        store(edit(first, 1, "ada", "2026-01-01T00:00:00Z"), edit(second, 1, "bob", "2026-01-02T00:00:00Z"))

        val removed = repository.deleteAllByArtifactId(first.id)

        assertThat(removed).isEqualTo(1)
        assertThat(repository.findEdits(project, everything).map { it.path }).containsExactly("Second")
    }

    @Test
    fun `deleting a page removes its versions`() {
        val setup = page("Setup", project)
        store(edit(setup, 1, "ada", "2026-01-01T00:00:00Z"))

        entityManager.remove(entityManager.find(Artifact::class.java, setup.id))
        entityManager.flush()

        assertThat(repository.count()).isZero()
    }
}
