package com.sprintstart.sprintstartbackend.github.models

import com.sprintstart.sprintstartbackend.github.models.client.Commit
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.Embedded
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapsId
import jakarta.persistence.Table
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "gh_file_snapshots")
data class GithubFileSnapshot(
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
