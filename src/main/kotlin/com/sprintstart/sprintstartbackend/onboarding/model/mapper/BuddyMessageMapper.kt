package com.sprintstart.sprintstartbackend.onboarding.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyMessageRole
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyAgentMessageDto
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyMessage
import com.sprintstart.sprintstartbackend.onboarding.model.response.buddy.BuddyMessageResponse

fun BuddyMessage.toResponse(): BuddyMessageResponse =
    BuddyMessageResponse(
        role = role,
        content = content,
        createdAt = createdAt,
    )

/**
 * This message as the AI service's stateless endpoints expect it.
 *
 * Shared rather than duplicated because two callers now send a hire's transcript to the AI — the
 * agent turn and the background fold — and a conversation the mentor answers from must be spelled
 * the same way as the one it remembers from.
 *
 * The `when` is exhaustive on purpose: `role.name.lowercase()` would silently change the wire
 * value if the enum were ever renamed.
 */
fun BuddyMessage.toAgentMessage(): BuddyAgentMessageDto =
    BuddyAgentMessageDto(role = role.toHistoryRole(), content = content)

private fun BuddyMessageRole.toHistoryRole(): String =
    when (this) {
        BuddyMessageRole.USER -> "user"
        BuddyMessageRole.ASSISTANT -> "assistant"
    }
