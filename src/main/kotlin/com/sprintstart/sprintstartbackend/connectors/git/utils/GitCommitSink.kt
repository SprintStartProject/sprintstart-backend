package com.sprintstart.sprintstartbackend.connectors.git.utils

import com.sprintstart.sprintstartbackend.shared.git.GitCommit

/**
 * Consumes the commits produced by [GitIngestionEngine].
 *
 * Commits are delivered one at a time rather than in batches: unlike file contents they carry no
 * payload, so the cost of a call is a reference rather than a copy of the repository.
 */
interface GitCommitSink {
    /**
     * Receives one commit, newest first.
     *
     * @param commit The commit as read from the clone.
     */
    suspend fun onCommit(commit: GitCommit)
}
