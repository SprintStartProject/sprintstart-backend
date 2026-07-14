package com.sprintstart.sprintstartbackend.chat.models

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "citations")
internal data class Citation(
    @Id
    var id: UUID = UUID.randomUUID(),
    @ManyToOne
    @JoinColumn(name = "message_id")
    var message: ChatMessage,
    var artifactId: UUID,
    var filename: String,
    var sourceUrl: String? = null,
    var startLine: Int? = null,
    var startPage: Int? = null,
)
