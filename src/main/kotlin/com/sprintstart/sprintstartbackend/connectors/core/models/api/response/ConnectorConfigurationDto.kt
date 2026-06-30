package com.sprintstart.sprintstartbackend.connectors.core.models.api.response

import com.sprintstart.sprintstartbackend.connectors.core.models.IConnector
import java.time.Instant

/**
 * The DTO holding all available information about connectors.
 *
 * @param id The id of the connector.
 * @param name The display name of the connector.
 * @param enabled Whether the connector is currently enabled or not.
 * @param configuredAt A timestamp indicating when the connector has been configured.
 */
data class ConnectorConfigurationDto(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val configuredAt: Instant,
)

fun IConnector.toDto() =
    ConnectorConfigurationDto(
        id = this.id,
        name = this.displayName,
        enabled = this.enabled,
        configuredAt = this.configuredAt,
    )
