package com.sprintstart.sprintstartbackend.connectors.github.external

/**
 * Module-facing API for interacting with GitHub user data needed outside the GitHub module.
 */
interface GithubUserApi {
    /**
     * Checks if a given `username` corresponds to a user on GitHub.
     *
     * @param username The username to check if it corresponds to a GitHub account.
     */
    suspend fun userExistsInGithub(username: String): Boolean?
}
