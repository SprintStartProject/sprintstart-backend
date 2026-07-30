package com.sprintstart.sprintstartbackend.connectors.jira.model.entity

import com.sprintstart.sprintstartbackend.shared.crypto.SymmetricEncryptedStringConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "jira_credentials")
internal class JiraCredential(
    @Id
    var id: JiraCredentialsId,
    @Convert(converter = SymmetricEncryptedStringConverter::class)
    @Column(name = "auth_token", nullable = false, columnDefinition = "TEXT")
    var authToken: String,
)

@Embeddable
internal class JiraCredentialsId(
    @Column(name = "user_email")
    var userEmail: String,
    @Column(nullable = false)
    var name: String,
)
