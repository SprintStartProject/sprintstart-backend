package com.sprintstart.sprintstartbackend.connectors.git.github.util

import com.sprintstart.sprintstartbackend.connectors.git.utils.GitProviderDescriptor
import org.springframework.stereotype.Component

/**
 * Describes GitHub as a Git host to the shared connector plumbing.
 *
 * A single bean holds this rather than each service declaring its own copy, so the Git host, the
 * clone user name and the browser URL shapes have exactly one definition per provider — the same
 * arrangement the Bitbucket connector uses.
 */
@Component
class GithubGitProvider {
    /** GitHub expressed in provider-neutral terms. */
    val descriptor: GitProviderDescriptor = GitProviderDescriptor(
        host = CLONE_HOST,
        cloneUsername = CLONE_USERNAME,
        sourceUrls = GithubSourceUrls(),
    )

    private companion object {
        /** GitHub's Git host, where clones are read from. */
        const val CLONE_HOST = "github.com"

        /**
         * The static user name GitHub accepts for token-authenticated HTTPS clones.
         *
         * Using it instead of the user's login keeps cloning independent of which account the
         * stored PAT belongs to — the token alone decides what can be read.
         */
        const val CLONE_USERNAME = "x-access-token"
    }
}
