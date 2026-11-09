package com.sprintstart.sprintstartbackend.buddy.model.mapper

import com.sprintstart.sprintstartbackend.assistant.external.BuddyAgentMessageDto
import com.sprintstart.sprintstartbackend.buddy.external.enums.toHistoryRole
import com.sprintstart.sprintstartbackend.buddy.model.entity.BuddyCitation
import com.sprintstart.sprintstartbackend.buddy.model.entity.BuddyMessage
import com.sprintstart.sprintstartbackend.buddy.model.response.buddy.BuddyCitationResponse
import com.sprintstart.sprintstartbackend.buddy.model.response.buddy.BuddyMessageResponse
import com.sprintstart.sprintstartbackend.knowledge.external.NewQuestion
import com.sprintstart.sprintstartbackend.shared.ids.BuddySessionId
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.QuestionId
import com.sprintstart.sprintstartbackend.shared.ids.UserId

fun BuddyMessage.toResponse(): BuddyMessageResponse =
    BuddyMessageResponse(
        id = id,
        role = role,
        content = content,
        createdAt = createdAt,
        citations = citations.map { it.toResponse() },
        isIncomplete = isIncomplete,
    )

fun BuddyCitation.toResponse(): BuddyCitationResponse =
    BuddyCitationResponse(
        id = id,
        artifactId = artifactId,
        filename = filename,
        sourceUrl = sourceUrl,
        startLine = startLine,
        startPage = startPage,
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

/**
 * This message as the question log records it: [text] is the question without a quoted selection, and the
 * message itself identifies it, so deleting the message can forget it.
 */
fun BuddyMessage.toNewQuestion(project: ProjectId, text: String): NewQuestion =
    NewQuestion(
        id = QuestionId(id),
        conversation = BuddySessionId(session.id),
        askedBy = UserId(session.userId),
        project = project,
        text = text,
        askedAt = createdAt,
    )
