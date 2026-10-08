package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyMessageRole
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyMessage
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddySession
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddyMessageRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.UUID

class BuddyQuestionApiServiceTest {
    private val messageRepository = mockk<BuddyMessageRepository>()
    private val projectId: UUID = UUID.randomUUID()
    private val service = BuddyQuestionApiService(messageRepository)

    @Test
    fun `getUserQuestionsForProject maps the project's user messages to questions`() {
        val session = BuddySession(
            userId = UUID.randomUUID(),
            projectId = projectId,
        )
        val message = BuddyMessage(
            role = BuddyMessageRole.USER,
            session = session,
            content = "How do I get VPN access?",
        )

        every {
            messageRepository.findAllByRoleAndSessionProjectId(
                BuddyMessageRole.USER,
                projectId,
            )
        } returns listOf(message)

        val result = service.getUserQuestionsForProject(projectId)

        Assertions.assertEquals(1, result.size)
        Assertions.assertEquals(message.id, result.first().id)
        Assertions.assertEquals("How do I get VPN access?", result.first().text)
        verify(exactly = 1) {
            messageRepository.findAllByRoleAndSessionProjectId(
                BuddyMessageRole.USER,
                projectId,
            )
        }
    }
}
