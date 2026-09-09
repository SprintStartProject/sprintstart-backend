package com.sprintstart.sprintstartbackend.connectors.atlassian.model.entity

import com.sprintstart.sprintstartbackend.shared.crypto.SymmetricEncryptedStringConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * The table is still named `jira_credentials` for historical reasons: Jira and Confluence share one
 * Atlassian API token, and renaming the physical table would require a manual, riskier migration
 * since Hibernate's `ddl-auto: update` never renames tables on its own.
 */
@Entity
@Table(name = "jira_credentials")
internal class AtlassianCredential(
    @Id
    var id: AtlassianCredentialId,
    @Convert(converter = SymmetricEncryptedStringConverter::class)
    @Column(name = "auth_token", nullable = false, columnDefinition = "TEXT")
    var authToken: String,
    @Column(name = "user_email", nullable = false)
    var userEmail: String,
)

@Embeddable
internal class AtlassianCredentialId(
    @Column(name = "auth_id", nullable = false)
    var authId: String,
    @Column(nullable = false)
    var name: String,
)
