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
}
