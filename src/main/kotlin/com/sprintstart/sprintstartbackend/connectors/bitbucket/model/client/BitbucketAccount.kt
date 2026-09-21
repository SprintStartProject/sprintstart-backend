package com.sprintstart.sprintstartbackend.connectors.bitbucket.model.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A Bitbucket user account as the API embeds it into other resources.
 *
 * Embedded accounts appear in workspace members, pull request authors, and comment authors. The
 * fields are nullable because an account may have been deactivated or deleted after it authored a
 * resource, in which case Bitbucket still reports the placeholder shape without identity fields.
 */
@Serializable
data class BitbucketAccount(
    val uuid: String? = null,
    @SerialName("account_id")
    val accountId: String? = null,
    val nickname: String? = null,
    @SerialName("display_name")
    val displayName: String? = null,
    val links: BitbucketLinks? = null,
)

/**
 * The link block Bitbucket attaches to most resources.
 *
 * Every link points at the resource's human-facing page; the `html` entry is the one this
 * application reads, e.g. to display a discovered repository or a pull request.
 */
@Serializable
data class BitbucketLinks(
    val html: BitbucketLink? = null,
)

@Serializable
data class BitbucketLink(
    val href: String? = null,
)
