package com.sprintstart.sprintstartbackend.connectors.core.models.exceptions

data class ConnectorNotFoundException(
    private val msg: String,
) : RuntimeException(msg)
