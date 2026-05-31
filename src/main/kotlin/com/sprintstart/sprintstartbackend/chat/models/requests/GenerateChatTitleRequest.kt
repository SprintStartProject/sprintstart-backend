package com.sprintstart.sprintstartbackend.chat.models.requests

/**
 * The outgoing AI request for generating a new chat title.
 *
 * @property prompt The initial chat prompt to generate the title based off of.
 */
data class GenerateChatTitleRequest(
    val prompt: String,
)
