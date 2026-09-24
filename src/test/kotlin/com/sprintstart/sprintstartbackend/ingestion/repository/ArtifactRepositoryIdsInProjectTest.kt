package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.shared.crypto.CryptoConfiguration
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

/**
 * The ai-status endpoint trusts this query to hide foreign artifacts, so it meets a real database:
 * a mocked repository could not show that the project join actually filters.
 */
@ActiveProfiles("test")
@DataJpaTest
@Import(CryptoConfiguration::class)
class ArtifactRepositoryIdsInProjectTest {
    @Autowired
    private lateinit var repository: ArtifactRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun `returns only the requested ids that are linked to the project`() {
        val projectId = UUID.randomUUID()
        val otherProject = UUID.randomUUID()
        val mine = store("mine").apply { addProjectId(projectId) }
        val shared = store("shared").apply { addProjectIds(setOf(projectId, otherProject)) }
        val foreign = store("foreign").apply { addProjectId(otherProject) }
        store("unrequested").addProjectId(projectId)
        entityManager.flush()

        val found = repository.findIdsInProject(
            projectId,
            listOf(mine.id, shared.id, foreign.id, UUID.randomUUID()),
        )

        assertThat(found).containsExactlyInAnyOrder(mine.id, shared.id)
    }

    private fun store(name: String): Artifact {
        val run = IngestionRun(
            id = UUID.randomUUID(),
            sourceSystem = SourceSystem.UPLOAD,
            status = IngestionRunStatus.COMPLETED,
        )
        entityManager.persist(run)
        val artifact = Artifact(
            sourceSystem = SourceSystem.UPLOAD,
            sourceId = "upload:$name",
            sourceUrl = null,
            artifactType = ArtifactType.FILE,
            title = name,
            content = "content",
            mime = null,
            language = null,
            createdAtSource = null,
            updatedAtSource = null,
            ingestionRun = run,
            hash = null,
        )
        entityManager.persist(artifact)
        return artifact
    }
}
