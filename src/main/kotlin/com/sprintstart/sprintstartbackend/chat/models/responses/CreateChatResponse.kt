package com.sprintstart.sprintstartbackend.chat.models.responses

import java.util.UUID

/**
 * The response to send out to the frontend including a new chat's id.
 *
 * @property id The id of the new chat.
 */
data class CreateChatResponse(
    val id: UUID,
)
