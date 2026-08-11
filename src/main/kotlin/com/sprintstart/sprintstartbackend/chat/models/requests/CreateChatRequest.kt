package com.sprintstart.sprintstartbackend.chat.models.requests

import jakarta.validation.constraints.NotNull
import java.util.UUID

/**
 * The incoming network request for creating a new chat.
 *
 * @property userId The id of the user this chat belongs to.
 * @property projectId The project the chat is scoped to. Required: the AI service resolves every
 * prompt against a project, so a chat without one could never be prompted.
 */
internal data class CreateChatRequest(
    @NotNull val userId: UUID,
    @NotNull val projectId: UUID,
)

/**
 * The incoming network request for creating a chat owned by the authenticated user.
 *
 * Carries no user id — ownership is resolved from the JWT subject so a caller cannot create chats
 * under someone else's id.
 *
 * @property projectId The project the chat is scoped to.
 */
internal data class CreateMyChatRequest(
    @NotNull val projectId: UUID,
)
