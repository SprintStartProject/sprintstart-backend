package com.sprintstart.sprintstartbackend.chat.models

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

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
)
