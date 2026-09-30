package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.client

import kotlinx.serialization.Serializable

/**
 * One page of a Bitbucket Cloud collection response.
 *
 * Bitbucket pages every collection (`/repositories/...`, `/workspaces/.../members`, pull request
 * comments, ...) in this shape. Instead of page numbers, a page carries an absolute `next` URL
 * that is already the complete, correctly cursor-ed address of the following page, and omits the
 * field on the last page. Callers walk collections by following [next] verbatim until it is null.
 *
 * Unknown response fields (size, page, pagelen) are ignored by the shared JSON configuration.
 *
 * @param T the collection's item type.
 * @param values the items on this page, in API order.
 * @param next the absolute URL of the following page, or null when this is the last page.
 */
@Serializable
data class BitbucketPage<T>(
    val values: List<T> = emptyList(),
    val next: String? = null,
)
