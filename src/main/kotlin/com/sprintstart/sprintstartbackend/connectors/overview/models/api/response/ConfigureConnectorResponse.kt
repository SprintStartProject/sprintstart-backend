package com.sprintstart.sprintstartbackend.connectors.overview.models.api.response

import com.sprintstart.sprintstartbackend.connectors.overview.models.ConnectorConfiguration
import java.time.Instant

/**
 * The response for [ConfigureConnectorRequest].
 *
 * @param id The connector id.
 * @param enabled The new connector status.
 */
data class ConfigureConnectorResponse(
    val id: String,
    val enabled: Boolean,
    val firstConfiguredAt: Instant?,
    val lastConfiguredAt: Instant?,
)

fun ConnectorConfiguration.toConfigureConnectorResponse() =
    ConfigureConnectorResponse(
        id = this.id,
        enabled = this.enabled,
        firstConfiguredAt = this.firstConfiguredAt,
        lastConfiguredAt = this.lastConfiguredAt,
    )
