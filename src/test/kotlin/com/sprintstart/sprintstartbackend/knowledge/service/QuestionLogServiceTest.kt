package com.sprintstart.sprintstartbackend.knowledge.service

import com.sprintstart.sprintstartbackend.knowledge.external.AskedQuestion
import com.sprintstart.sprintstartbackend.knowledge.external.NewQuestion
import com.sprintstart.sprintstartbackend.knowledge.external.event.QuestionAskedEvent
import com.sprintstart.sprintstartbackend.knowledge.listener.QuestionLogUserDeletedListener
import com.sprintstart.sprintstartbackend.knowledge.model.entity.LoggedQuestion
import com.sprintstart.sprintstartbackend.knowledge.repository.LoggedQuestionRepository
import com.sprintstart.sprintstartbackend.shared.ids.BuddySessionId
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.QuestionId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.user.external.events.UserDeletedEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.UUID

class QuestionLogServiceTest {
    private val repository: LoggedQuestionRepository = mockk(relaxed = true)
    private val eventPublisher: ApplicationEventPublisher = mockk(relaxed = true)
    private val service = QuestionLogService(repository, eventPublisher)

    private val messageId = UUID.randomUUID()
    private val sessionId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val projectId = UUID.randomUUID()
    private val askedAt = Instant.parse("2026-10-20T09:00:00Z")

    private fun newQuestion() = NewQuestion(
        id = QuestionId(messageId),
        conversation = BuddySessionId(sessionId),
        askedBy = UserId(userId),
        project = ProjectId(projectId),
        text = "How do we deploy?",
        askedAt = askedAt,
    )

    /** Stored first, then announced, so a listener that reads the log back finds the question. */
    @Test
    fun `recording keeps the question and then announces it`() {
        val saved = slot<LoggedQuestion>()
        every { repository.save(capture(saved)) } answers { firstArg() }

        service.record(newQuestion())

        with(saved.captured) {
            assertThat(id).isEqualTo(messageId)
            assertThat(this.sessionId).isEqualTo(this@QuestionLogServiceTest.sessionId)
            assertThat(this.userId).isEqualTo(this@QuestionLogServiceTest.userId)
            assertThat(this.projectId).isEqualTo(this@QuestionLogServiceTest.projectId)
            assertThat(text).isEqualTo("How do we deploy?")
            assertThat(this.askedAt).isEqualTo(askedAt)
        }
        verifyOrder {
            repository.save(any())
            eventPublisher.publishEvent(
                QuestionAskedEvent(
                    messageId = messageId,
                    sessionId = sessionId,
                    projectId = projectId,
                    question = "How do we deploy?",
                    askedAt = askedAt,
                ),
            )
        }
    }

    @Test
    fun `reads a project's questions as the question, its message and when it was asked`() {
        every { repository.findAllByProjectId(projectId) } returns listOf(
            LoggedQuestion(messageId, sessionId, userId, projectId, "How do we deploy?", askedAt),
        )

        assertThat(service.questionsFor(ProjectId(projectId)))
            .containsExactly(AskedQuestion(id = messageId, text = "How do we deploy?", askedAt = askedAt))
    }

    @Test
    fun `counts all of a project's questions, or only those since a moment`() {
        every { repository.countByProjectId(projectId) } returns 7
        every { repository.countByProjectIdAndAskedAtGreaterThanEqual(projectId, askedAt) } returns 2

        assertThat(service.countFor(ProjectId(projectId))).isEqualTo(7)
        assertThat(service.countFor(ProjectId(projectId), since = askedAt)).isEqualTo(2)
    }

    @Test
    fun `forgets one question by its message, or a whole conversation`() {
        service.forget(QuestionId(messageId))
        service.forgetConversation(BuddySessionId(sessionId))

        verify { repository.deleteById(messageId) }
        verify { repository.deleteAllBySessionId(sessionId) }
    }

    @Test
    fun `deleting a user erases the questions they asked`() {
        QuestionLogUserDeletedListener(repository).onUserDeleted(UserDeletedEvent(userId))

        verify { repository.deleteAllByUserId(userId) }
    }
}
