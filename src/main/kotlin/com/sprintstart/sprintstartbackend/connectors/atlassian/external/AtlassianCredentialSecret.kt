package com.sprintstart.sprintstartbackend.connectors.atlassian.external

/**
 * Carries a decrypted Atlassian API token out of the credential store for a connector's HTTP client.
 *
 * Deliberately redacts both fields from [toString] since this is the value that leaves the module
 * boundary with the plaintext token.
 */
data class AtlassianCredentialSecret(
    val userEmail: String,
    val apiToken: String,
) {
    override fun toString(): String = "AtlassianCredentialSecret(userEmail=<redacted>, apiToken=<redacted>)"
}
