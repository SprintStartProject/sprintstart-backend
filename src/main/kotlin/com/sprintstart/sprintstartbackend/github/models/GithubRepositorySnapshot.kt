package com.sprintstart.sprintstartbackend.github.models

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "gh_repository_snapshots")
data class GithubRepositorySnapshot(
    @Id
    var id: UUID = UUID.randomUUID(),
    @OneToOne(mappedBy = "snapshot", fetch = FetchType.LAZY)
    var repository: GithubRepositoryConnection,
    @OneToMany(mappedBy = "snapshot", fetch = FetchType.LAZY)
    val files: List<GithubFileSnapshot>,
    @Column(nullable = false)
    var lastCommitsSyncAt: Instant,
    @Column(nullable = false)
    var lastIssuesSyncAt: Instant,
    @Column(nullable = false)
    var lastPullRequestsSyncAt: Instant,
)
