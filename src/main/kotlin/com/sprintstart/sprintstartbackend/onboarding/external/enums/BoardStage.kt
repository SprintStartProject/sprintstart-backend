package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * How soon a card is worth the hire's attention.
 *
 * Two and not three. A third step ("next") was tried on the client and taken back out: neither a PM
 * writing a blueprint nor a hire sorting their board could defend the line between "next" and
 * "later", and a distinction nobody can apply is one that fills up with whatever was clicked first.
 *
 * **A `LATER` card is not locked.** It is put aside so it does not crowd the first days, and the
 * hire can open it whenever they like. What something *waits on* is a dependency, not a stage — see
 * [CardDependencySource].
 */
enum class BoardStage {
    NOW,
    LATER,
}
