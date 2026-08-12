package com.sprintstart.sprintstartbackend.user.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapsId
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "user_projects")
class ProjectUserAssignment(
    @EmbeddedId
    val id: ProjectUserAssignmentId = ProjectUserAssignmentId(),
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("projectId")
    @JoinColumn(name = "project_id", nullable = false)
    val project: Project,
    /**
     * When this person joined this project — the moment onboarding's clock starts.
     *
     * Nullable because assignments made before this column existed have no honest value to
     * backfill: guessing one would put a fabricated number underneath the metric the whole
     * initiative is judged on. A hire with no `assignedAt` is reported as "clock unknown" rather
     * than as instantaneous.
     */
    @Column(name = "assigned_at")
    val assignedAt: Instant? = Instant.now(),
) {
    constructor(user: User, project: Project) : this(
        id = ProjectUserAssignmentId(user.id, project.id),
        user = user,
        project = project,
    )
}

@Embeddable
data class ProjectUserAssignmentId(
    @Column(name = "user_id")
    var userId: UUID = UUID(0L, 0L),
    @Column(name = "project_id")
    var projectId: UUID = UUID(0L, 0L),
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
