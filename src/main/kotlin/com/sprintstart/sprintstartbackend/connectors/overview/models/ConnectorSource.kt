package com.sprintstart.sprintstartbackend.connectors.overview.models

data class ConnectorSource(
    val id: String,
    val name: String,
    val url: String,
    var enabled: Boolean,
)
