package com.sprintstart.sprintstartbackend.chat.models.requests

import jakarta.validation.constraints.Min

/**
 * The incoming network request asking for the n latest chats.
 *
 * @property limit The limit (n) of chats to retrieve. Must either be >1, or if not given remains null,
 * in which case all chats are retrieved.
 */
internal data class GetChatsRequest(
    @Min(1) val limit: Int?,
)
