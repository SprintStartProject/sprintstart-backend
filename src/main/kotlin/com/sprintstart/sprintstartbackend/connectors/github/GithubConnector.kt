package com.sprintstart.sprintstartbackend.connectors.github

import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.github.service.GithubConnectorService
import com.sprintstart.sprintstartbackend.connectors.overview.models.ConnectorSource
import com.sprintstart.sprintstartbackend.connectors.overview.models.IConnector
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class GithubConnector(
    private val service: GithubConnectorService,
) : IConnector {
    override val id: String
        get() = "github"
    override val displayName: String
        get() = "Github Repository Connector"

    override fun getSources(): List<ConnectorSource> =
        service.getAllSources().map { it.toConnectorSource() }

    override fun getSources(projectId: UUID): List<ConnectorSource> =
        service.getSourcesByProjectId(projectId).map { it.toConnectorSource() }

    override fun patchSource(source: ConnectorSource, newStatus: Boolean) =
        service.patchSource(source, newStatus)

    private fun GithubRepositoryConnection.toConnectorSource() =
        ConnectorSource(
            id = "$owner/$name",
            name = name,
            url = "https://github.com/$owner/$name",
            enabled = sourceEnabled,
        )
}
