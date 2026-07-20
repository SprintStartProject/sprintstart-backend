package com.sprintstart.sprintstartbackend.connectors.overview.models

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "connector_configurations")
class ConnectorConfiguration(
    @Id
    var id: String,
    @Column(nullable = false)
    var enabled: Boolean = false,
    @Column(name = "first_configured_at")
    var firstConfiguredAt: Instant? = null,
    @Column(name = "last_configured_at")
    var lastConfiguredAt: Instant? = null,
)
