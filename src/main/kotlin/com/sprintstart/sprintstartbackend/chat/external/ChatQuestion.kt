package com.sprintstart.sprintstartbackend.chat.external

import java.util.UUID

/**
 * A user-authored question exported from the chat module for cross-module analytics.
 *
 * @property id identifier of the underlying chat message
 * @property text the raw question text
 */
data class ChatQuestion(
    val id: UUID,
    val text: String,
)
