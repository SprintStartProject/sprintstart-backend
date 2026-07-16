package com.sprintstart.sprintstartbackend.connectors.overview.service

import com.sprintstart.sprintstartbackend.connectors.overview.SourceClient
import com.sprintstart.sprintstartbackend.connectors.overview.models.ConnectorConfiguration
import com.sprintstart.sprintstartbackend.connectors.overview.models.IConnector
import com.sprintstart.sprintstartbackend.connectors.overview.models.api.request.ConfigureConnectorRequest
import com.sprintstart.sprintstartbackend.connectors.overview.models.api.request.PatchSourcesRequest
import com.sprintstart.sprintstartbackend.connectors.overview.models.api.response.ConfigureConnectorResponse
import com.sprintstart.sprintstartbackend.connectors.overview.models.api.response.ConnectorDto
import com.sprintstart.sprintstartbackend.connectors.overview.models.api.response.GetSourcesOfConnectorResponse
import com.sprintstart.sprintstartbackend.connectors.overview.models.api.response.PatchSourcesOfConnectorResponse
import com.sprintstart.sprintstartbackend.connectors.overview.models.api.response.PatchedSource
import com.sprintstart.sprintstartbackend.connectors.overview.models.api.response.toConfigureConnectorResponse
import com.sprintstart.sprintstartbackend.connectors.overview.models.exceptions.ConnectorNotFoundException
import com.sprintstart.sprintstartbackend.connectors.overview.repository.ConnectorConfigurationRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class ConnectorConfigurationService(
    private val repository: ConnectorConfigurationRepository,
    private val connectors: List<IConnector>,
    private val sourceClient: SourceClient,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Automatically synchronizes static (software-set) connectors with connectors stored in the db.
     *
     * The metadata of connectors is owned partly by this overview, and partly by the actual connector.
     * The connector module, implementing `IConnector` holds all static data, `id`, `displayName`, stuff like that,
     * while the connector configuration stored in the db keeps track of all dynamic data, eg. data that can be changed
     * from the outside (`enabled`, `lastConfiguredAt`, ...).
     *
     * This function, paired with the `@PostConstruct` annotation, at Spring Bean creation time, synchronizes this
     * distributed state so that changes in either one don't break the state.
     */
    @PostConstruct
    fun ensureAllConnectorsHaveConfig() {
        logger.info("Synchronizing connectors with db")
        val savedConnectorIds = repository.findAll().map { it.id }.toSet()
        val connectorIds = connectors.map { it.id }.toSet()

        val insertables = connectorIds - savedConnectorIds
        val deletables = savedConnectorIds - connectorIds

        // Insert all connectors that are defined via the `IConnector` interface but have no db entry
        insertables.forEach { connectorId ->
            repository.findById(connectorId).orElseGet {
                logger.info("Found missing connector db entry '$connectorId' - inserting")
                repository.save(ConnectorConfiguration(id = connectorId))
            }
        }

        // Delete all connector db entries that are not defined via the `IConnector` interface anymore
        deletables.forEach { connectorId ->
            logger.info("Found deprecated connector db entry '$connectorId' - deleting")
            repository.deleteById(connectorId)
        }
    }

    /**
     * Retrieves a list of all available connectors, no matter their state.
     *
     * @return a list of all defined connectors.
     * @throws ConnectorNotFoundException if the internal state is corrupted.
     */
    @Tracked("Retrieving all connectors")
    @Transactional(readOnly = true)
    fun findAllConnectors(): List<ConnectorDto> {
        return repository.findAll().map { config ->
            val iConnector = connectors.find { it.id == config.id }
                ?: throw ConnectorNotFoundException(
                    "Connector db state is desynced with soft state. Should not happen.",
                )

            ConnectorDto(
                config.id,
                iConnector.displayName,
                config.enabled,
                config.firstConfiguredAt,
                config.lastConfiguredAt,
            )
        }
    }

    /**
     * Configures a connector - e.g. enables or disables it globally.
     *
     * @param connectorId The id of the connector to configure.
     * @param request [ConfigureConnectorRequest] contains the new state of the connector.
     * @return the updated connector.
     */
    @Tracked("Configuring connector")
    suspend fun configure(connectorId: String, request: ConfigureConnectorRequest): ConfigureConnectorResponse {
        connectors.find { it.id == connectorId }
            ?: throw ConnectorNotFoundException("No connector with id $connectorId")
        val configuration = withContext(Dispatchers.IO) {
            repository.findById(connectorId)
        }.get()

        configuration.enabled = request.enabled
        configuration.lastConfiguredAt = Instant.now()

        if (configuration.firstConfiguredAt == null) {
            configuration.firstConfiguredAt = Instant.now()
        }

        sourceClient.configureConnector(connectorId, request.enabled)

        return withContext(Dispatchers.IO) {
            repository.save(configuration)
        }.toConfigureConnectorResponse()
    }

    /**
     * Retrieves all sources of a given connector.
     *
     * @param connectorId The id of the connector to retrieve all sources of.
     * @return [GetSourcesOfConnectorResponse] all sources of the given connector.
     * @throws ConnectorNotFoundException if no connector with the given id could be found.
     */
    @Tracked("Retrieving all sources of given connector")
    @Transactional(readOnly = true)
    fun getSourcesOfConnector(connectorId: String): GetSourcesOfConnectorResponse {
        val connector = connectors.stream().filter { it.id == connectorId }.findFirst().orElseThrow {
            ConnectorNotFoundException("Unable to load up connector with id $connectorId")
        }

        return GetSourcesOfConnectorResponse(
            connectorId = connectorId,
            sources = connector.getSources(),
        )
    }

    /**
     * Patches a list of sources of a given connector.
     *
     * Patching here means changing the state of a source.
     * Also, the change is synchronized with the AI system.
     *
     * @param connectorId The id of the connector to patch sources of.
     * @param request [PatchSourcesRequest] contains information on the sources to patch (which and how).
     * @return [PatchSourcesOfConnectorResponse] the updated sources.
     * @throws ConnectorNotFoundException if no connector with the given id could be found.
     */
    @Tracked("Patching requested sources statuses of connector")
    suspend fun patchSourcesIfConnectorExists(
        connectorId: String,
        request: PatchSourcesRequest,
    ): PatchSourcesOfConnectorResponse {
        val connector = connectors.find { it.id == connectorId }
            ?: throw ConnectorNotFoundException("Unable to find connector with id $connectorId")
        val idStatusesMap = request.sources.associateBy({ it.sourceId }, { it.enabled })

        return patchSources(connector, idStatusesMap)
    }

    /**
     * Patches a list of sources of a given connector.
     *
     * Patching in this context means changing the status of a source.
     *
     * @param connector The connector to patch sources of.
     * @param requestedSources A map, sourceId -> newStatus, representing the requested changes.
     * @return the updated sources
     */
    private suspend fun patchSources(
        connector: IConnector,
        requestedSources: Map<String, Boolean>,
    ): PatchSourcesOfConnectorResponse {
        val result = connector
            .getSources()
            .stream()
            .filter { source ->
                requestedSources.containsKey(source.id) && requestedSources[source.id] != null
            }.map { source ->
                connector.patchSource(source, requestedSources[source.id]!!)
                PatchedSource(source.id, source.name, source.url, requestedSources[source.id]!!)
            }.toList()

        sourceClient.patchSources(connector.id, requestedSources)

        return PatchSourcesOfConnectorResponse(
            connectorId = connector.id,
            sources = result,
        )
    }
}
