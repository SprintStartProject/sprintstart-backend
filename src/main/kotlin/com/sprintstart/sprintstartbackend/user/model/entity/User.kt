package com.sprintstart.sprintstartbackend.user.model.entity

import com.sprintstart.sprintstartbackend.user.model.Roles
import com.sprintstart.sprintstartbackend.user.model.WorkingAreas
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "users")
class User {

    @Id
    val id: UUID = UUID.randomUUID()

//  Once we have auth, this is where the authSubject/authId should go

    @Column(nullable = false)
    var username: String = ""

    @Column(nullable = false)
    var firstname: String = ""

    @Column(nullable = false)
    var lastname: String = ""

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var primaryRole: Roles = Roles.NO_ROLE

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var secondaryRole: Roles = Roles.NO_ROLE

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var workingArea: WorkingAreas = WorkingAreas.NO_WORKING_AREA
}