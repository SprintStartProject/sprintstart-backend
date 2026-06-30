package com.sprintstart.sprintstartbackend.connectors.core.service

import com.sprintstart.sprintstartbackend.connectors.core.models.IConnector
import com.sprintstart.sprintstartbackend.connectors.core.models.api.request.ConfigureConnectorRequest
import com.sprintstart.sprintstartbackend.connectors.core.models.api.response.ConfigureConnectorResponse
import com.sprintstart.sprintstartbackend.connectors.core.models.api.response.GetSourcesOfConnectorResponse
import com.sprintstart.sprintstartbackend.connectors.core.models.api.response.toConfigureConnectorResponse
import com.sprintstart.sprintstartbackend.connectors.core.models.exceptions.ConnectorConfigurationNotFoundException
import com.sprintstart.sprintstartbackend.connectors.core.models.exceptions.ConnectorNotFoundException
import com.sprintstart.sprintstartbackend.connectors.core.repository.ConnectorConfigurationRepository
import org.springframework.stereotype.Service

@Service
class ConnectorConfigurationService(
    private val repository: ConnectorConfigurationRepository,
    private val connectors: List<IConnector>,
) {
    fun configure(connectorId: String, request: ConfigureConnectorRequest): ConfigureConnectorResponse {
        val configuration = repository.findById(connectorId).orElseThrow {
            ConnectorConfigurationNotFoundException(
                "Could not find configuration for connector $connectorId",
            )
        }

        configuration.enabled = request.enabled
        return repository.save(configuration).toConfigureConnectorResponse()
    }

    fun getSourcesOfConnector(connectorId: String): GetSourcesOfConnectorResponse {
        val connector = connectors.stream().filter { it.id == connectorId }.findFirst().orElseThrow {
            ConnectorNotFoundException("Unable to load up connector with id $connectorId")
        }

        return GetSourcesOfConnectorResponse(
            connectorId = connectorId,
            sources = connector.getSources(),
        )
    }

    fun patchSourcesOfConnector(connectionId: String) {}
}
