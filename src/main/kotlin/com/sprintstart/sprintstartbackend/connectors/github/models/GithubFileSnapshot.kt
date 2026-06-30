package com.sprintstart.sprintstartbackend.connectors.github.models

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapsId
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "gh_file_snapshots")
class GithubFileSnapshot(
    @EmbeddedId
    var id: GithubFileSnapshotSharedId = GithubFileSnapshotSharedId(),
    @Column(nullable = false)
    var sha: String,
    @Column(name = "last_ingested_at", nullable = false)
    var lastIngestedAt: Instant = Instant.now(),
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("repositoryId")
    @JoinColumn(name = "repository_id")
    var repository: GithubRepositoryConnection? = null,
)

@Embeddable
data class GithubFileSnapshotSharedId(
    @Column(name = "repository_id")
    var repositoryId: UUID? = null,
    var path: String = "",
)
