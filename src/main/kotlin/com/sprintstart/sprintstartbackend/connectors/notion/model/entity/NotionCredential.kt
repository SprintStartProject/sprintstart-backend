package com.sprintstart.sprintstartbackend.connectors.notion.model.entity

import com.sprintstart.sprintstartbackend.shared.crypto.SymmetricEncryptedStringConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

@Entity
@Table(name = "notion_credentials")
internal class NotionCredential(
    @EmbeddedId
    var id: NotionCredentialId,
    @Convert(converter = SymmetricEncryptedStringConverter::class)
    @Column(name = "token", nullable = false, columnDefinition = "TEXT")
    var token: String,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
) {
    @PrePersist
    fun recordCreationTime() {
        updatedAt = Instant.now()
    }

    @PreUpdate
    fun recordUpdateTime() {
        updatedAt = Instant.now()
    }

    override fun toString(): String {
        return "NotionCredential(authId=${id.authId}, name=${id.name}, token=<redacted>)"
    }
}

@Embeddable
internal data class NotionCredentialId(
    @Column(name = "auth_id", nullable = false)
    var authId: String,
    @Column(name = "name", nullable = false)
    var name: String,
) : Serializable {
    private companion object {
        const val serialVersionUID: Long = 1L
    }
}
