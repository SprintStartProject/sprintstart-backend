package com.sprintstart.sprintstartbackend.chat.models.requests

import jakarta.validation.constraints.Min
import java.util.UUID

/**
 * The incoming network request asking for the n latest chats.
 *
 * @property limit The limit (n) of chats to retrieve. Must either be >1, or if not given remains null,
 * in which case all chats are retrieved.
 * @property projectId Restricts the result to one project. `null` only for the admin listing, which
 * spans every project; the per-user listing always scopes to the project the caller is looking at,
 * so the chat sidebar follows the project switcher like the rest of the application.
 */
internal data class GetChatsRequest(
    @Min(1) val limit: Int?,
    val projectId: UUID? = null,
)
