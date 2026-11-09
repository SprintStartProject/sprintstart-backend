package com.sprintstart.sprintstartbackend.knowledge.service

import com.sprintstart.sprintstartbackend.knowledge.external.AskedQuestion
import com.sprintstart.sprintstartbackend.knowledge.external.NewQuestion
import com.sprintstart.sprintstartbackend.knowledge.external.QuestionLogApi
import com.sprintstart.sprintstartbackend.knowledge.external.event.QuestionAskedEvent
import com.sprintstart.sprintstartbackend.knowledge.model.entity.LoggedQuestion
import com.sprintstart.sprintstartbackend.knowledge.repository.LoggedQuestionRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.shared.ids.BuddySessionId
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.QuestionId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** The question log, stored in `knowledge_questions`. */
@Service
internal class QuestionLogService(
    private val loggedQuestionRepository: LoggedQuestionRepository,
    private val eventPublisher: ApplicationEventPublisher,
) : QuestionLogApi {
    @Transactional
    override fun record(question: NewQuestion) {
        loggedQuestionRepository.save(
            LoggedQuestion(
                id = question.id.value,
                sessionId = question.conversation.value,
                userId = question.askedBy.value,
                projectId = question.project.value,
                text = question.text,
                askedAt = question.askedAt,
            ),
        )
        eventPublisher.publishEvent(
            QuestionAskedEvent(
                messageId = question.id.value,
                sessionId = question.conversation.value,
                projectId = question.project.value,
                question = question.text,
                askedAt = question.askedAt,
            ),
        )
    }

    @Transactional
    override fun forget(question: QuestionId) {
        loggedQuestionRepository.deleteById(question.value)
    }

    @Transactional
    override fun forgetConversation(conversation: BuddySessionId) {
        loggedQuestionRepository.deleteAllBySessionId(conversation.value)
    }

    @Tracked("Retrieving user questions for a project")
    @Transactional(readOnly = true)
    override fun questionsFor(project: ProjectId): List<AskedQuestion> =
        loggedQuestionRepository
            .findAllByProjectId(project.value)
            .map { AskedQuestion(id = it.id, text = it.text, askedAt = it.askedAt) }

    @Tracked("Counting user questions for a project")
    @Transactional(readOnly = true)
    override fun countFor(project: ProjectId, since: Instant?): Long =
        if (since == null) {
            loggedQuestionRepository.countByProjectId(project.value)
        } else {
            loggedQuestionRepository.countByProjectIdAndAskedAtGreaterThanEqual(project.value, since)
        }
}
