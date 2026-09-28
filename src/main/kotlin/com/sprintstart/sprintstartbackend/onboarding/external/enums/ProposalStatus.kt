package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * Whether a
 * [com.sprintstart.sprintstartbackend.onboarding.model.entity.StarterWorkTaskProposal] is work a
 * hire can be pointed at.
 *
 * [LIVE] is claimable; [REJECTED] is terminal and sticky — a task somebody turned down is never
 * mined back into existence, or they would turn it down again after every crawl.
 *
 * [STALE] is the pool catching up with its source: the issue was closed where it lives, so it is
 * not work to hand anybody, but nobody here judged it. That is why it is not [REJECTED] — a
 * rejection is a person's decision and must stay terminal, while this is a fact about the tracker
 * and reverses itself the moment the issue reopens. Keeping the two apart is what lets
 * reconciliation run repeatedly without ever undoing somebody's refusal.
 *
 * There is deliberately no `PROPOSED`. A value meaning "awaiting review" makes a person's
 * attention a gate, and nothing would reach a hire until somebody worked through a queue. Mined
 * tasks land live and carry
 * [com.sprintstart.sprintstartbackend.onboarding.model.entity.StarterWorkTaskProposal.reviewed]
 * instead, with `StarterWorkMatcher` demoting the unreviewed ones: human attention improves
 * the ranking rather than blocking the pool.
 *
 * Starter work is the only lifecycle this serves, and it is not a review lifecycle.
 */
enum class ProposalStatus { LIVE, REJECTED, STALE }
