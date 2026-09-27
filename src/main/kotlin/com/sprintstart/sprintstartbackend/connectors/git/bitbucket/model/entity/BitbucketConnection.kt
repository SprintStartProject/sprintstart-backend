package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity

import com.sprintstart.sprintstartbackend.connectors.ConnectionState
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * One Bitbucket repository connected to the application.
 *
 * The connection stores the credentials to read the repository by name rather than by value, so
 * revoking a credential in one place stops every repository that used it.
 *
 * The two revision cursors are what make an ingest incremental. Each holds the revision the last
 * ingest of that kind reached, and an empty value means "never ingested", which the ingestion engine
 * treats as "read everything". Files and commits are tracked separately because their ingests are
 * independent: reading one fully must not make the other look already done.
 *
 * @property lastSha The revision whose files were last ingested.
 * @property lastCommitsSyncedSha The revision whose commits were last ingested.
 */
@Entity
@Table(name = "bitbucket_repositories")
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
)
