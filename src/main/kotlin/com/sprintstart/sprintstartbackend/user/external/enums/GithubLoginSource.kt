package com.sprintstart.sprintstartbackend.user.external.enums

/**
 * How a user's GitHub login came to be on their record — i.e. how much it can be trusted.
 *
 * Artifact verification attributes a pull request to a hire by comparing its author against this
 * login, so the provenance matters: [SELF_DECLARED] is a claim the hire typed themselves, while
 * [PM_CONFIRMED] was set or corrected by a project manager. Nothing here proves ownership of the
 * GitHub account — that would need a federated GitHub login in Keycloak — so this records what is
 * actually known rather than implying more.
 */
enum class GithubLoginSource {
    /** Entered by the user on their own profile. Unverified. */
    SELF_DECLARED,

    /** Set or corrected by a PM/HR on the user's record. */
    PM_CONFIRMED,
}
