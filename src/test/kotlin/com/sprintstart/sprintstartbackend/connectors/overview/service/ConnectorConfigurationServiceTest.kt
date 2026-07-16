package com.sprintstart.sprintstartbackend.connectors.overview.service

import com.sprintstart.sprintstartbackend.connectors.overview.SourceClient
import com.sprintstart.sprintstartbackend.connectors.overview.models.ConnectorConfiguration
import com.sprintstart.sprintstartbackend.connectors.overview.models.ConnectorSource
import com.sprintstart.sprintstartbackend.connectors.overview.models.IConnector
import com.sprintstart.sprintstartbackend.connectors.overview.models.api.request.ConfigureConnectorRequest
import com.sprintstart.sprintstartbackend.connectors.overview.models.api.request.PatchSourceRequest
import com.sprintstart.sprintstartbackend.connectors.overview.models.api.request.PatchSourcesRequest
import com.sprintstart.sprintstartbackend.connectors.overview.models.exceptions.ConnectorNotFoundException
import com.sprintstart.sprintstartbackend.connectors.overview.repository.ConnectorConfigurationRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Optional
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectorConfigurationServiceTest {
    private val repository = mockk<ConnectorConfigurationRepository>()
    private val sourceClient = mockk<SourceClient>()

    private val githubConnector = mockk<IConnector>()
    private val connectors = listOf(githubConnector)

    private lateinit var service: ConnectorConfigurationService

    @BeforeEach
    fun setUp() {
        every { githubConnector.id } returns "github"
        every { githubConnector.displayName } returns "Github Repository Connector"

        service = ConnectorConfigurationService(repository, connectors, sourceClient)
    }

    @Nested
    inner class EnsureAllConnectorsHaveConfig {
        @Test
        fun `should do nothing when connectors are in sync`() {
            every { repository.findAll() } returns listOf(ConnectorConfiguration(id = "github"))

            service.ensureAllConnectorsHaveConfig()

            verify(exactly = 0) { repository.save(any()) }
            verify(exactly = 0) { repository.deleteById(any()) }
        }

        @Test
        fun `should insert missing connector config`() {
            every { repository.findAll() } returns emptyList()
            every { repository.findById("github") } returns Optional.empty()
            every { repository.save(any()) } returns mockk()

            service.ensureAllConnectorsHaveConfig()

            verify { repository.save(match { it.id == "github" }) }
        }

        @Test
        fun `should not insert when config already exists in db via findById race`() {
            val existingConfig = ConnectorConfiguration(id = "github")
            every { repository.findAll() } returns emptyList()
            every { repository.findById("github") } returns Optional.of(existingConfig)

            service.ensureAllConnectorsHaveConfig()

            verify(exactly = 0) { repository.save(any()) }
        }

        @Test
        fun `should delete orphaned connector config when insertables empty`() {
            every { repository.findAll() } returns listOf(
                ConnectorConfiguration(id = "github"),
                ConnectorConfiguration(id = "deprecated-connector"),
            )
            every { repository.deleteById(any()) } just runs

            service.ensureAllConnectorsHaveConfig()

            verify { repository.deleteById("deprecated-connector") }
            verify(exactly = 0) { repository.save(any()) }
        }

        @Test
        fun `should insert missing connector and delete orphaned in same sync`() {
            every { repository.findAll() } returns listOf(
                ConnectorConfiguration(id = "orphan"),
            )
            every { repository.findById("github") } returns Optional.empty()
            every { repository.save(any()) } returns mockk()
            every { repository.deleteById(any()) } just runs

            service.ensureAllConnectorsHaveConfig()

            verify { repository.save(match { it.id == "github" }) }
            verify { repository.deleteById("orphan") }
        }
    }

    @Nested
    inner class FindAllConnectors {
        @Test
        fun `should return list of ConnectorDto`() {
            val config = ConnectorConfiguration(
                id = "github",
                enabled = true,
            )
            every { repository.findAll() } returns listOf(config)

            val result = service.findAllConnectors()

            assertThat(result).hasSize(1)
            assertThat(result[0].id).isEqualTo("github")
            assertThat(result[0].name).isEqualTo("Github Repository Connector")
            assertThat(result[0].enabled).isTrue()
        }

        @Test
        fun `should return empty list when no connectors configured`() {
            every { repository.findAll() } returns emptyList()

            val result = service.findAllConnectors()

            assertThat(result).isEmpty()
        }

        @Test
        fun `should return disabled connector`() {
            val config = ConnectorConfiguration(id = "github", enabled = false)
            every { repository.findAll() } returns listOf(config)

            val result = service.findAllConnectors()

            assertThat(result).hasSize(1)
            assertThat(result[0].enabled).isFalse()
        }

        @Test
        fun `should throw ConnectorNotFoundException when config has no matching IConnector`() {
            every { repository.findAll() } returns listOf(ConnectorConfiguration(id = "unknown"))

            assertFailsWith<ConnectorNotFoundException> { service.findAllConnectors() }
        }
    }

    @Nested
    inner class Configure {
        @Test
        fun `should enable a connector`() = runTest {
            val config = ConnectorConfiguration(id = "github", enabled = false)
            val request = ConfigureConnectorRequest(enabled = true)
            every { repository.findById("github") } returns Optional.of(config)
            every { repository.save(any()) } answers { firstArg() }
            coEvery { sourceClient.configureConnector(any(), any()) } just runs

            val result = service.configure("github", request)

            assertThat(result.enabled).isTrue()
        }

        @Test
        fun `should disable a connector`() = runTest {
            val config = ConnectorConfiguration(id = "github", enabled = true)
            val request = ConfigureConnectorRequest(enabled = false)
            every { repository.findById("github") } returns Optional.of(config)
            every { repository.save(any()) } answers { firstArg() }
            coEvery { sourceClient.configureConnector(any(), any()) } just runs

            val result = service.configure("github", request)

            assertThat(result.enabled).isFalse()
        }

        @Test
        fun `should set timestamps on first configuration`() = runTest {
            val config = ConnectorConfiguration(id = "github")
            val request = ConfigureConnectorRequest(enabled = true)
            every { repository.findById("github") } returns Optional.of(config)
            every { repository.save(any()) } answers { firstArg() }
            coEvery { sourceClient.configureConnector(any(), any()) } just runs

            val result = service.configure("github", request)

            assertThat(result.firstConfiguredAt).isNotNull()
            assertThat(result.lastConfiguredAt).isNotNull()
        }

        @Test
        fun `should update lastConfiguredAt but not firstConfiguredAt on subsequent config`() = runTest {
            val now = java.time.Instant.now()
            val config = ConnectorConfiguration(
                id = "github",
                enabled = true,
                firstConfiguredAt = now,
                lastConfiguredAt = now,
            )
            val request = ConfigureConnectorRequest(enabled = false)
            every { repository.findById("github") } returns Optional.of(config)
            every { repository.save(any()) } answers { firstArg() }
            coEvery { sourceClient.configureConnector(any(), any()) } just runs

            val result = service.configure("github", request)

            assertThat(result.firstConfiguredAt).isEqualTo(now)
            assertThat(result.lastConfiguredAt).isAfter(now)
        }

        @Test
        fun `should throw ConnectorNotFoundException when connector does not exist`() = runTest {
            assertFailsWith<ConnectorNotFoundException> {
                service.configure("unknown", ConfigureConnectorRequest(enabled = true))
            }
        }

        @Test
        fun `should call sourceClient dot configureConnector when configuring`() = runTest {
            val config = ConnectorConfiguration(id = "github", enabled = false)
            val request = ConfigureConnectorRequest(enabled = true)
            every { repository.findById("github") } returns Optional.of(config)
            every { repository.save(any()) } answers { firstArg() }
            coEvery { sourceClient.configureConnector(any(), any()) } just runs

            service.configure("github", request)

            coVerify { sourceClient.configureConnector("github", true) }
        }
    }

    @Nested
    inner class GetSourcesOfConnector {
        @Test
        fun `should return sources of existing connector`() {
            val sources = listOf(
                ConnectorSource(
                    id = "owner/repo",
                    name = "repo",
                    url = "https://github.com/owner/repo",
                    enabled = true,
                ),
            )
            every { githubConnector.getSources() } returns sources

            val result = service.getSourcesOfConnector("github")

            assertThat(result.connectorId).isEqualTo("github")
            assertThat(result.sources).hasSize(1)
            assertThat(result.sources[0].id).isEqualTo("owner/repo")
            assertThat(result.sources[0].enabled).isTrue()
        }

        @Test
        fun `should return empty sources when connector has no sources`() {
            every { githubConnector.getSources() } returns emptyList()

            val result = service.getSourcesOfConnector("github")

            assertThat(result.connectorId).isEqualTo("github")
            assertThat(result.sources).isEmpty()
        }

        @Test
        fun `should throw ConnectorNotFoundException when connector not found`() {
            assertFailsWith<ConnectorNotFoundException> {
                service.getSourcesOfConnector("unknown")
            }
        }
    }

    @Nested
    inner class PatchSourcesIfConnectorExists {
        @Test
        fun `should patch sources and call sourceClient`() = runTest {
            val source = ConnectorSource(
                id = "owner/repo",
                name = "repo",
                url = "https://github.com/owner/repo",
                enabled = false,
            )
            every { githubConnector.getSources() } returns listOf(source)
            every { githubConnector.patchSource(source, true) } answers {
                firstArg<ConnectorSource>().enabled = true
            }
            coEvery { sourceClient.patchSources("github", mapOf("owner/repo" to true)) } just runs

            val request = PatchSourcesRequest(
                sources = listOf(PatchSourceRequest(sourceId = "owner/repo", enabled = true)),
            )
            val result = service.patchSourcesIfConnectorExists("github", request)

            assertThat(result.connectorId).isEqualTo("github")
            assertThat(result.sources).hasSize(1)
            assertThat(result.sources[0].id).isEqualTo("owner/repo")
            assertThat(result.sources[0].enabled).isTrue()

            verify { githubConnector.patchSource(source, true) }
            coVerify { sourceClient.patchSources("github", mapOf("owner/repo" to true)) }
        }

        @Test
        fun `should patch multiple sources`() = runTest {
            val source1 = ConnectorSource(
                id = "owner/repo1",
                name = "repo1",
                url = "https://github.com/owner/repo1",
                enabled = false,
            )
            val source2 = ConnectorSource(
                id = "owner/repo2",
                name = "repo2",
                url = "https://github.com/owner/repo2",
                enabled = true,
            )
            every { githubConnector.getSources() } returns listOf(source1, source2)
            every { githubConnector.patchSource(source1, true) } answers {
                firstArg<ConnectorSource>().enabled = true
            }
            every { githubConnector.patchSource(source2, false) } answers {
                firstArg<ConnectorSource>().enabled = false
            }
            coEvery {
                sourceClient.patchSources("github", mapOf("owner/repo1" to true, "owner/repo2" to false))
            } just runs

            val request = PatchSourcesRequest(
                sources = listOf(
                    PatchSourceRequest(sourceId = "owner/repo1", enabled = true),
                    PatchSourceRequest(sourceId = "owner/repo2", enabled = false),
                ),
            )
            val result = service.patchSourcesIfConnectorExists("github", request)

            assertThat(result.sources).hasSize(2)
            assertThat(result.sources[0].enabled).isTrue()
            assertThat(result.sources[1].enabled).isFalse()
        }

        @Test
        fun `should only patch sources that exist on the connector`() = runTest {
            val source = ConnectorSource(
                id = "owner/repo",
                name = "repo",
                url = "https://github.com/owner/repo",
                enabled = false,
            )
            every { githubConnector.getSources() } returns listOf(source)
            every { githubConnector.patchSource(source, true) } answers {
                firstArg<ConnectorSource>().enabled = true
            }
            coEvery {
                sourceClient.patchSources("github", mapOf("owner/repo" to true, "non-existent" to true))
            } just runs

            val request = PatchSourcesRequest(
                sources = listOf(
                    PatchSourceRequest(sourceId = "owner/repo", enabled = true),
                    PatchSourceRequest(sourceId = "non-existent", enabled = true),
                ),
            )
            val result = service.patchSourcesIfConnectorExists("github", request)

            assertThat(result.sources).hasSize(1)
            assertThat(result.sources[0].id).isEqualTo("owner/repo")
            verify(exactly = 1) { githubConnector.patchSource(any(), any()) }
        }

        @Test
        fun `should throw ConnectorNotFoundException when connector not found`() = runTest {
            assertFailsWith<ConnectorNotFoundException> {
                service.patchSourcesIfConnectorExists(
                    "unknown",
                    PatchSourcesRequest(sources = emptyList()),
                )
            }
        }
    }
}
