package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.utils

import com.sprintstart.sprintstartbackend.connectors.git.utils.GitProviderDescriptor
import org.springframework.stereotype.Component

/**
 * Describes Bitbucket Cloud as a Git host to the shared ingestion engine.
 *
 * The Git host is deliberately separate from `sprintstart.bitbucket.base-url`: that one addresses
 * the REST API at `api.bitbucket.org`, while clones are read over Git from `bitbucket.org`.
 *
 * A single bean holds this rather than each service declaring its own copy, so the host and the
 * clone user name have exactly one definition per provider.
 */
@Component
internal class BitbucketGitProvider {
    /** Bitbucket Cloud expressed in provider-neutral terms. */
    val descriptor: GitProviderDescriptor = GitProviderDescriptor(
        host = CLONE_HOST,
        cloneUsername = CLONE_USERNAME,
        sourceUrls = BitbucketSourceUrls(),
    )

    private companion object {
        /** Bitbucket Cloud's Git host, where clones are read from. */
        const val CLONE_HOST = "bitbucket.org"

        /**
         * The static user name Bitbucket Cloud accepts instead of a Bitbucket user name.
         *
         * Using it means a clone does not need to know which Bitbucket account an API token belongs
         * to, so a connection only stores the token's name.
         */
        const val CLONE_USERNAME = "x-bitbucket-api-token-auth"
    }
}
