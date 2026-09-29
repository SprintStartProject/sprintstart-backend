package com.sprintstart.sprintstartbackend.connectors.git.bitbucket

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.BitbucketConnectionService
import com.sprintstart.sprintstartbackend.connectors.overview.models.ConnectorSource
import com.sprintstart.sprintstartbackend.connectors.overview.models.IConnector
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Registers the Bitbucket connector with the connector overview.
 *
 * Deliberately reports no sources yet: source lookup needs the connection service that this
 * connector's module still grows, and returning an empty list is the honest answer until then —
 * the overview treats a connector without sources as connectable but empty rather than broken.
 *
 * Once sources exist, a connection maps to `ConnectorSource(id = "$workspace/$slug",
 * url = "https://bitbucket.org/$workspace/$slug", enabled = sourceEnabled)`, mirroring the
 * GitHub connector.
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
            url = "https://bitbucket.org/$workspace/$slug", // TODO:
            enabled = sourceEnabled,
        )
}
