package com.sprintstart.sprintstartbackend.connectors.jira

import com.sprintstart.sprintstartbackend.connectors.jira.model.api.response.JiraInstanceDto
import com.sprintstart.sprintstartbackend.connectors.jira.service.JiraService
import com.sprintstart.sprintstartbackend.connectors.overview.models.ConnectorSource
import com.sprintstart.sprintstartbackend.connectors.overview.models.IConnector
import org.springframework.stereotype.Component
import java.util.UUID

@Component
internal class JiraConnector(
    private val service: JiraService,
) : IConnector {
    override val id: String
        get() = "jira"
    override val displayName: String
        get() = "Jira Connector"

    override fun getSources(): List<ConnectorSource> =
        service.getInstances().map { it.toConnectorSource() }

    override fun getSources(projectId: UUID): List<ConnectorSource> =
        service.getInstances(projectId).map { it.toConnectorSource() }

    override fun patchSource(
        source: ConnectorSource,
        newStatus: Boolean,
    ) = service.patchInstance(source.id, newStatus)
}

private fun JiraInstanceDto.toConnectorSource(): ConnectorSource {
    return ConnectorSource(
        id = this.instanceUrl,
        name = this.displayName,
        url = this.instanceUrl,
        enabled = this.sourceEnabled,
    )
}
