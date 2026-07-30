package com.sprintstart.sprintstartbackend.connectors.jira.model.entity

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

@Entity
@Table(name = "jira_instance_configs")
internal class JiraInstanceConfig(
    @Id
    var id: String? = null,
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "instance_id")
    var instance: JiraInstance,
    @Column(name = "auto_update", nullable = false)
    var autoUpdate: Boolean = false,
    @Column(nullable = false)
    var schedule: String = "0 0 2 * * *",
    @Column(columnDefinition = "TEXT")
    @Convert(converter = ScheduleSpecJpaConverter::class)
    var spec: ScheduleSpec = ScheduleSpec.Daily(time = LocalTime.of(2, 0)),
    @Column(name = "next_sync_at")
    var nextSyncAt: Instant? = null,
)
