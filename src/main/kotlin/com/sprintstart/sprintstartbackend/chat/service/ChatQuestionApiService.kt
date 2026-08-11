package com.sprintstart.sprintstartbackend.chat.service

import com.sprintstart.sprintstartbackend.chat.external.ChatQuestion
import com.sprintstart.sprintstartbackend.chat.external.ChatQuestionApi
import com.sprintstart.sprintstartbackend.chat.models.ChatRole
import com.sprintstart.sprintstartbackend.chat.repository.ChatMessageRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Service implementation of the chat API used by other modules.
 *
 * Provides a small module-facing adapter over the chat message repository without exposing internal
 * chat entities or service workflows.
 */
@Service
internal class ChatQuestionApiService(
    private val messageRepository: ChatMessageRepository,
) : ChatQuestionApi {
    @Tracked("Retrieving user questions for a project")
    @Transactional(readOnly = true)
    override fun getUserQuestionsForProject(projectId: UUID): List<ChatQuestion> {
        return messageRepository
            .findAllByRoleAndChatProjectId(ChatRole.USER, projectId)
            .map { ChatQuestion(id = it.id, text = it.content) }
    }
}
