package com.sprintstart.sprintstartbackend.user.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

/**
 * A project owning sources, artifacts and member assignments.
 *
 * The optional [manager] is the single project manager responsible for this project. Modelling it
 * as a nullable foreign key makes "at most one manager per project" structurally impossible to
 * violate, while a user may manage any number of projects. Membership is a separate concern and is
 * held by [ProjectUserAssignment]; managing a project does not require a membership row, although
 * one is kept in sync so the project still shows up in member-facing project lists.
 */
@Entity
@Table(
    name = "sprintstart_projects",
    indexes = [Index(name = "idx_projects_manager_user", columnList = "manager_user_id")],
)
class Project(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false, unique = true)
    var name: String,
    @Column(nullable = true)
    var description: String? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "manager_user_id",
        nullable = true,
        foreignKey = ForeignKey(name = "fk_project_manager_user"),
    )
    var manager: User? = null,
)
