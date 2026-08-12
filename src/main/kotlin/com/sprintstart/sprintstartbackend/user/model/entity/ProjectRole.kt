package com.sprintstart.sprintstartbackend.user.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "sprintstart_project_roles")
class ProjectRole(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false, unique = true)
    var name: String,
    @Column(nullable = false)
    var description: String,
    /**
     * Which onboarding track somebody in this role onboards on.
     *
     * A plain key rather than a foreign key: the track is owned by the onboarding module, and this
     * module must not hold a JPA relation into it. The same loosely-coupled convention
     * `Verification.repositoryConnectionId` and every `competencyKey` already use.
     *
     * Null means "not decided", which resolves to the default track rather than failing — a role
     * created before tracks existed, or by somebody who did not care, must keep working.
     */
    @Column(name = "onboarding_track_key", nullable = true)
    var onboardingTrackKey: String? = null,
)
