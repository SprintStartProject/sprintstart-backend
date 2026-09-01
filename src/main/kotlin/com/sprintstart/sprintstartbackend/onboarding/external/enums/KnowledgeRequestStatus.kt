package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * Where a hire's escalated question is in its life.
 *
 * `OPEN` is waiting on a PM; `ANSWERED` has a [com.sprintstart.sprintstartbackend.onboarding.model.entity.CanonicalAnswer]
 * behind it that the buddy can now serve; `DISMISSED` is a question a PM decided needs no durable
 * answer (a one-off, a duplicate) — closed without polluting the answer store.
 */
enum class KnowledgeRequestStatus {
    OPEN,
    ANSWERED,
    DISMISSED,
}
