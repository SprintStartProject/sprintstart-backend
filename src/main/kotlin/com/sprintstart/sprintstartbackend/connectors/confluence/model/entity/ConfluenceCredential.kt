package com.sprintstart.sprintstartbackend.connectors.confluence.model.entity

import com.sprintstart.sprintstartbackend.shared.crypto.SymmetricEncryptedStringConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** Stores the Basic-auth credential owned by one Confluence space connection. */
@Entity
@Table(name = "confluence_credentials")
internal class ConfluenceCredential(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(name = "user_email", nullable = false)
    var email: String,
    @Convert(converter = SymmetricEncryptedStringConverter::class)
    @Column(name = "api_token", nullable = false, columnDefinition = "TEXT")
    var apiToken: String,
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "connection_id",
        nullable = false,
        unique = true,
        foreignKey = ForeignKey(name = "fk_confluence_credential_connection"),
    )
    var connection: ConfluenceSpaceConnection,
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),
) {
    override fun toString(): String {
        return "ConfluenceCredential(id=$id, email=<redacted>, apiToken=<redacted>)"
    }
}
