package com.sprintstart.sprintstartbackend.user.external.events

import java.util.UUID

/**
 * Published whenever a new user entity is persisted for the first time.
 *
 * Both the Just-In-Time (JIT) login path in [UserService] and the Keycloak
 * registration webhook in [KeycloakEventService] emit this event so that
 * other modules can react to user creation without coupling to user module
 * internals.
 *
 * @property userId The unique identifier of the newly created user.
 */
data class UserCreatedEvent(
    val userId: UUID,
)
