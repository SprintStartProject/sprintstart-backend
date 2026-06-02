package com.sprintstart.sprintstartbackend.chat.models

import kotlinx.serialization.Serializable

/**
 * Used to determine who actually provided this text in a chat.
 */
@Serializable
enum class ChatRole {
    ASSISTANT, // Answers from the AI
    USER, // Prompts from the user
    SYSTEM, // System prompts fine-tuning AI behaviour
}
