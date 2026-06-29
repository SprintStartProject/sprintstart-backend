package com.sprintstart.sprintstartbackend.user.model.entity

import com.sprintstart.sprintstartbackend.user.external.enums.Role
import com.sprintstart.sprintstartbackend.user.external.enums.WorkingArea
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "sprintstart_users")
class User(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false, unique = true, updatable = false)
    val authId: String,
    @Column(nullable = false)
    var username: String,
    @Column(nullable = true)
    var email: String?,
    @Column(nullable = false)
    var firstname: String,
    @Column(nullable = false)
    var lastname: String,
    @Column(nullable = false)
    var enabled: Boolean = true,
    @Column(nullable = true)
    var profileIcon: String? = null,
    @Column(nullable = false)
    var hasCompletedOnboarding: Boolean = false,
    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(
        name = "user_roles",
        joinColumns = [JoinColumn(name = "id")],
    )
    @Column(name = "role", nullable = false)
    val roles: MutableSet<Role> = mutableSetOf(),
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    var workingArea: WorkingArea,
    @Column(nullable = true)
    var avatarUrl: String? = null,
    @jakarta.persistence.ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @jakarta.persistence.JoinColumn(name = "project_id")
    var project: Project? = null,
    @jakarta.persistence.ManyToMany(fetch = jakarta.persistence.FetchType.LAZY)
    @jakarta.persistence.JoinTable(
        name = "user_project_roles",
        joinColumns = [
            jakarta.persistence.JoinColumn(
                name = "user_id",
                foreignKey = jakarta.persistence.ForeignKey(
                    name = "fk_upr_user_id",
                    foreignKeyDefinition = "FOREIGN KEY (user_id) REFERENCES sprintstart_users ON DELETE CASCADE",
                ),
            ),
        ],
        inverseJoinColumns = [
            jakarta.persistence.JoinColumn(
                name = "role_id",
                foreignKey = jakarta.persistence.ForeignKey(
                    name = "fk_upr_role_id",
                    foreignKeyDefinition = "FOREIGN KEY (role_id) REFERENCES " +
                        "sprintstart_project_roles ON DELETE CASCADE",
                ),
            ),
        ],
    )
    @org.hibernate.annotations.BatchSize(size = 50)
    var projectRoles: MutableSet<ProjectRole> = mutableSetOf(),
    @jakarta.persistence.OneToMany(
        mappedBy = "user",
        cascade = [jakarta.persistence.CascadeType.ALL],
        orphanRemoval = true,
    )
    @org.hibernate.annotations.BatchSize(size = 50)
    var skillAssessments: MutableSet<UserSkillAssessment> = mutableSetOf(),
)
