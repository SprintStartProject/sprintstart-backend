package com.sprintstart.sprintstartbackend.connectors.core.repository

import com.sprintstart.sprintstartbackend.connectors.core.models.ConnectorConfiguration
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ConnectorConfigurationRepository : JpaRepository<ConnectorConfiguration, String>
