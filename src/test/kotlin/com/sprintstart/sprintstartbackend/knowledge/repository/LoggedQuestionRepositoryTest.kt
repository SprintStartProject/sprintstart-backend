package com.sprintstart.sprintstartbackend.knowledge.repository

import com.sprintstart.sprintstartbackend.knowledge.model.entity.LoggedQuestion
import com.sprintstart.sprintstartbackend.shared.crypto.CryptoConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/** The log's derived queries: which project, since when, and what a deletion takes with it. */
@ActiveProfiles("test")
@DataJpaTest
// A JPA slice loads no @Configuration of its own, but the entity graph reaches an
// AttributeConverter that needs the encryptor.
@Import(CryptoConfiguration::class)
class LoggedQuestionRepositoryTest {
    @Autowired
    private lateinit var repository: LoggedQuestionRepository

    private val now: Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)
    private val project = UUID.randomUUID()

    private fun question(
        projectId: UUID = project,
        sessionId: UUID = UUID.randomUUID(),
        userId: UUID = UUID.randomUUID(),
        askedAt: Instant = now,
    ) = repository.save(
        LoggedQuestion(
            id = UUID.randomUUID(),
            sessionId = sessionId,
            userId = userId,
            projectId = projectId,
            text = "How do we deploy?",
            askedAt = askedAt,
        ),
    )

    @Test
    fun `reads and counts only the project's questions, and only recent ones when asked`() {
        val old = question(askedAt = now.minus(10, ChronoUnit.DAYS))
        val recent = question(askedAt = now.minus(1, ChronoUnit.DAYS))
        question(projectId = UUID.randomUUID())

        assertThat(repository.findAllByProjectId(project).map { it.id }).containsExactlyInAnyOrder(old.id, recent.id)
        assertThat(repository.countByProjectId(project)).isEqualTo(2)
        assertThat(repository.countByProjectIdAndAskedAtGreaterThanEqual(project, now.minus(1, ChronoUnit.DAYS)))
            .isEqualTo(1)
    }

    @Test
    fun `forgetting a conversation takes only that conversation's questions`() {
        val conversation = UUID.randomUUID()
        question(sessionId = conversation)
        question(sessionId = conversation)
        val other = question()

        repository.deleteAllBySessionId(conversation)

        assertThat(repository.findAllByProjectId(project).map { it.id }).containsExactly(other.id)
    }

    @Test
    fun `erasing a user takes only the questions they asked`() {
        val user = UUID.randomUUID()
        question(userId = user)
        val other = question()

        repository.deleteAllByUserId(user)

        assertThat(repository.findAllByProjectId(project).map { it.id }).containsExactly(other.id)
    }
}
