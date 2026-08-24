package com.sprintstart.sprintstartbackend.chat.service

import com.sprintstart.sprintstartbackend.chat.models.Chat
import com.sprintstart.sprintstartbackend.chat.repository.ChatRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
internal class ChatAuthService(
    private val chatRepository: ChatRepository,
) {
    /**
     * Retrieves the user id based on a given [authId].
     *
     * @param userApi The user api to use for lookup.
     * @param authId The JWT auth id to look up.
     * @return The internal user's UUID.
     * @throws [ResponseStatusException] (404), if the user was not found.
     */
    fun resolveCurrentUserId(userApi: UserApi, authId: String): UUID {
        return userApi.getUserIdByAuthId(authId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Authenticated user not found")
        }
    }

    /**
     * Retrieves a chat based on its id and the id of the user the chat "belongs" to.
     *
     * @param chatId The id of the chat to retrieve.
     * @param userId The UUID of the internal user that "owns" the chat.
     * @return The chat, if found.
     * @throws [ResponseStatusException] (404), if no chat could be found.
     */
    fun findOwnedChat(chatId: UUID, userId: UUID): Chat {
        return chatRepository.findByIdAndUserId(chatId, userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Chat with id $chatId not found")
        }
    }
}
