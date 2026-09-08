package com.sprintstart.sprintstartbackend.chat.models.requests

import com.sprintstart.sprintstartbackend.chat.models.AiChatFilters
import com.sprintstart.sprintstartbackend.chat.models.ChatFilters
import com.sprintstart.sprintstartbackend.chat.models.ChatMessage
import jakarta.validation.constraints.NotBlank
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Used for prompting the AI on a new prompt, providing the chat context
 *
 * @property prompt The new prompt the AI should answer
 * @property context All relevant chat context, e.g. previous messages in this chat
 * @property projectId The project the owning chat is scoped to. Required by the AI service:
 * retrieval is fail-closed on it, so an unscoped request would find nothing at all rather than
 * search everything.
 */
@Serializable
data class AiPromptRequest(
    @NotBlank
    @SerialName("question")
    val prompt: String,
    @SerialName("history")
    val context: List<ContextEntry>,
    @SerialName("projectId")
    val projectId: String,
    val filters: AiChatFilters? = null,
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

internal fun ChatFilters.toAiChatFilters(): AiChatFilters {
    return AiChatFilters(
        sourceSystems = this.sourceSystems,
        from = this.from?.toString(),
        to = this.to?.toString(),
    )
}
