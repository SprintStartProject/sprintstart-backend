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

/**
 * Persists one user-named Notion PAT together with its validated remote identity.
 *
 * The token is encrypted through [SymmetricEncryptedStringConverter] and must never be exposed by
 * API mappers or logs. Workspace metadata is nullable because personal-token identity responses do
 * not always provide bot workspace fields.
 */
@Entity
@Table(name = "notion_credentials")
internal class NotionCredential(
    @EmbeddedId
    var id: NotionCredentialId,
    @Convert(converter = SymmetricEncryptedStringConverter::class)
    @Column(name = "token", nullable = false, columnDefinition = "TEXT")
    var token: String,
    @Column(name = "workspace_id")
    var workspaceId: String? = null,
    @Column(name = "workspace_name", length = 2000)
    var workspaceName: String? = null,
    @Column(name = "token_owner_id")
    var tokenOwnerId: String? = null,
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

/** Identifies a credential by its owning backend user and user-chosen name. */
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
