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
    @Column @Id val id: UUID = UUID.randomUUID(),
    @Column var title: String = "",
    @Column("user_id", nullable = false) val userId: UUID,
    @Column("created_at") val createdAt: OffsetDateTime,
)
