package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * Whether a
 * [com.sprintstart.sprintstartbackend.onboarding.model.entity.StarterWorkTaskProposal] is work a
 * hire can be pointed at.
 *
 * [LIVE] is claimable; [REJECTED] is terminal **and sticky** — a task somebody turned down is never
 * mined back into existence, or they would turn it down again after every crawl.
 *
 * ⚠️ **There is deliberately no `PROPOSED`.** A value meaning "awaiting review" makes a person's
 * attention a gate, and nothing would reach a hire until somebody worked through a queue. Mined
 * tasks land live and carry
 * [com.sprintstart.sprintstartbackend.onboarding.model.entity.StarterWorkTaskProposal.reviewed]
 * instead, with `StarterWorkMatcher` **demoting** the unreviewed ones: human attention improves
 * the ranking rather than blocking the pool.
 *
 * Starter work is the only lifecycle this serves, and it is not a review lifecycle.
 */
enum class ProposalStatus { LIVE, REJECTED }
