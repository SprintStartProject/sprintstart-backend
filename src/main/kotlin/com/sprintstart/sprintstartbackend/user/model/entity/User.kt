package com.sprintstart.sprintstartbackend.user.model.entity

import com.sprintstart.sprintstartbackend.user.api.enums.Roles
import com.sprintstart.sprintstartbackend.user.api.enums.WorkingAreas
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "users")
class User (

    @Id
    val id: UUID = UUID.randomUUID(),

//  Once we have auth, this is where the authSubject/authId should go

    @Column(nullable = false)
    var username: String,

    @Column(nullable = false)
    var firstname: String,

    @Column(nullable = false)
    var lastname: String,

//  The Roles have a default value because they are assigned separately using the selection wizard
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var primaryRole: Roles = Roles.NO_ROLE,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var secondaryRole: Roles = Roles.NO_ROLE,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var workingArea: WorkingAreas
)