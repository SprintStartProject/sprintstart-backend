package com.sprintstart.sprintstartbackend.onboarding

import com.sprintstart.sprintstartbackend.onboarding.seeding.SeedingService
import com.sprintstart.sprintstartbackend.user.external.events.UserWorkingAreaUpdatedEvent
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Reacts to user working-area changes that affect onboarding state.
 *
 * This listener forms the onboarding module's explicit integration point with the user module.
 * It consumes [UserWorkingAreaUpdatedEvent] through Spring Modulith instead of reaching into
 * user module internals directly, then delegates the reseeding decision to [SeedingService].
 */
@Component
class OnboardingEventListener(
    private val seedingService: SeedingService,
) {
    /**
     * Handles a published working-area change for a user.
     *
     * The event already contains the identifiers and old/new values needed by onboarding, so the
     * listener can react without querying another module's repository.
     *
     * @param event Cross-module event describing the user's working-area update.
     */
    @ApplicationModuleListener
    fun on(event: UserWorkingAreaUpdatedEvent) {
        seedingService.handle(event)
    }
}
