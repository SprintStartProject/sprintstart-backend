package com.sprintstart.sprintstartbackend.chat.models.requests

import com.sprintstart.sprintstartbackend.chat.models.ChatMessage
import jakarta.validation.constraints.NotBlank
import kotlinx.serialization.Serializable

/**
 * Used for prompting the AI on a new prompt, providing the chat context
 *
 * @property prompt The new prompt the AI should answer
 * @property context All relevant chat context, e.g. previous messages in this chat
 */
@Serializable
data class AiPromptRequest(
    @NotBlank val prompt: String,
    val context: List<ContextEntry>,
)

@Serializable
data class ContextEntry(
    @NotBlank val role: String,
    @NotBlank val content: String,
)

internal fun ChatMessage.toAiContextEntry(): ContextEntry {
    return ContextEntry(
        role = this.role.name.lowercase(),
        content = this.content,
    )
}
