package com.sprintstart.sprintstartbackend.chat.models

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

/**
 * A conversation, owned by one user and scoped to one project.
 *
 * [projectId] is nullable only because chats created before project scoping existed cannot be
 * assigned a project after the fact — there is no honest answer to which project they belonged to.
 * Those chats stay readable but can no longer be prompted, since the AI service requires a project
 * scope. Every chat created from now on has one.
 */
@Entity
@Table(name = "chats")
internal data class Chat(
    @Id
    var id: UUID = UUID.randomUUID(),
    var title: String = "",
    @Column("user_id", nullable = false)
    var userId: UUID,
    @Column("created_at")
    var createdAt: OffsetDateTime,
    // Last and defaulted so existing positional constructor calls keep working; the column order
    // in the table is irrelevant.
    @Column("project_id")
    var projectId: UUID? = null,
)
