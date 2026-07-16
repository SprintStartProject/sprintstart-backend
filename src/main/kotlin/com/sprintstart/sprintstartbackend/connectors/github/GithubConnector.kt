package com.sprintstart.sprintstartbackend.connectors.github

import com.sprintstart.sprintstartbackend.connectors.github.service.GithubConnectorService
import com.sprintstart.sprintstartbackend.connectors.overview.models.ConnectorSource
import com.sprintstart.sprintstartbackend.connectors.overview.models.IConnector
import org.springframework.stereotype.Component

@Component
class GithubConnector(
    private val service: GithubConnectorService,
) : IConnector {
    override val id: String
        get() = "github"
    override val displayName: String
        get() = "Github Repository Connector"

    override fun getSources(): List<ConnectorSource> =
        service.getAllSources().map {
            ConnectorSource(
                id = "${it.owner}/${it.name}",
                name = it.name,
                url = "https://github.com/${it.owner}/${it.name}",
                enabled = it.sourceEnabled,
            )
        }

    override fun patchSource(source: ConnectorSource, newStatus: Boolean) =
        service.patchSource(source, newStatus)
}
