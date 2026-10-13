package com.sprintstart.sprintstartbackend.connectors.atlassian.external

/**
 * Exposes the shared Atlassian credential store to other connector modules (Jira, Confluence).
 *
 * Implementations must never persist or log the decrypted token; it only ever leaves the module
 * inside an [AtlassianCredentialSecret] for immediate use by a connector's HTTP client.
 */
interface AtlassianCredentialApi {
    /** Resolves the decrypted secret for one user-scoped credential, or `null` if it does not exist. */
    fun findSecret(authId: String, credentialName: String): AtlassianCredentialSecret?

    /**
     * Resolves the decrypted secrets of every credential a user has stored.
     *
     * Used by callers that must try a user's credentials one after another — a visibility probe that
     * only needs *one* of the user's tokens to reach a resource, not a specific named one. An empty
     * list means the user has no credentials at all.
     *
     * @param authId The user whose credentials should be resolved.
     * @return The decrypted secrets, empty when the user has none.
     */
    fun findAllSecretsByAuthId(authId: String): List<AtlassianCredentialSecret>
}
