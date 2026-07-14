package com.sprintstart.sprintstartbackend.user.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.ManyToOne
import jakarta.persistence.MapsId
import jakarta.persistence.Table
import java.io.Serializable
import java.util.UUID

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
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_project_assignment_roles",
        joinColumns = [
            JoinColumn(
                name = "user_id",
                referencedColumnName = "user_id",
                nullable = false,
                foreignKey = ForeignKey(name = "fk_upar_user_project_user"),
            ),
            JoinColumn(
                name = "project_id",
                referencedColumnName = "project_id",
                nullable = false,
                foreignKey = ForeignKey(name = "fk_upar_user_project_project"),
            ),
        ],
        inverseJoinColumns = [
            JoinColumn(
                name = "role_id",
                nullable = false,
                foreignKey = ForeignKey(name = "fk_upar_role_id"),
            ),
        ],
    )
    var projectRoles: MutableSet<ProjectRole> = mutableSetOf(),
) {
    constructor(user: User, project: Project) : this(
        id = ProjectUserAssignmentId(user.id, project.id),
        user = user,
        project = project,
    )
}
