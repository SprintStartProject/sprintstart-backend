package com.sprintstart.sprintstartbackend.connectors.confluence.service

import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceConnectionConfigurationException
import java.net.URI

internal fun normalizeConfluenceBaseUrl(rawBaseUrl: String): String {
    val uri = parseConfluenceBaseUrl(rawBaseUrl)
    validateHttps(uri)
    validateTenantOrigin(uri)
    validateTenantPath(uri)

    val normalizedPort = if (uri.port == HTTPS_DEFAULT_PORT) -1 else uri.port
    return URI(
        "https",
        null,
        uri.host.lowercase().trimEnd('.'),
        normalizedPort,
        null,
        null,
        null,
    ).toString()
}

private fun parseConfluenceBaseUrl(rawBaseUrl: String): URI {
    return try {
        URI.create(rawBaseUrl.trim())
    } catch (@Suppress("SwallowedException") exception: IllegalArgumentException) {
        throw ConfluenceConnectionConfigurationException("Confluence base URL is invalid")
    }
}

private fun validateHttps(uri: URI) {
    if (!uri.scheme.equals("https", ignoreCase = true)) {
        throw ConfluenceConnectionConfigurationException("Confluence base URL must use HTTPS")
    }
}

private fun validateTenantOrigin(uri: URI) {
    val validOriginParts = listOf(
        !uri.host.isNullOrBlank(),
        uri.userInfo == null,
        uri.query == null,
        uri.fragment == null,
    )
    if (validOriginParts.any { isValid -> !isValid }) {
        throw ConfluenceConnectionConfigurationException("Confluence base URL must identify a tenant origin")
    }
}

private fun validateTenantPath(uri: URI) {
    val normalizedPath = uri.path.trimEnd('/')
    if (normalizedPath.isNotEmpty() && normalizedPath != "/wiki") {
        throw ConfluenceConnectionConfigurationException("Confluence base URL path must be empty or /wiki")
    }
}

internal fun normalizeConfluencePageIds(values: List<String>, listName: String): List<String> {
    val normalized = values.map { value -> value.trim() }
    if (normalized.any { value -> value.isEmpty() }) {
        throw ConfluenceConnectionConfigurationException("Confluence $listName must not contain blank page IDs")
    }
    return normalized.distinct()
}

private const val HTTPS_DEFAULT_PORT = 443
