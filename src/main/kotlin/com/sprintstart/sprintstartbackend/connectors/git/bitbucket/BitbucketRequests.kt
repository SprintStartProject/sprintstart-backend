package com.sprintstart.sprintstartbackend.connectors.git.bitbucket

import com.sprintstart.sprintstartbackend.connectors.atlassian.external.AtlassianCredentialSecret
import com.sprintstart.sprintstartbackend.shared.web.RequestBuilder
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Builds an authenticated `GET` request for a Bitbucket URI.
 *
 * Every call in this client goes through here, so the transport concerns — the authorization
 * scheme and the accepted content type — are decided in exactly one place.
 *
 * Authentication uses HTTP Basic with the credential's email as the username and its API token
 * as the password, which is the scheme Bitbucket Cloud accepts for personal API tokens. Bearer
 * only authenticates OAuth and workspace access tokens, so it cannot authenticate the stored
 * credentials this client is handed.
 *
 * @param uri The absolute request URI. Collection URLs taken from a `next` link are passed
 * through unchanged.
 * @param credentials The caller's stored Atlassian credential; its email and API token authenticate the request.
 * @return The prepared request, ready for `.sync()`.
 */
internal fun WebClient.authorizedGet(uri: String, credentials: AtlassianCredentialSecret): RequestBuilder =
    get()
        .uri(uri)
        .header("Accept", "application/json")
        .header("Authorization", "Basic ${basicCredentials(credentials)}")

/**
 * Encodes one credential pair for the `Authorization` header.
 *
 * Base64 of `email:token` in UTF-8, per RFC 7617. Kept separate so no call site ever logs or
 * concatenates the plaintext pair itself.
 *
 * @param credentials The credential whose email and API token to encode.
 * @return The Base64 `email:token` pair, without the `Basic` prefix.
 */
private fun basicCredentials(credentials: AtlassianCredentialSecret): String =
    Base64.getEncoder().encodeToString(
        "${credentials.userEmail}:${credentials.apiToken}".toByteArray(StandardCharsets.UTF_8),
    )
