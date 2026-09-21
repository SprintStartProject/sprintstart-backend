package com.sprintstart.sprintstartbackend.connectors.bitbucket.model.entity

import com.sprintstart.sprintstartbackend.connectors.ConnectionState
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * One Bitbucket repository connected to the application.
 */
@Entity
@Table(name = "bitbucket_repositories")
class BitbucketConnection(
    @Id
    var id: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    var workspace: String,
    @Column(nullable = false)
    var slug: String,
    @Column(name = "credential_auth_id", nullable = false)
    var credentialAuthId: String,
    @Column(name = "credential_name", nullable = false)
    var credentialName: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "connection_state", nullable = false)
    var connectionState: ConnectionState = ConnectionState.UP_TO_DATE,
    @Column(name = "source_enabled", nullable = false)
    var sourceEnabled: Boolean = true,
)
