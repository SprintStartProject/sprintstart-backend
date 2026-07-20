package com.sprintstart.sprintstartbackend.connectors.github.models

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapsId
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

@Entity
@Table(name = "gh_repository_configs")
class GithubRepositoryConfig(
    @Id
    var id: UUID? = null,
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "repository_id")
    var repository: GithubRepositoryConnection,
    @Column(name = "auto_update", nullable = false)
    var autoUpdate: Boolean = false,
    @Column(nullable = false)
    var schedule: String = "0 0 2 * * *",
    @Column(columnDefinition = "TEXT")
    @Convert(converter = ScheduleSpecJpaConverter::class)
    var spec: ScheduleSpec = ScheduleSpec.Daily(time = LocalTime.of(2, 0)),
    @Column("next_sync_at")
    var nextSyncAt: Instant? = null,
)
