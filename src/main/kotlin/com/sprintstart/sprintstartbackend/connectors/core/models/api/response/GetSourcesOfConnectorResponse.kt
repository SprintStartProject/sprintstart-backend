package com.sprintstart.sprintstartbackend.connectors.core.models.api.response

import com.sprintstart.sprintstartbackend.connectors.core.models.ConnectorSource

/**
 * Response for retrieving all sources of a connector.
 *
 * @param connectorId The id of the connector to collect sources from.
 * @param sources The actual sources.
 */
data class GetSourcesOfConnectorResponse(
    val connectorId: String,
    val sources: List<ConnectorSource>,
)
