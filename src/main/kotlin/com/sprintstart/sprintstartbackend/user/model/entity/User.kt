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
    var experience: String? = null,
)
