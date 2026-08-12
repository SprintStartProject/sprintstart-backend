package com.sprintstart.sprintstartbackend.onboarding.model.response.setup

import java.util.UUID

/**
 * Whether a project is ready to onboard someone, as a ladder of setup stages.
 *
 * Derived on read from what each stage's own surface already stores -- there is no readiness table
 * to fall behind, and a stage completed before this shipped still reads done. Composed, not gated:
 * per the "retire the gates" decision, a PM can proceed at any state; the rungs are a nudge.
 *
 * The corpus stage is deliberately **absent** here. Corpus health belongs to the ingestion module
 * (its own `IngestionStatusController`), and this endpoint stays inside the onboarding module rather
 * than reaching across the boundary to re-derive it. The client composes the corpus rung onto the
 * top of this ladder from the ingestion status it already fetches.
 */
data class SetupReadinessResponse(
    val projectId: UUID,
    val rungs: List<SetupRungResponse>,
    /** True when every rung this endpoint owns is [RungState.OK]. A summary of the rungs, not a lock. */
    val ready: Boolean,
)

data class SetupRungResponse(
    /** Stable key the client renders off: `skill-map`, `starter-tasks`, `tracks`. */
    val key: String,
    val state: RungState,
    /** The positive quantity for this rung (competencies, live starter tasks, ...) -- never a pending count. */
    val count: Int,
    /** One sentence describing what is there, or what could not be built and why. */
    val detail: String,
)

/**
 * Whether a stage is in the state a ready project has.
 *
 * ⚠️ **There is no third value, and there must not be one.** A `BLOCKED` state would render as a
 * padlock on the one surface whose entire point is that **nothing here gates anything**. Keeping it
 * out of the enum rather than merely unused is what makes that enforceable rather than a comment.
 *
 * @property OK This stage is in the state a ready project has.
 * @property WARN It is not, yet. Never a lock and never a chore — most of what lands here follows
 * from the corpus rather than from anything a person has failed to do.
 */
enum class RungState { OK, WARN }
