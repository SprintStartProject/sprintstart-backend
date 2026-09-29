package com.sprintstart.sprintstartbackend.onboarding.listener

import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPathRepository
import com.sprintstart.sprintstartbackend.user.external.events.UserMovedToProjectEvent
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class UserMovedToProjectListenerTest {
    private val onboardingPathRepository: OnboardingPathRepository = mockk(relaxed = true)

    private val listener = UserMovedToProjectListener(onboardingPathRepository)

    private val userId = UUID.randomUUID()

    /**
     * The path was built from the old project's blueprint. Deleting it lets the personalize flow
     * build a new one from the project the user moved into.
     */
    @Test
    fun `moving a user deletes their onboarding path`() {
        listener.onUserMovedToProject(UserMovedToProjectEvent(userId, UUID.randomUUID(), listOf(UUID.randomUUID())))

        verify(exactly = 1) { onboardingPathRepository.deleteByUserId(userId) }
    }

    /** Only the path is reset. Everything else onboarding holds is scoped to a project already. */
    @Test
    fun `moving a user touches nothing but the path of that user`() {
        listener.onUserMovedToProject(UserMovedToProjectEvent(userId, UUID.randomUUID(), listOf(UUID.randomUUID())))

        verify(exactly = 1) { onboardingPathRepository.deleteByUserId(userId) }
        confirmVerified(onboardingPathRepository)
    }
}
