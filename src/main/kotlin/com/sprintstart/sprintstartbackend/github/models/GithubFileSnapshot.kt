package com.sprintstart.sprintstartbackend.github.models

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "gh_file_snapshots")
data class GithubFileSnapshot(
    @Id
    var id: UUID = UUID.randomUUID(),
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_id")
    var snapshot: GithubRepositorySnapshot,
    @Column(nullable = false)
    var filePath: String,
    @Column(nullable = false)
    var contentHash: String,
    @Column(nullable = false)
    var lastIngestedAt: Instant,
)
