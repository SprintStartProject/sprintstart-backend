package com.sprintstart.sprintstartbackend.onboarding

import com.sprintstart.sprintstartbackend.onboarding.seeding.SeedingService
import com.sprintstart.sprintstartbackend.user.external.events.UserCreatedEvent
import com.sprintstart.sprintstartbackend.user.external.events.UserWorkingAreaUpdatedEvent
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

/**
 * Reacts to user lifecycle events that affect onboarding state.
 *
 * This listener forms the onboarding module's explicit integration point with the user module.
 * It consumes [UserCreatedEvent] and [UserWorkingAreaUpdatedEvent] through Spring Modulith
 * instead of reaching into user module internals directly, then delegates to [SeedingService].
 */
@Component
class OnboardingEventListener(
    private val seedingService: SeedingService,
) {
    /**
     * Seeds a default onboarding path immediately when a new user is created.
     *
     * This ensures [GET /api/v1/onboarding/me/path] returns a valid response
     * for every user from their very first login, with no working area selection required.
     *
     * @param event Cross-module event signalling that a new user has been persisted.
     */
    @ApplicationModuleListener
    fun on(event: UserCreatedEvent) {
        seedingService.seedDefault(event.userId)
    }

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
