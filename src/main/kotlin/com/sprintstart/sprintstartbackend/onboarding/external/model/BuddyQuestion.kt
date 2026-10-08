package com.sprintstart.sprintstartbackend.onboarding.external.model

import java.time.Instant
import java.util.UUID

/**
 * A user-authored question exported from the chat module for cross-module analytics.
 *
 * @property id identifier of the underlying chat message
 * @property text the raw question text
 * @property askedAt when the question was asked. Consumers that group questions have no other way
 * to tell a topic that is picking up from one that has gone quiet, since the grouping itself
 * carries no time.
 */
data class BuddyQuestion(
    val id: UUID,
    val text: String,
    val askedAt: Instant,
)
