package com.sprintstart.sprintstartbackend.user.model.entity

import com.sprintstart.sprintstartbackend.user.external.enums.WorkingArea
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "users")
class User(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false, unique = true, updatable = false)
    val authId: String,
    @Column(nullable = false)
    var username: String,
    @Column(nullable = false)
    var firstname: String,
    @Column(nullable = false)
    var lastname: String,
    @Column(nullable = false)
    var workingArea: WorkingArea,
)
