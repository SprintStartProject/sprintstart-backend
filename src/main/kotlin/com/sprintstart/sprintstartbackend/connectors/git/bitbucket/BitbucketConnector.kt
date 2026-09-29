package com.sprintstart.sprintstartbackend.connectors.git.bitbucket

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.BitbucketConnectionService
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.utils.bitbucketRepositoryUrl
import com.sprintstart.sprintstartbackend.connectors.overview.models.ConnectorSource
import com.sprintstart.sprintstartbackend.connectors.overview.models.IConnector
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Registers the Bitbucket connector with the connector overview.
 *
 * A connection maps to `ConnectorSource(id = "$workspace/$slug", name = slug,
 * url = <browser url>, enabled = sourceEnabled)`, mirroring the GitHub connector. The URL is built
 * by the same helper the ingestion status view uses, so both report the same repository address.
 */
@Component
internal class BitbucketConnector(
    private val service: BitbucketConnectionService,
) : IConnector {
    override val id: String
        get() = "bitbucket"
    override val displayName: String
        get() = "Bitbucket Repository Connector"

    override fun getSources(): List<ConnectorSource> = service.getSources().map {
        it.toConnectorSource()
    }

    override fun getSources(projectId: UUID): List<ConnectorSource> = service.getSources(projectId).map {
        it.toConnectorSource()
    }

    override fun patchSource(source: ConnectorSource, newStatus: Boolean) =
        service.patchSource(source, newStatus)

    private fun BitbucketConnection.toConnectorSource() =
        ConnectorSource(
            id = "$workspace/$slug",
            name = slug,
            url = bitbucketRepositoryUrl(workspace, slug),
            enabled = sourceEnabled,
        )
}
