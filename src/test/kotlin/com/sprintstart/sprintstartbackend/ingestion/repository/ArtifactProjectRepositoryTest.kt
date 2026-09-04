package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
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
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

/**
 * These queries resolve artifacts by a *prefix* of a free-text column, so they are only as correct
 * as their pattern matching. A unit test against a mocked repository cannot see that; the query has
 * to meet a real database.
 */
@ActiveProfiles("test")
@DataJpaTest
// A JPA slice loads no @Configuration of its own, but the entity graph reaches an
// AttributeConverter that needs the encryptor.
@Import(CryptoConfiguration::class)
class ArtifactProjectRepositoryTest {
    @Autowired
    private lateinit var repository: ArtifactProjectRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    private lateinit var run: IngestionRun

    @BeforeEach
    fun setUp() {
        run = IngestionRun(
            id = UUID.randomUUID(),
            sourceSystem = SourceSystem.GITHUB,
            status = IngestionRunStatus.COMPLETED,
        )
        entityManager.persist(run)
    }

    @Test
    fun `an underscore in a repository name matches that repository only`() {
        val wanted = storeGithubArtifact("acme/data_service", "file.md")
        val neighbour = storeGithubArtifact("acme/data-service", "file.md")
        entityManager.flush()

        val found = repository.findAllByComponent("acme/data_service").map { it.id }

        // `_` is a single-character wildcard in SQL LIKE. Unescaped, connecting `data_service`
        // reaches into `data-service` as well and re-scopes a repository the caller never named --
        // its content becomes retrievable from a project it does not belong to.
        assertThat(found).containsExactly(wanted.id)
        assertThat(found).doesNotContain(neighbour.id)
    }

    @Test
    fun `a percent sign in a repository name is not a wildcard either`() {
        storeGithubArtifact("acme/real-repo", "file.md")
        entityManager.flush()

        assertThat(repository.findAllByComponent("acme/%")).isEmpty()
    }

    @Test
    fun `every artifact of the named repository is returned`() {
        val issue = storeGithubArtifact("acme/repo", "1", ArtifactType.ISSUE)
        val file = storeGithubArtifact("acme/repo", "README.md", ArtifactType.FILE)
        storeGithubArtifact("acme/other", "README.md", ArtifactType.FILE)
        entityManager.flush()

        assertThat(repository.findAllByComponent("acme/repo").map { it.id })
            .containsExactlyInAnyOrder(issue.id, file.id)
    }

    @Test
    fun `an underscore in a Jira instance url matches that instance only`() {
        val wanted = storeJiraArtifact("https://a_b.atlassian.net", "PROJ-1")
        val neighbour = storeJiraArtifact("https://axb.atlassian.net", "PROJ-1")
        entityManager.flush()

        val found = repository
            .findAllJiraArtifactsByInstanceUrl("https://a_b.atlassian.net")
            .map { it.id }

        assertThat(found).containsExactly(wanted.id)
        assertThat(found).doesNotContain(neighbour.id)
    }

    @Test
    fun `deleting a project drops its links and leaves the others alone`() {
        val deleted = UUID.randomUUID()
        val kept = UUID.randomUUID()
        val shared = storeGithubArtifact("acme/repo", "shared.md")
        shared.addProjectIds(setOf(deleted, kept))
        val onlyDeleted = storeGithubArtifact("acme/repo", "gone.md")
        onlyDeleted.addProjectId(deleted)
        entityManager.flush()

        assertThat(repository.deleteProjectLinks(deleted)).isEqualTo(2)

        entityManager.clear()
        assertThat(entityManager.find(Artifact::class.java, shared.id).projectIds)
            .containsExactly(kept)
        assertThat(entityManager.find(Artifact::class.java, onlyDeleted.id).projectIds).isEmpty()
    }

    private fun storeGithubArtifact(
        component: String,
        unique: String,
        type: ArtifactType = ArtifactType.FILE,
    ): Artifact = store(
        sourceSystem = SourceSystem.GITHUB,
        sourceId = "github:$component:$type:$unique",
        sourceUrl = "https://github.com/$component",
        type = type,
    )

    private fun storeJiraArtifact(instanceUrl: String, key: String): Artifact = store(
        sourceSystem = SourceSystem.JIRA,
        sourceId = "jira:$instanceUrl:$key",
        sourceUrl = "$instanceUrl/browse/$key",
        type = ArtifactType.ISSUE,
    )

    private fun store(
        sourceSystem: SourceSystem,
        sourceId: String,
        sourceUrl: String,
        type: ArtifactType,
    ): Artifact {
        val artifact = Artifact(
            sourceSystem = sourceSystem,
            sourceId = sourceId,
            sourceUrl = sourceUrl,
            artifactType = type,
            title = sourceId,
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
