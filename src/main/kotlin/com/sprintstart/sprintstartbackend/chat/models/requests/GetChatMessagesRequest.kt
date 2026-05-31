package com.sprintstart.sprintstartbackend.chat.models.requests

import jakarta.validation.constraints.Min

/**
 * The incoming network request asking for a specific chat's messages
 *
 * @property limit The limit of chats to retrieve. If given must be >1, if not given remains null,
 * in which case all messages of the chat are retrieved.
 */
data class GetChatMessagesRequest(
    @Min(1) val limit: Int?,
)
