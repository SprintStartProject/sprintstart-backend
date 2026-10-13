package com.sprintstart.sprintstartbackend.connectors.git.utils

/**
 * Builds the browser-facing URLs of one Git host.
 *
 * Each provider spells these differently — a file on GitHub lives under `/blob/`, on Bitbucket
 * under `/src/`, on GitLab under `/-/blob/` — and the host itself differs between a provider's
 * cloud and its self-hosted edition. Gathering the three shapes behind one interface keeps those
 * differences in a single provider-local class instead of scattered through services, where they
 * would drift the moment a fourth site hard-coded a URL.
 *
 * Implementations must not embed credentials: these URLs are shown to users and stored on
 * artifacts.
 */
interface GitSourceUrls {
    /**
     * The URL of the repository itself.
     *
     * @param namespacePath The owning account or group, segment by segment.
     * @param name The repository name without a `.git` suffix.
     */
    fun repositoryUrl(namespacePath: List<String>, name: String): String

    /**
     * The URL of one file at one revision.
     *
     * @param revision The revision the file's content was read at, so the link shows what was
     *        actually ingested rather than whatever the default branch holds now.
     */
    fun fileUrl(namespacePath: List<String>, name: String, revision: String, path: String): String

    /**
     * The URL of one commit.
     *
     * @param sha The full commit SHA.
     */
    fun commitUrl(namespacePath: List<String>, name: String, sha: String): String
}
