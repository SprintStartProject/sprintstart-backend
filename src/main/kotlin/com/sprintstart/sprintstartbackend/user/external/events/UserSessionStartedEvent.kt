package com.sprintstart.sprintstartbackend.user.external.events

import java.util.UUID

/**
 * Published when a user's authenticated request follows an idle gap past the configured session
 * threshold -- this backend's stand-in for a real login boundary (see [SessionActivityService]
 * [com.sprintstart.sprintstartbackend.user.service.SessionActivityService]; there is no true
 * session/login concept available, since the Keycloak event listener SPI filters `LOGIN` events
 * out before they would ever reach this backend).
 *
 * Not published for a user's very first-ever activity, since nothing has a baseline to reconcile
 * against yet.
 *
 * @property userId The unique identifier of the user whose session started.
 */
data class UserSessionStartedEvent(
    val userId: UUID,
)
