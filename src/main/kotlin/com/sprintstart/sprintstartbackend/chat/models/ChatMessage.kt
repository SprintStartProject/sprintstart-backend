package com.sprintstart.sprintstartbackend.chat.models

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "chat_messages")
internal data class ChatMessage(
    @Column @Id val id: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING) @Column(nullable = false) val role: ChatRole,
    @JoinColumn("chat_id", nullable = false) @ManyToOne val chat: Chat,
    @Column(nullable = false) val content: String,
    @Column("created_at") val createdAt: OffsetDateTime,
)
