package com.sprintstart.sprintstartbackend.chat.models.requests

import jakarta.validation.constraints.NotNull
import java.util.UUID

/**
 * The incoming network request for creating a new chat.
 *
 * @property userId The id of the user this chat belongs to.
 */
internal data class CreateChatRequest(
    @NotNull val userId: UUID,
)
