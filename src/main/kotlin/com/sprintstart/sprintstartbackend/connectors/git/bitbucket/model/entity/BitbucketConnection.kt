package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity

import com.sprintstart.sprintstartbackend.connectors.ConnectionState
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

/**
 * One Bitbucket repository connected to the application.
 *
 * The connection stores the credentials to read the repository by name rather than by value, so
 * revoking a credential in one place stops every repository that used it.
 *
 * Each collector owns its own cursor, which is what makes an ingest incremental: files and commits
 * hold the revision their last ingest reached, pull requests hold the instant theirs started. An
 * empty revision and a null timestamp both mean "never ingested".
 *
 * @property lastSha The revision whose files were last ingested.
 * @property lastCommitsSyncedSha The revision whose commits were last ingested.
 * @property lastPullRequestsSyncAt When the last successful pull-request fetch started, or `null`
 *           when pull requests have never been read.
 * @property projectIdsInternal The SprintStart projects this repository is connected to.
 */
@Entity
@Table(
    name = "bitbucket_repositories",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_workspace_slug",
            columnNames = ["workspace", "slug"],
        ),
    ],
)
class BitbucketConnection(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    var workspace: String,
    @Column(nullable = false)
    var slug: String,
    @Column(name = "credential_auth_id", nullable = false)
    var credentialAuthId: String,
    @Column(name = "credential_name", nullable = false)
    var credentialName: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "connection_state", nullable = false)
    var connectionState: ConnectionState = ConnectionState.UP_TO_DATE,
    @Column(name = "source_enabled", nullable = false)
    var sourceEnabled: Boolean = true,
    @Column(name = "last_sha", nullable = false)
    var lastSha: String = "",
    @Column(name = "last_commits_synced_sha", nullable = false)
    var lastCommitsSyncedSha: String = "",
    @Column(name = "last_pr_sync")
    var lastPullRequestsSyncAt: Instant? = null,
    @ElementCollection
    @CollectionTable(
        name = "bitbucket_repository_projects",
        joinColumns = [JoinColumn(name = "repository_id")],
    )
    @Column(name = "project_id", nullable = false)
    var projectIdsInternal: MutableSet<UUID> = mutableSetOf(),
) {
    /** The projects this repository is connected to, as an immutable view. */
    val projectIds: Set<UUID>
        get() = projectIdsInternal.toSet()
}
