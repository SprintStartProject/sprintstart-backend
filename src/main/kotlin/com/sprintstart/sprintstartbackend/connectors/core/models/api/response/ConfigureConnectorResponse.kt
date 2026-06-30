package com.sprintstart.sprintstartbackend.connectors.core.models.api.response

import com.sprintstart.sprintstartbackend.connectors.core.models.ConnectorConfiguration
import com.sprintstart.sprintstartbackend.connectors.core.models.api.request.ConfigureConnectorRequest

/**
 * The response for [ConfigureConnectorRequest].
 *
 * @param id The connector id.
 * @param enabled The new connector status.
 */
data class ConfigureConnectorResponse(
    val id: String,
    val enabled: Boolean,
)

fun ConnectorConfiguration.toConfigureConnectorResponse() =
    ConfigureConnectorResponse(
        id = this.id,
        enabled = this.enabled,
    )
