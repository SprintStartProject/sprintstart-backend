package com.sprintstart.sprintstartbackend.chat.repository

import com.sprintstart.sprintstartbackend.chat.models.Citation
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
internal interface CitationRepository : JpaRepository<Citation, UUID> {

    @Modifying
    @Query(
        value = """
        DELETE FROM citations
        WHERE message_id IN (
            SELECT id
            FROM chat_messages
            WHERE chat_id = :chatId
        )
        """,
        nativeQuery = true,
    )
    fun deleteAllByMessageChatId(@Param("chatId") chatId: UUID)
}
