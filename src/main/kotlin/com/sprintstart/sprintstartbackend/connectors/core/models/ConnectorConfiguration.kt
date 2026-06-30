package com.sprintstart.sprintstartbackend.connectors.core.models

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "connector_configurations")
class ConnectorConfiguration(
    @Id
    var id: String,
    @Column(nullable = false)
    var enabled: Boolean = false,
)
