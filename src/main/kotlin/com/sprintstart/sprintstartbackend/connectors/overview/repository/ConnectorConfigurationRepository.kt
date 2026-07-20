package com.sprintstart.sprintstartbackend.connectors.overview.repository

import com.sprintstart.sprintstartbackend.connectors.overview.models.ConnectorConfiguration
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ConnectorConfigurationRepository : JpaRepository<ConnectorConfiguration, String>
