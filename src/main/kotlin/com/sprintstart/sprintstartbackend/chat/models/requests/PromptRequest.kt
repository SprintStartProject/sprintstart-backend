package com.sprintstart.sprintstartbackend.chat.models.requests

import com.sprintstart.sprintstartbackend.chat.models.ChatFilters
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.util.UUID

/**
 * The incoming network request for prompting the ai.
 *
 * @property chatId The id of the chat this prompt belongs to. Must not be empty/blank.
 * @property msg The actual text prompt. Must not be empty/blank.
 * @property filters The filters determining what kind of documents the AI may use to generate the response.
 */
data class PromptRequest(
    @NotNull val chatId: UUID,
    @NotBlank val msg: String,
    @field:Valid val filters: ChatFilters? = null,
)
