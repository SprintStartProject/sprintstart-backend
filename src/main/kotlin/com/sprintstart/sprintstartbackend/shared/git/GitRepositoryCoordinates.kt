package com.sprintstart.sprintstartbackend.shared.git

/**
 * Identifies one hosted repository and the credentials needed to read it.
 *
 * This is the only thing [ICustomOnDiskCache] knows about a repository, which is what keeps the
 * cache free of any connector: each connector maps its own connection entity onto these
 * coordinates instead of handing over its entity or a ready-made URL.
 *
 * [host] is part of the identity, not just of the URL. The cache stores clones under
 * `<cache-path>/<host>/<namespace>/<name>`, so a repository that exists on more than one provider
 * — `github.com/acme/api` and `bitbucket.org/acme/api` — ends up in two directories instead of
 * one clone being reused by both.
 *
 * [secret] is a credential. It is not loggable, so [toString] masks it; only the cache's
 * URL building should read it.
 *
 * @property host The hosting provider's Git host, e.g. `github.com` or `bitbucket.org`.
 * @property namespace The owning account or group: GitHub's owner, Bitbucket's workspace.
 * @property name The repository name without any `.git` suffix: GitHub's name, Bitbucket's slug.
 * @property username The user name used for HTTPS clone authentication. Providers accept fixed
 *           values for token authentication, so this is not necessarily a real account name.
 * @property secret The token or password that authenticates [username].
 */
data class GitRepositoryCoordinates(
    val host: String,
    val namespace: String,
    val name: String,
    val username: String,
    val secret: String,
) {
    /**
     * The namespace split into its hierarchical segments, outermost first.
     *
     * Most providers address a repository by a single owning account, so this is normally a
     * one-element list. Providers that allow nested groups, such as GitLab's `group/subgroup`, are
     * the reason it is a list rather than a string: the cache and the clone URL both need the
     * segments, and neither should have to guess at how a separator was meant.
     */
    val namespacePath: List<String>
        get() = namespace.split('/').filter(String::isNotBlank)

    /** Renders the coordinates with the secret masked, so they can be logged safely. */
    override fun toString(): String =
        "GitRepositoryCoordinates(host=$host, namespace=$namespace, name=$name, " +
            "username=$username, secret=***)"
}
