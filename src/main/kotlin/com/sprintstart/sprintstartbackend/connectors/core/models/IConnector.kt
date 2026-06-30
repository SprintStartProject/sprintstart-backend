package com.sprintstart.sprintstartbackend.connectors.core.models

interface IConnector {
    val id: String
    val displayName: String

    fun getSources(): List<ConnectorSource>
}

data class ConnectorSource(
    val id: String,
    val name: String,
    val url: String,
    val status: String,
)
