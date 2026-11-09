package com.sprintstart.sprintstartbackend.path.service

import com.sprintstart.sprintstartbackend.path.model.mapper.toResponse
import com.sprintstart.sprintstartbackend.path.model.response.question.QuestionAttemptResponse
import com.sprintstart.sprintstartbackend.path.repository.QuestionAttemptRepository
import com.sprintstart.sprintstartbackend.shared.ids.CheckQuestionId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import org.springframework.stereotype.Component

/** A hire's answers to the path's checks, read without the access checks an HTTP caller goes through. */
@Component
class QuestionAttemptReader(
    private val questionAttemptRepository: QuestionAttemptRepository,
) {
    /** [userId]'s most recent attempt at [questionId], or null when they have not answered it yet. */
    fun lastAttempt(questionId: CheckQuestionId, userId: UserId): QuestionAttemptResponse? =
        questionAttemptRepository
            .findAllByQuestionIdAndUserIdOrderByCreatedAtDesc(questionId.value, userId.value)
            .firstOrNull()
            ?.toResponse()
}
