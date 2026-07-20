package com.sprintstart.sprintstartbackend.chat.external

/**
 * Exported chat-module API for other backend modules.
 *
 * Other modules should depend on this interface instead of calling chat-module services or
 * repositories directly.
 */
interface ChatQuestionApi {
    /**
     * Returns every user-authored question across all chats.
     *
     * Only messages with the user role are returned; assistant and system messages are excluded.
     */
    fun getAllUserQuestions(): List<ChatQuestion>
}
