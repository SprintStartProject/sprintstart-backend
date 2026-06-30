package com.sprintstart.sprintstartbackend.connectors.github

import com.sprintstart.sprintstartbackend.connectors.core.models.ConnectorSource
import com.sprintstart.sprintstartbackend.connectors.core.models.IConnector
import com.sprintstart.sprintstartbackend.connectors.github.models.parse
import com.sprintstart.sprintstartbackend.connectors.github.service.GithubConnectorService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "sprintstart.github", name = ["token"])
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
                id = "${it.owner}/${it.name}}",
                name = it.name,
                url = "https://github.com/${it.owner}/${it.name}",
                status = it.sourceStatus.parse(),
            )
        }
}
