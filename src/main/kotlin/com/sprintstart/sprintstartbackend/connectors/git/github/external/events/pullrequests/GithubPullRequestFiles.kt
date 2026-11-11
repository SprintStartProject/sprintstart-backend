package com.sprintstart.sprintstartbackend.connectors.git.github.external.events.pullrequests

/**
 * The files a pull request changed.
 *
 * GitHub returns a bounded page of them. A pull request that changed more files than one page holds
 * is reported as [Incomplete] instead of as a silently truncated list, because a reviewer cannot be
 * said to know the files that were cut off.
 */
sealed interface GithubPullRequestFiles {
    /**
     * Every file the pull request changed.
     *
     * @property paths The paths of the changed files.
     */
    data class Listed(
        val paths: List<String>,
    ) : GithubPullRequestFiles

    /** More files than one page holds, or GitHub did not report them. */
    data object Incomplete : GithubPullRequestFiles
}
