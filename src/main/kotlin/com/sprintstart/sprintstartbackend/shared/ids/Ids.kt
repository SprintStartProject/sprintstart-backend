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

/** A blueprint path: the template a project's onboarding paths are built from. */
@JvmInline
value class BlueprintId(
    val value: UUID,
)

/** A change an assistant offered and a user has yet to confirm or dismiss. */
@JvmInline
value class ActionProposalId(
    val value: UUID,
)

/** A question in the question log, identified by the chat message it was asked in. */
@JvmInline
value class QuestionId(
    val value: UUID,
)

/** A knowledge-check question in a phase of a hire's onboarding path. */
@JvmInline
value class CheckQuestionId(
    val value: UUID,
)

/** A question a hire escalated to the project's PMs because the Buddy could not answer it. */
@JvmInline
value class KnowledgeRequestId(
    val value: UUID,
)

/** An answer a PM gave and the project keeps, served to the next hire who asks something like it. */
@JvmInline
value class CanonicalAnswerId(
    val value: UUID,
)

/** A task in the starter-work pool, proposed from an open issue for hires to take on. */
@JvmInline
value class StarterWorkTaskId(
    val value: UUID,
)

/** A phase of a hire's onboarding path. */
@JvmInline
value class PhaseId(
    val value: UUID,
)

/** A step in a phase of a hire's onboarding path. */
@JvmInline
value class StepId(
    val value: UUID,
)

/** A resource (a link to read or watch) on a step of a hire's onboarding path. */
@JvmInline
value class ResourceId(
    val value: UUID,
)

/** A hire's request to skip one step of their onboarding path. */
@JvmInline
value class SkipId(
    val value: UUID,
)

/** Feedback a hire left on their onboarding. */
@JvmInline
value class FeedbackId(
    val value: UUID,
)
