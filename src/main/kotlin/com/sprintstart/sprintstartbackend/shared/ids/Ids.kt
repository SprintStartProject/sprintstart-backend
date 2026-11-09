package com.sprintstart.sprintstartbackend.shared.ids

import java.util.UUID

/** The Keycloak subject of the signed-in user, as the JWT carries it. */
@JvmInline
value class AuthId(
    val value: String,
)

/** A user of SprintStart, as the backend stores them. */
@JvmInline
value class UserId(
    val value: UUID,
)

/** A project the backend stores, which hires join and PMs run. */
@JvmInline
value class ProjectId(
    val value: UUID,
)

/** One of a hire's buddy conversations. */
@JvmInline
value class BuddySessionId(
    val value: UUID,
)

/** A card on a hire's board. */
@JvmInline
value class BoardCardId(
    val value: UUID,
)

/** One task of a step on a hire's onboarding path. */
@JvmInline
value class OnboardingTaskId(
    val value: UUID,
)
