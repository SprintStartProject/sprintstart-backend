package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.IssueActivityReading
import com.sprintstart.sprintstartbackend.ingestion.model.dto.CommitMessage
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IssueActivity
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

/** The project, date and type filters of the ticket evidence are database features, so they meet a real one. */
@ActiveProfiles("test")
@DataJpaTest
@Import(CryptoConfiguration::class)
class TicketEvidenceQueryTest {
    @Autowired
    private lateinit var activities: IssueActivityRepository

    @Autowired
    private lateinit var messages: CommitMessageRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    private lateinit var artifacts: CommitArtifacts
    private val project = UUID.randomUUID()
    private val everything = Instant.parse("2030-01-01T00:00:00Z")

    @BeforeEach
    fun setUp() {
        artifacts = CommitArtifacts(entityManager)
    }

    private fun linked(artifact: Artifact, vararg projects: UUID): Artifact =
        artifact.also { projects.forEach(it::addProjectId) }

    private fun activity(issue: Artifact, name: String, at: String) =
        IssueActivity(null, issue, "APP-1", name.lowercase(), name, Instant.parse(at))

    private fun store(vararg rows: IssueActivity) {
        activities.saveAll(rows.toList())
        entityManager.flush()
        entityManager.clear()
    }

    @Test
    fun `reads the activity on the project's tickets oldest first, up to the moment`() {
        val issue = linked(artifacts.pullRequest("jira:1"), project)
        val other = linked(artifacts.pullRequest("jira:2"), UUID.randomUUID())
        store(
            activity(issue, "Ben", "2026-02-01T00:00:00Z"),
            activity(issue, "Ada", "2026-01-01T00:00:00Z"),
            activity(issue, "Cy", "2026-04-01T00:00:00Z"),
            activity(other, "Eve", "2026-01-15T00:00:00Z"),
        )

        val read = activities.findActivities(project, Instant.parse("2026-03-01T00:00:00Z"))

        assertThat(read).containsExactly(
            IssueActivityReading("APP-1", "ada", "Ada", Instant.parse("2026-01-01T00:00:00Z")),
            IssueActivityReading("APP-1", "ben", "Ben", Instant.parse("2026-02-01T00:00:00Z")),
        )
    }

    @Test
    fun `replacing the activity of one ticket leaves the others`() {
        val first = linked(artifacts.pullRequest("jira:1"), project)
        val second = linked(artifacts.pullRequest("jira:2"), project)
        store(activity(first, "Ada", "2026-01-01T00:00:00Z"), activity(second, "Ben", "2026-01-02T00:00:00Z"))

        assertThat(activities.deleteAllByArtifactId(first.id)).isEqualTo(1)
        assertThat(activities.findActivities(project, everything).map { it.displayName }).containsExactly("Ben")
    }

    @Test
    fun `reads the messages of the project's commits only`() {
        val commit = linked(artifacts.commit("github:acme/app:COMMIT:1"), project)
        linked(artifacts.commit("github:acme/app:COMMIT:2"), UUID.randomUUID())
        linked(artifacts.pullRequest("github:acme/app:PR:3"), project)
        entityManager.flush()
        entityManager.clear()

        assertThat(messages.findMessages(project)).containsExactly(CommitMessage(commit.id, "content"))
    }
}
