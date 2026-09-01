package com.sprintstart.sprintstartbackend.chat.external

import java.time.Instant
import java.util.UUID

/**
 * Exported chat-module API for other backend modules.
 *
 * Other modules should depend on this interface instead of calling chat-module services or
 * repositories directly.
 */
interface ChatQuestionApi {
    /**
     * Returns every user-authored question asked in one project's chats.
     *
     * Only messages with the user role are returned; assistant and system messages are excluded.
     * Scoped to a project because the FAQ insights built from these questions are shown per
     * project — an unscoped list would surface one project's questions in another's panel.
     * Chats predating project scoping carry no project and are therefore excluded.
     *
     * @param projectId The project whose chats to collect questions from.
     */
    fun getUserQuestionsForProject(projectId: UUID): List<ChatQuestion>

    /**
     * Counts the questions [getUserQuestionsForProject] would return, optionally only recent ones.
     *
     * Exists so a caller can tell a user how much material an operation would work on without
     * loading every question's text to find out.
     *
     * @param projectId The project whose chats to count questions in.
     * @param since Only count questions asked at or after this moment; null counts all of them.
     */
    fun countUserQuestionsForProject(projectId: UUID, since: Instant? = null): Long
}
