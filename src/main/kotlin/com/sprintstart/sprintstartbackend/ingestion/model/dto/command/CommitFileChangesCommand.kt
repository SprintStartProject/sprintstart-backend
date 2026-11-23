package com.sprintstart.sprintstartbackend.ingestion.model.dto.command

import com.sprintstart.sprintstartbackend.shared.git.CommitFiles
import java.time.Instant

/**
 * The files one stored commit changed, as a neutral command every Git provider's mapper produces.
 *
 * Keeping it provider-neutral is what lets one service record the evidence for GitHub and
 * Bitbucket alike; the provider only decides how the commit's source id and repository key are
 * spelled.
 *
 * @property sourceId The source id of the commit artifact the files belong to.
 * @property repository The key of the repository the commit was read from, for example
 *           `github:owner/repo`.
 * @property authorName The Git author name recorded in the commit.
 * @property authorEmail The Git author email recorded in the commit.
 * @property changedAt The commit time.
 * @property files The files the commit changed.
 */
data class CommitFileChangesCommand(
    val sourceId: String,
    val repository: String,
    val authorName: String,
    val authorEmail: String,
    val changedAt: Instant,
    val files: CommitFiles,
)
