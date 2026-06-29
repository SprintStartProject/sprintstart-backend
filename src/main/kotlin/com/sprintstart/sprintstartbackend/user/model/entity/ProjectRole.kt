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
)
