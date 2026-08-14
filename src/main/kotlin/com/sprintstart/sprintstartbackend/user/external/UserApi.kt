package com.sprintstart.sprintstartbackend.user.external

import com.sprintstart.sprintstartbackend.user.external.dto.UserDto
import com.sprintstart.sprintstartbackend.user.external.enums.GithubLoginVerification
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.Instant
import java.util.Optional
import java.util.UUID

/**
 * Exported user-module API for other backend modules.
 *
 * Other modules should depend on this interface instead of calling user-module services
 * or repositories directly.
 */
@Suppress("TooManyFunctions") // The size of the module boundary, not one class doing unrelated things.
interface UserApi {
    /** Marks the given user's onboarding as completed. Idempotent. */
    fun markOnboardingCompleted(userId: UUID)

    /**
     * Checks whether a user projection exists for the given SprintStart user ID.
     *
     * @param id Internal SprintStart user identifier.
     * @return `true` when the user exists, otherwise `false`.
     */
    fun exists(id: UUID): Boolean

    /**
     * Resolves the internal SprintStart user ID for a Keycloak authentication subject.
     *
     * @param authId External authentication identifier from Keycloak.
     * @return The matching user ID when present.
     */
    fun getUserIdByAuthId(authId: String): Optional<UUID>

    fun getUserByAuthId(authId: String): UserDto

    fun searchUsers(
        search: String?,
        roleIds: List<UUID>?,
        projectIds: List<UUID>?,
        pageable: Pageable,
    ): Page<UserDto>

    fun getUsersByIds(ids: List<UUID>): List<UserDto>

    /**
     * Returns the onboarding-relevant profile for a user identified by auth ID.
     *
     * @param authId External authentication identifier.
     * @return The user's onboarding profile when present.
     */
    fun getOnboardingProfileByAuthId(authId: String): Optional<UserOnboardingProfile>

    fun userHasAccessToProject(authId: String, projectId: UUID): Boolean

    /**
     * Returns the GitHub account a user contributes as, if they have declared one.
     *
     * Artifact verification uses this to attribute a submitted pull request to the hire who
     * submitted it. Always lower-cased, because GitHub logins are case-insensitive and a case
     * difference must not read as a different person.
     *
     * @param userId Internal SprintStart user identifier.
     * @return The user's GitHub login, or `null` when they have none (or do not exist).
     */
    fun getGithubLoginByUserId(userId: UUID): String?

    /**
     * Everything the GitHub-history seeding feature needs about a user, in one read.
     *
     * Bundled rather than exposed as three accessors because they are only ever used together, and
     * the module boundary should describe a purpose rather than mirror columns.
     *
     * @return The user's seeding context, or `null` when no such user exists.
     */
    fun getGithubSeedingContext(userId: UUID): GithubSeedingContext?

    /**
     * Records or clears consent for using a user's existing repository work to calibrate their
     * skill assessment. `null` withdraws it.
     */
    fun setGithubSeedingConsent(userId: UUID, consentedAt: Instant?)

    /**
     * Records the GitHub account a user says their work comes from, and returns it as stored.
     *
     * The value is normalised (trimmed, lower-cased) by the one service that owns this field, so
     * the caller gets back what was actually written rather than what it passed in.
     *
     * Exposed on the module boundary because the buddy can now be told a username in conversation
     * — a second *entry point*, never a second writer: the rules (syntax, uniqueness, and clearing
     * a stale verification verdict when the value changes) stay in one place.
     *
     * @throws org.springframework.web.server.ResponseStatusException 400 when the value is not a
     * possible GitHub username; 409 when another user already claims it; 404 when no such user.
     */
    fun setGithubLogin(userId: UUID, githubLogin: String): String

    /**
     * Records what GitHub said about whether a user's declared login exists.
     *
     * Only ever called with a definitive answer. A rate limit or an outage must leave the previous
     * verdict alone rather than recording "not found", because a wrong "that account does not
     * exist" in front of somebody whose account is fine is worse than saying nothing at all.
     *
     * A no-op when the user does not exist.
     */
    fun recordGithubLoginVerification(userId: UUID, verification: GithubLoginVerification)
}

/**
 * The user-module facts a consent-gated history prior is built from.
 *
 * @property githubLogin The account their work is attributed to; `null` until declared.
 * @property projectIds The projects whose corpus may be read on their behalf -- never any other.
 * @property seedingConsentAt When they opted in, or `null` when they have not (or withdrew).
 */
data class GithubSeedingContext(
    val githubLogin: String?,
    val projectIds: Set<UUID>,
    val seedingConsentAt: Instant?,
)
