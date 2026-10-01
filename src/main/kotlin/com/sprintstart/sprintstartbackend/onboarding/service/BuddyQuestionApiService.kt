package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.BuddyQuestionApi
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyMessageRole
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyQuestion
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddyMessageRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * Service implementation of the chat API used by other modules.
 *
 * Provides a small module-facing adapter over the chat message repository without exposing internal
 * chat entities or service workflows.
 */
@Service
internal class BuddyQuestionApiService(
    private val messageRepository: BuddyMessageRepository,
) : BuddyQuestionApi {
    @Tracked("Retrieving user questions for a project")
    @Transactional(readOnly = true)
    override fun getUserQuestionsForProject(projectId: UUID): List<BuddyQuestion> {
        return messageRepository
            .findAllByRoleAndSessionProjectId(BuddyMessageRole.USER, projectId)
            .map { BuddyQuestion(id = it.id, text = it.content, askedAt = it.createdAt) }
    }

    @Tracked("Counting user questions for a project")
    @Transactional(readOnly = true)
    override fun countUserQuestionsForProject(projectId: UUID, since: Instant?): Long {
        if (since == null) {
            return messageRepository.countByRoleAndSessionProjectId(BuddyMessageRole.USER, projectId)
        }
        return messageRepository.countByRoleAndSessionProjectIdAndCreatedAtGreaterThanEqual(
            BuddyMessageRole.USER,
            projectId,
            since.atOffset(ZoneOffset.UTC),
        )
    }
}
