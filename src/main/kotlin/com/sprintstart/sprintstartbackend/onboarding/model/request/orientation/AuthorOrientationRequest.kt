package com.sprintstart.sprintstartbackend.onboarding.model.request.orientation

import com.sprintstart.sprintstartbackend.onboarding.external.enums.OrientationOrigin
import com.sprintstart.sprintstartbackend.onboarding.external.enums.OrientationStep

/**
 * A human-authored orientation packet for one task, replacing whatever was there before.
 *
 * Authored by a PM from the Starter Work page, or by the hire fixing their own task's orientation in
 * place. Saving pins the packet as [OrientationOrigin.HUMAN], so it is thereafter served exactly as
 * written and never AI-re-assembled or auto-deleted.
 *
 * Deliberately lighter than the AI's own contract in one way: a human [section] is **not required to
 * carry citations**. The mandatory-citation rule ("no section ships uncited") is a guardrail against
 * the AI asserting things the corpus does not support; a person writing about a task they are doing
 * is not bound by it. Citations remain optional, for a human who wants to point at a source.
 */
data class AuthorOrientationRequest(
    val summary: String? = null,
    val sections: List<AuthorOrientationSectionRequest> = emptyList(),
)

/** One section of a human-authored packet, belonging to exactly one step of the path to a PR. */
data class AuthorOrientationSectionRequest(
    val step: OrientationStep,
    val title: String,
    val body: String,
    val citations: List<AuthorOrientationCitationRequest> = emptyList(),
)

/**
 * An optional source link a human attached to a section.
 *
 * Carries no `chunkId`: that is a handle into the AI's retrieval index, meaningless for a human who
 * is naming a file or pasting a URL. A human citation stores an empty chunk id, which the reader
 * never sees anyway -- only [filename] and [sourceUrl] reach the client.
 */
data class AuthorOrientationCitationRequest(
    val filename: String,
    val sourceUrl: String? = null,
)
