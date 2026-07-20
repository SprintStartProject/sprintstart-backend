package com.sprintstart.sprintstartbackend.connectors.overview.external.models

import java.time.Instant

/**
 * The DTO holding all available information about connectors.
 *
 * @param id The id of the connector.
 * @param name The display name of the connector.
 * @param enabled Whether the connector is currently enabled or not.
 * @param firstConfiguredAt A timestamp of the first time this connector was configured (enabled).
 * @param lastConfiguredAt A timestamp of the last time the status (enabled) of this connector changed.
 */
data class ConnectorDto(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val firstConfiguredAt: Instant?,
    val lastConfiguredAt: Instant?,
)
