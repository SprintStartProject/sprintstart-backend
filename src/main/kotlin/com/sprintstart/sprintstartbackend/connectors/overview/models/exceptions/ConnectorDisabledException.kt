package com.sprintstart.sprintstartbackend.connectors.overview.models.exceptions

data class ConnectorDisabledException(
    private val msg: String,
) : RuntimeException(msg)
