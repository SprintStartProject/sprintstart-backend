package com.sprintstart.sprintstartbackend.chat.repository

import com.sprintstart.sprintstartbackend.chat.models.ChatMessage
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
internal interface ChatMessageRepository : JpaRepository<ChatMessage, UUID> {
    /**
     * Fetches chat messages by chat id.
     *
     * This function fetches chat messages from the database. The `pageable` parameter allows specifying a limit of
     * messages to fetch as well as the order in which to return/fetch.
     *
     * @param chatId The id of the chat for which to fetch messages.
     * @param pageable The pageable that allows configuring a limit as well as ordering.
     * @return [List<String>] A list of the n latest chat messages (n being the pageable limit)
     * @see Pageable
     */
    @Query(
        value = """
        SELECT * FROM chat_messages c WHERE c.chat_id = :chat_id
    """,
        countQuery = """
        SELECT count(*) FROM chat_messages c WHERE c.chat_id = :chat_id
    """,
        nativeQuery = true,
    )
    fun findAllByChat(@Param("chat_id") chatId: UUID, pageable: Pageable): Page<ChatMessage>
}
