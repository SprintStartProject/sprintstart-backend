package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactFilterCriteria
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactSort
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.shared.crypto.CryptoConfiguration
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.UUID

/**
 * Runs the criteria queries behind the project artifact list and its facets against H2.
 *
 * Ordering, date bounds and case folding are decided by the SQL the criteria API renders, which a
 * mocked repository cannot see; these tests pin that SQL's behaviour instead of the builder calls.
 */
@ActiveProfiles("test")
@DataJpaTest
// A JPA slice loads no @Configuration of its own, but the entity graph reaches an
// AttributeConverter that needs the encryptor.
@Import(CryptoConfiguration::class)
class ArtifactFacetRepositoryQueryTest {
    @Autowired
    private lateinit var repository: ArtifactRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    private lateinit var run: IngestionRun

    private val projectId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        run = IngestionRun(
            id = UUID.randomUUID(),
            sourceSystem = SourceSystem.GITHUB,
            status = IngestionRunStatus.COMPLETED,
        )
        entityManager.persist(run)
    }

    // ========================== sort ==========================

    @Test
    fun `ADDED_DESC lists the newest import first and breaks ties by id`() {
        val old = store(id = id(1), ingestedAt = BASE.minusSeconds(60))
        val newerSecond = store(id = id(3), ingestedAt = BASE)
        val newerFirst = store(id = id(2), ingestedAt = BASE)
        flush()

        assertThat(listIds(sort = ArtifactSort.ADDED_DESC))
            .containsExactly(newerFirst.id, newerSecond.id, old.id)
    }

    @Test
    fun `CHANGED_DESC orders by the latest change and falls back to the import time`() {
        // Imported first but changed last: the change, not the import, decides its place.
        val changedLate = store(id = id(1), ingestedAt = BASE, lastChangedAt = BASE.plusSeconds(500))
        val neverChanged = store(id = id(4), ingestedAt = BASE.plusSeconds(300))
        val neverChangedTie = store(id = id(2), ingestedAt = BASE.plusSeconds(300))
        val changedEarly = store(id = id(3), ingestedAt = BASE, lastChangedAt = BASE.plusSeconds(100))
        flush()

        assertThat(listIds(sort = ArtifactSort.CHANGED_DESC))
            .containsExactly(changedLate.id, neverChangedTie.id, neverChanged.id, changedEarly.id)
    }

    @Test
    fun `TITLE_ASC ignores case, puts untitled artifacts last and breaks ties by id`() {
        val untitledSecond = store(id = id(6), title = null)
        val beta = store(id = id(1), title = "beta")
        val untitledFirst = store(id = id(2), title = null)
        val lowerAlpha = store(id = id(5), title = "alpha")
        val upperAlpha = store(id = id(3), title = "Alpha")
        flush()

        // "Alpha" and "alpha" fold to one key, so only the id decides between them.
        assertThat(listIds(sort = ArtifactSort.TITLE_ASC)).containsExactly(
            upperAlpha.id,
            lowerAlpha.id,
            beta.id,
            untitledFirst.id,
            untitledSecond.id,
        )
    }

    @Test
    fun `the id tie-break keeps rows sharing a sort key stable across pages`() {
        val first = store(id = id(1), title = null)
        val second = store(id = id(2), title = null)
        val third = store(id = id(3), title = null)
        flush()

        val pages = (0..2).flatMap { page ->
            list(sort = ArtifactSort.TITLE_ASC, page = page, size = 1).content.map { it.id }
        }

        assertThat(pages).containsExactly(first.id, second.id, third.id)
    }

    @Test
    fun `sorting leaves the total count untouched`() {
        repeat(3) { store() }
        store(project = UUID.randomUUID())
        flush()

        ArtifactSort.entries.forEach { sort ->
            assertThat(list(sort = sort).totalElements).isEqualTo(3)
        }
    }

    // ========================== helpers ==========================

    private fun list(
        criteria: ArtifactFilterCriteria = ArtifactFilterCriteria(),
        sort: ArtifactSort = ArtifactSort.ADDED_DESC,
        page: Int = 0,
        size: Int = 50,
    ) = repository.findProjectArtifactsWithCriteria(projectId, criteria, sort, PageRequest.of(page, size))

    private fun listIds(
        criteria: ArtifactFilterCriteria = ArtifactFilterCriteria(),
        sort: ArtifactSort = ArtifactSort.ADDED_DESC,
    ): List<UUID> = list(criteria, sort).content.map { it.id }

    /** Ids whose natural order is unambiguous on every database: `...0001` sorts before `...0002`. */
    private fun id(n: Long): UUID = UUID(0L, n)

    @Suppress("LongParameterList")
    private fun store(
        id: UUID = UUID.randomUUID(),
        title: String? = "file-$id",
        ingestedAt: Instant = BASE,
        lastChangedAt: Instant? = null,
        language: String? = null,
        sourceSystem: SourceSystem = SourceSystem.GITHUB,
        type: ArtifactType = ArtifactType.FILE,
        project: UUID = projectId,
    ): Artifact {
        val artifact = Artifact(
            id = id,
            sourceSystem = sourceSystem,
            sourceId = "github:acme/repo:$type:$id",
            sourceUrl = "https://github.com/acme/repo",
            artifactType = type,
            title = title,
            content = "content",
            mime = null,
            language = language,
            createdAtSource = null,
            updatedAtSource = null,
            ingestedAt = ingestedAt,
            lastChangedAt = lastChangedAt,
            ingestionRun = run,
            hash = null,
        )
        artifact.addProjectId(project)
        entityManager.persist(artifact)
        return artifact
    }

    private fun flush() {
        entityManager.flush()
        entityManager.clear()
    }

    private companion object {
        val BASE: Instant = Instant.parse("2026-03-10T12:00:00Z")
    }
}
