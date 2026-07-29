package com.sprintstart.sprintstartbackend.connectors.jira

import com.sprintstart.sprintstartbackend.connectors.jira.model.api.response.JiraInstanceDto
import com.sprintstart.sprintstartbackend.connectors.jira.service.JiraService
import com.sprintstart.sprintstartbackend.connectors.overview.models.ConnectorSource
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Instant
import java.util.UUID

@ExtendWith(MockKExtension::class)
class JiraConnectorTest {
    private val service = mockk<JiraService>()
    private lateinit var connector: JiraConnector

    @BeforeEach
    fun setUp() {
        connector = JiraConnector(service)
    }

    @Test
    fun `should expose jira id and display name`() {
        assertThat(connector.id).isEqualTo("jira")
        assertThat(connector.displayName).isEqualTo("Jira Connector")
    }

    @Test
    fun `getSources should map instances to connector sources`() {
        val instance = JiraInstanceDto(
            instanceUrl = "https://jira.example.com",
            displayName = "Test",
            lastUpdate = Instant.now(),
            projectIds = mutableSetOf(),
            sourceEnabled = true,
            status = "UP_TO_DATE",
            updateCredentialName = "token",
            updateCredentialUserEmail = "user@example.com",
        )
        every { service.getInstances() } returns listOf(instance)

        val result = connector.getSources()

        assertThat(result).hasSize(1)
        assertThat(result[0].id).isEqualTo(instance.instanceUrl)
        assertThat(result[0].enabled).isTrue()
    }

    @Test
    fun `getSources with project id should delegate to service`() {
        val projectId = UUID.randomUUID()
        every { service.getInstances(projectId) } returns emptyList()

        val result = connector.getSources(projectId)

        assertThat(result).isEmpty()
    }

    @Test
    fun `patchSource should delegate to service`() {
        val source = ConnectorSource("https://jira.example.com", "Test", "https://jira.example.com", true)
        every { service.patchInstance(source.id, false) } returns Unit

        connector.patchSource(source, false)

        verify { service.patchInstance(source.id, false) }
    }
}
