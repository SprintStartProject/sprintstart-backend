package com.sprintstart.sprintstartbackend.onboarding.listener

import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPathRepository
import com.sprintstart.sprintstartbackend.user.external.events.UserMovedToProjectEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Resets a user's onboarding path when they are moved into another project.
 *
 * The path is the one piece of onboarding data that hangs on the user alone and is still built from
 * a project's blueprint, so after a move it would keep describing the project the user left. It is
 * deleted here and rebuilt from the new project's blueprint through the existing personalize flow
 * the next time the user opens it.
 *
 * Everything else onboarding keeps about a user, such as the board, goals or the Task Zero
 * assignment, carries a `projectId`. Those rows stay in the database but are not shown in the new
 * project, so they do not need to be touched.
 *
 * Runs in the transaction of the assignment, so a failed move takes the reset with it and the two
 * cannot end up disagreeing.
 */
@Component
class UserMovedToProjectListener(
    private val onboardingPathRepository: OnboardingPathRepository,
) {
    @EventListener
    @Transactional
    fun onUserMovedToProject(event: UserMovedToProjectEvent) {
        onboardingPathRepository.deleteByUserId(event.userId)
    }
}
