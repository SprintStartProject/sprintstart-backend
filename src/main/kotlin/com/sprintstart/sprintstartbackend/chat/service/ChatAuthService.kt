package com.sprintstart.sprintstartbackend.chat.service

import com.sprintstart.sprintstartbackend.chat.models.Chat
import com.sprintstart.sprintstartbackend.chat.repository.ChatRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
@Validated
internal class ChatAuthService(
    private val chatRepository: ChatRepository,
) {
    fun resolveCurrentUserId(userApi: UserApi, authId: String): UUID {
        return userApi.getUserIdByAuthId(authId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Authenticated user not found")
        }
    }

    fun findOwnedChat(chatId: UUID, userId: UUID): Chat {
        return chatRepository.findByIdAndUserId(chatId, userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Chat with id $chatId not found")
        }
    }
}
