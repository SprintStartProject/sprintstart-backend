package com.sprintstart.sprintstartbackend.user.model.entity

import com.sprintstart.sprintstartbackend.user.external.enums.SkillStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "sprintstart_skills")
class Skill(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false, unique = true)
    var name: String,
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "sprintstart_skill_project_roles",
        joinColumns = [JoinColumn(name = "skill_id")],
        inverseJoinColumns = [JoinColumn(name = "role_id")],
    )
    var projectRoles: MutableSet<ProjectRole> = mutableSetOf(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: SkillStatus = SkillStatus.ACTIVE,
)
