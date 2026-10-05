package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity

import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduleSpec
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduleSpecJpaConverter
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

/**
 * The update behavior of one connected Bitbucket repository.
 *
 * A config shares its primary key with its repository, so exactly one exists per connection and the
 * two cannot drift apart. The cron [schedule] is what the executor compares [nextSyncAt] against;
 * [spec] is the structured form the user submitted, kept alongside it so the API can return the
 * schedule the caller originally chose rather than only its cron expansion.
 *
 * [autoUpdate] defaults to `true` so a repository keeps updating on its own as soon as it is
 * connected; a caller turns it off to keep the connection but stop the scheduled ingest. When it is
 * off, the tick still advances [nextSyncAt], so re-enabling later resumes on the normal schedule
 * instead of firing once for every skipped interval.
 *
 * @property repository The connection this configuration belongs to; provides the id.
 * @property autoUpdate Whether the scheduled ingest runs for this repository.
 * @property schedule The Spring six-field cron expression derived from [spec].
 * @property spec The structured schedule the caller submitted.
 * @property nextSyncAt When the repository is next due for an update.
 */
@Entity
@Table(name = "bb_repository_configs")
internal class BitbucketRepositoryConfig(
    @Id
    var id: UUID? = null,
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "repository_id")
    var repository: BitbucketConnection,
    @Column(name = "auto_update", nullable = false)
    var autoUpdate: Boolean = true,
    @Column(nullable = false)
    var schedule: String = "0 0 2 * * *",
    @Column(columnDefinition = "TEXT")
    @Convert(converter = ScheduleSpecJpaConverter::class)
    var spec: ScheduleSpec = ScheduleSpec.Daily(time = LocalTime.of(2, 0)),
    @Column(name = "next_sync_at")
    var nextSyncAt: Instant? = null,
)
