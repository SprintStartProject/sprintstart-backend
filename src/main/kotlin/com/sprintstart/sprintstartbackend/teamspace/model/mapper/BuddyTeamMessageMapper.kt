package com.sprintstart.sprintstartbackend.teamspace.model.mapper

import com.sprintstart.sprintstartbackend.assistant.external.BuddyAgentMessageDto
import com.sprintstart.sprintstartbackend.buddy.external.enums.toHistoryRole
import com.sprintstart.sprintstartbackend.buddy.model.response.buddy.BuddyMessageResponse
import com.sprintstart.sprintstartbackend.teamspace.model.entity.BuddyTeamMessage

/** A team-mode message in the same client shape as the hire's, so one transcript view renders both. */
fun BuddyTeamMessage.toResponse(): BuddyMessageResponse =
    BuddyMessageResponse(
        id = id,
        role = role,
        content = content,
        createdAt = createdAt,
        isIncomplete = isIncomplete,
    )

/** A team-mode message as the AI expects it, spelled by the same role mapping as the hire's. */
fun BuddyTeamMessage.toAgentMessage(): BuddyAgentMessageDto =
    BuddyAgentMessageDto(role = role.toHistoryRole(), content = content)
