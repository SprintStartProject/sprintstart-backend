package com.sprintstart.sprintstartbackend.connectors.overview.models.exceptions

data class ConnectorNotFoundException(
    private val msg: String,
) : RuntimeException(msg)
