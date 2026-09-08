package com.sprintstart.sprintstartbackend.connectors.jira.service

import com.sprintstart.sprintstartbackend.connectors.jira.external.JiraInstanceApi
import com.sprintstart.sprintstartbackend.connectors.jira.external.JiraSourceInstanceDto
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class JiraProjectSourceProviderTest {
    private val jiraInstanceApi = mockk<JiraInstanceApi>()
    private val provider = JiraProjectSourceProvider(jiraInstanceApi)

    @Test
    fun `maps connected Jira instances to project source dtos`() {
        val projectId = UUID.randomUUID()
        every { jiraInstanceApi.getSourceInstances(projectId) } returns
            listOf(
                JiraSourceInstanceDto(
                    instanceUrl = "https://acme.atlassian.net",
                    displayName = "Team board",
                    status = "CONNECTED",
                    enabled = true,
                    lastUpdate = Instant.now(),
                    jiraProjectKeys = setOf("TEAM"),
                ),
            )

        val sources = provider.findSourcesByProjectId(projectId)

        assertThat(sources).singleElement().satisfies({
            assertThat(it.id).isEqualTo("https://acme.atlassian.net")
            assertThat(it.name).isEqualTo("Team board")
            assertThat(it.type).isEqualTo("JIRA")
            assertThat(it.status).isEqualTo("CONNECTED")
        })
    }

    @Test
    fun `returns empty when the project has no Jira instances`() {
        val projectId = UUID.randomUUID()
        every { jiraInstanceApi.getSourceInstances(projectId) } returns emptyList()

        assertThat(provider.findSourcesByProjectId(projectId)).isEmpty()
    }
}
