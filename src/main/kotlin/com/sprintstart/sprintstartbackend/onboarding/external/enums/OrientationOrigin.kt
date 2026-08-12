package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * Who a [com.sprintstart.sprintstartbackend.onboarding.model.entity.TaskOrientationPacket] came from.
 *
 * Load-bearing for how a packet is served. The whole staleness machinery around orientation --
 * fingerprint the corpus, re-assemble when it moves, delete the cache when the AI can no longer
 * ground it -- is a guardrail against an *AI* packet quietly describing code that has since changed.
 * A [HUMAN] packet is a person's own words about the task, so it carries none of those risks and is
 * bound by none of those rules: it is served exactly as written, never fingerprinted, never
 * re-assembled, and never auto-deleted as stale. Editing an [AI] packet adopts it as [HUMAN] --
 * a human stood behind it, so it stops being a disposable cache entry.
 *
 * Deliberately AI | HUMAN rather than reusing [ContentProvenance]'s AI | PM: an orientation packet
 * can be authored by a PM *or* by the hire fixing their own task's orientation in place, and both
 * are "a person stands behind this".
 */
enum class OrientationOrigin { AI, HUMAN }
