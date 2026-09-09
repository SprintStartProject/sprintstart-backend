package com.sprintstart.sprintstartbackend.connectors.confluence.client

import java.net.URI
import java.net.URLEncoder

internal const val CONFLUENCE_COLLECTION_PAGE_LIMIT = 100
internal const val CONFLUENCE_RESTRICTION_PAGE_LIMIT = 100
internal const val CONFLUENCE_RESTRICTIONS_NOT_FOUND_MESSAGE =
    "Confluence page disappeared while retrieving read restrictions"
internal const val CONFLUENCE_RESTRICTIONS_RETRY_EXHAUSTED_MESSAGE =
    "Confluence page restrictions remained unavailable after retry attempts"

internal fun confluenceValidationUri(baseUrl: String): URI {
    return confluenceTenantUri(baseUrl, "/wiki/api/v2/spaces?limit=1")
}

internal fun confluenceSpacesUri(baseUrl: String): URI {
    return confluenceTenantUri(baseUrl, "/wiki/api/v2/spaces?limit=$CONFLUENCE_COLLECTION_PAGE_LIMIT")
}

internal fun confluenceSpaceUri(baseUrl: String, spaceId: String): URI {
    return confluenceTenantUri(baseUrl, "/wiki/api/v2/spaces/${spaceId.encodeConfluencePathSegment()}")
}

internal fun confluencePagesUri(baseUrl: String, spaceId: String): URI {
    val path = "/wiki/api/v2/spaces/${spaceId.encodeConfluencePathSegment()}/pages"
    return confluenceTenantUri(
        baseUrl,
        "$path?body-format=storage&limit=$CONFLUENCE_COLLECTION_PAGE_LIMIT",
    )
}

internal fun confluenceRestrictionsUri(baseUrl: String, pageId: String, start: Int): URI {
    val path = "/wiki/rest/api/content/${pageId.encodeConfluencePathSegment()}/restriction/byOperation/read"
    return confluenceTenantUri(
        baseUrl,
        "$path?start=$start&limit=$CONFLUENCE_RESTRICTION_PAGE_LIMIT",
    )
}

internal fun normalizedConfluenceTenantUri(baseUrl: String): URI {
    val uri = try {
        URI.create(baseUrl.trim().trimEnd('/'))
    } catch (@Suppress("SwallowedException") exception: IllegalArgumentException) {
        throw IllegalArgumentException("Confluence base URL is invalid")
    }
    require(uri.scheme == "https" || uri.scheme == "http") { "Confluence base URL must use HTTP or HTTPS" }
    require(uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null) {
        "Confluence base URL must identify a tenant origin"
    }
    return uri
}

internal fun confluencePaginationUri(
    tenantUri: URI,
    currentUri: URI,
    next: String,
    requestContext: String,
): URI {
    val resolved = try {
        currentUri.resolve(next)
    } catch (@Suppress("SwallowedException") exception: IllegalArgumentException) {
        throw ConfluenceInvalidResponseException(requestContext)
    }
    if (!resolved.hasSameConfluenceOriginAs(tenantUri)) {
        throw ConfluenceInvalidResponseException(requestContext)
    }
    return resolved
}

private fun confluenceTenantUri(baseUrl: String, path: String): URI {
    return normalizedConfluenceTenantUri(baseUrl).resolve(path)
}

private fun String.encodeConfluencePathSegment(): String {
    return URLEncoder.encode(this, Charsets.UTF_8).replace("+", "%20")
}

private fun URI.hasSameConfluenceOriginAs(other: URI): Boolean {
    return scheme.equals(other.scheme, ignoreCase = true) &&
        host.equals(other.host, ignoreCase = true) &&
        effectiveConfluencePort() == other.effectiveConfluencePort()
}

private fun URI.effectiveConfluencePort(): Int {
    if (port != -1) {
        return port
    }
    return if (scheme.equals("https", ignoreCase = true)) HTTPS_DEFAULT_PORT else HTTP_DEFAULT_PORT
}

private const val HTTP_DEFAULT_PORT = 80
private const val HTTPS_DEFAULT_PORT = 443
