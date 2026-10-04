package com.sprintstart.sprintstartbackend.insights.repository

import com.sprintstart.sprintstartbackend.insights.model.entity.ProjectAnalysisRun
import com.sprintstart.sprintstartbackend.shared.crypto.CryptoConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.UUID

/**
 * Runs the native pruning query and the project delete against H2.
 *
 * `deleteAllButNewest` is hand-written SQL (`NOT IN (… ORDER BY … LIMIT :keep)`), which a mocked
 * repository cannot see; these tests pin what it actually deletes.
 */
@ActiveProfiles("test")
@DataJpaTest
// A JPA slice scans every entity, and one of them reaches an AttributeConverter that needs the
// encryptor.
@Import(CryptoConfiguration::class)
class ProjectAnalysisRunRepositoryTest {
    @Autowired
    private lateinit var repository: ProjectAnalysisRunRepository

    private val projectId: UUID = UUID.randomUUID()
    private val otherProjectId: UUID = UUID.randomUUID()
    private val start: Instant = Instant.parse("2026-09-01T10:00:00Z")

    private fun store(project: UUID, count: Int): List<ProjectAnalysisRun> =
        repository.saveAllAndFlush(
            (0 until count).map { index ->
                ProjectAnalysisRun(
                    projectId = project,
                    score = index,
                    failedChecks = 0,
                    payload = """{"findings":[],"tasks":[]}""",
                    createdAt = start.plusSeconds(index.toLong()),
                )
            },
        )

    @Test
    fun `pruning keeps exactly the newest runs of the project`() {
        val runs = store(projectId, KEEP + 1)

        repository.deleteAllButNewest(projectId, KEEP)

        val left = repository.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(0, KEEP + 10))
        assertThat(left).hasSize(KEEP)
        // The oldest run is the one that went; the newest is still first.
        assertThat(left.map { it.id }).doesNotContain(runs.first().id)
        assertThat(left.first().id).isEqualTo(runs.last().id)
    }

    @Test
    fun `pruning one project leaves another project's runs alone`() {
        store(projectId, KEEP + 1)
        store(otherProjectId, 3)

        repository.deleteAllButNewest(projectId, KEEP)

        assertThat(repository.findByProjectIdOrderByCreatedAtDesc(otherProjectId, PageRequest.of(0, 10)))
            .hasSize(3)
    }

    @Test
    fun `pruning a project with fewer runs than kept deletes nothing`() {
        store(projectId, 5)

        repository.deleteAllButNewest(projectId, KEEP)

        assertThat(repository.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(0, 10))).hasSize(5)
    }

    @Test
    fun `deleting a project's runs deletes only that project's runs`() {
        store(projectId, 4)
        store(otherProjectId, 2)

        val deleted = repository.deleteByProjectId(projectId)

        assertThat(deleted).isEqualTo(4)
        assertThat(repository.findByProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(0, 10))).isEmpty()
        assertThat(repository.findByProjectIdOrderByCreatedAtDesc(otherProjectId, PageRequest.of(0, 10)))
            .hasSize(2)
    }

    private companion object {
        /** The kept history the service uses; 100 runs plus one is the case the review asked for. */
        const val KEEP = 100
    }
}
