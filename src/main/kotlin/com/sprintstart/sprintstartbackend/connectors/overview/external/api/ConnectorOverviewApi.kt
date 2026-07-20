package com.sprintstart.sprintstartbackend.connectors.overview.external.api

import com.sprintstart.sprintstartbackend.connectors.overview.external.models.ConnectorDto

interface ConnectorOverviewApi {
    /**
     * Retrieves a list of all existing connectors with their associated details.
     *
     * @return A list of ConnectorDto instances, where each instance represents a connector and holds
     * information such as its id, name, status, and configuration timestamps.
     */
    fun findAllConnectors(): List<ConnectorDto>
}
