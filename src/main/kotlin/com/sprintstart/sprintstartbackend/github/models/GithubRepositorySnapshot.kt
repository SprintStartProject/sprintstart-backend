package com.sprintstart.sprintstartbackend.github.models

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "gh_repository_snapshots")
class GithubRepositorySnapshot(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "last_commits_sync_at", nullable = false)
    var lastCommitsSyncAt: Instant = Instant.now(),
    @Column(name = "last_issues_sync_at", nullable = false)
    var lastIssuesSyncAt: Instant = Instant.now(),
    @Column(name = "last_pr_sync_at", nullable = false)
    var lastPullRequestsSyncAt: Instant = Instant.now(),
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "repository_id")
    var repository: GithubRepositoryConnection,
)
