package com.sprintstart.sprintstartbackend.connectors.core.models.api.request

/**
 * Request for enabling/disabling a connector
 *
 * @param enabled The status to set the connector to. Either enabled or disabled.
 */
data class ConfigureConnectorRequest(
    val enabled: Boolean,
)
