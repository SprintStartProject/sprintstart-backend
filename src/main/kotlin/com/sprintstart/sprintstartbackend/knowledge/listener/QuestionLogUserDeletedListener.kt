package com.sprintstart.sprintstartbackend.knowledge.listener

import com.sprintstart.sprintstartbackend.knowledge.repository.LoggedQuestionRepository
import com.sprintstart.sprintstartbackend.user.external.events.OnUserDeleted
import com.sprintstart.sprintstartbackend.user.external.events.UserDeletedEvent
import org.springframework.stereotype.Component

/** Erases the questions a deleted user asked. */
@Component
class QuestionLogUserDeletedListener(
    private val loggedQuestionRepository: LoggedQuestionRepository,
) {
    @OnUserDeleted
    fun onUserDeleted(event: UserDeletedEvent) {
        loggedQuestionRepository.deleteAllByUserId(event.userId)
    }
}
