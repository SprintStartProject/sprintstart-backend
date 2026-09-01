package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.user.external.enums.GithubLoginSource
import com.sprintstart.sprintstartbackend.user.model.entity.User
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

/**
 * Owns writing a user's GitHub login: normalization, validation and uniqueness.
 *
 * Extracted from [UserService] because both the self-service and the PM update path set it, and
 * because it is more than an assignment -- artifact verification attributes a submitted pull
 * request to a hire by comparing its author against this value, so a wrong or ambiguous login is a
 * grading bug, not a cosmetic profile flaw.
 */
@Service
class GithubLoginService(
    private val userRepository: UserRepository,
) {
    /**
     * Stores [githubLogin] on [user] together with how it was established.
     *
     * A blank value clears both fields, so a user can withdraw a wrong handle rather than being
     * stuck with it. The value is trimmed and lower-cased: GitHub logins are case-insensitive, and
     * a case difference must not read as a different person during PR attribution.
     *
     * @throws ResponseStatusException 400 when the value is not a syntactically valid GitHub
     * login; 409 when another user already claims it (which would make attribution ambiguous).
     */
    fun apply(user: User, githubLogin: String, source: GithubLoginSource) {
        val normalized = githubLogin.trim().lowercase()

        if (normalized.isEmpty()) {
            user.githubLogin = null
            user.githubLoginSource = null
            clearVerification(user)
            return
        }

        if (!GITHUB_LOGIN.matches(normalized)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "'$githubLogin' is not a valid GitHub username")
        }

        if (userRepository.existsByGithubLoginAndIdNot(normalized, user.id)) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "GitHub account '$normalized' is already linked to another user",
            )
        }

        // A verdict is about a *value*. Leaving the old one in place after a correction would show
        // "we could not find that account" against the login that replaced it, which is the same
        // class of wrong answer this verification exists to prevent. Nothing verifies here: the
        // check is an HTTP call and this runs inside the caller's transaction.
        if (user.githubLogin != normalized) {
            clearVerification(user)
        }

        user.githubLogin = normalized
        user.githubLoginSource = source
    }

    private fun clearVerification(user: User) {
        user.githubLoginVerification = null
        user.githubLoginVerifiedAt = null
    }

    private companion object {
        // GitHub's own rule: alphanumerics and single inner hyphens, max 39 characters.
        val GITHUB_LOGIN = Regex("^[a-z\\d](?:[a-z\\d]|-(?=[a-z\\d])){0,38}$")
    }
}
