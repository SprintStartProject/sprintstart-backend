package com.sprintstart.sprintstartbackend.onboarding.model

/**
 * What one unit of a hire's accepted work is called, and how their own act reads.
 *
 * Fixed, because every hire onboards as an engineer. The words are kept in one place rather than
 * written into each sentence so that "merged change" and "merged changes" cannot drift apart
 * across the ramp, the board, the metrics and the buddy's persona.
 *
 * Bare noun: it is always rendered next to [VERB_PAST] — "merged change" — and baking the verb
 * into the noun produces "merged merged change" the moment a sentence needs both.
 */
object ContributionWording {
    const val NOUN = "change"
    const val NOUN_PLURAL = "changes"
    const val VERB_PAST = "merged"
}
