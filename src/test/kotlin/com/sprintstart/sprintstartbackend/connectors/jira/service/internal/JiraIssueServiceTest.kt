package com.sprintstart.sprintstartbackend.connectors.jira.service.internal

import com.sprintstart.sprintstartbackend.connectors.ConnectionState
import com.sprintstart.sprintstartbackend.connectors.atlassian.external.AtlassianCredentialApi
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.exception.AtlassianCredentialNotFoundException
import com.sprintstart.sprintstartbackend.connectors.jira.JiraClient
import com.sprintstart.sprintstartbackend.connectors.jira.atlassianCredentialSecret
import com.sprintstart.sprintstartbackend.connectors.jira.jiraInstance
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.response.JiraIssueResponse
import com.sprintstart.sprintstartbackend.connectors.jira.repository.JiraInstanceRepository
import com.sprintstart.sprintstartbackend.connectors.jira.repository.JiraIssueRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class JiraIssueServiceTest {
    private val jiraClient = mockk<JiraClient>()
    private val instanceRepository = mockk<JiraInstanceRepository>()
    private val atlassianCredentialApi = mockk<AtlassianCredentialApi>()
    private val issueRepository = mockk<JiraIssueRepository>(relaxUnitFun = true)
    private val eventPublisher = mockk<org.springframework.context.ApplicationEventPublisher>(relaxUnitFun = true)

    private lateinit var service: JiraIssueService

    @BeforeEach
    fun setUp() {
        service = JiraIssueService(
            jiraClient,
            instanceRepository,
            atlassianCredentialApi,
            issueRepository,
            eventPublisher,
        )
    }

    @Nested
    inner class SearchAndIngestAllIssuesOfProjects {
        @Test
        fun `should fetch issues for each project key`() = runTest {
            val instance = jiraInstance(jiraProjectKeys = mutableSetOf("TEST", "DEV"))
            val credential = atlassianCredentialSecret()
            every { atlassianCredentialApi.findSecret(any(), any()) } returns credential
            coEvery { jiraClient.searchIssues(instance.instanceUrl, credential, any()) } returns emptyList()
            every { instanceRepository.save(any()) } answers { firstArg() }

            service.searchAndIngestAllIssuesOfProjects(
                instance,
                "user@example.com",
                "token",
                UUID.randomUUID(),
            )

            coVerify(exactly = 2) { jiraClient.searchIssues(instance.instanceUrl, credential, any()) }
        }
    }

    @Nested
    inner class CheckInstanceForUpdates {
        @Test
        fun `should set status to UP_TO_DATE when no new issues`() = runTest {
            val instance = jiraInstance()
            val credential = atlassianCredentialSecret()
            every { instanceRepository.findByInstanceUrlWithCollections(instance.instanceUrl) } returns instance
            every { atlassianCredentialApi.findSecret(any(), any()) } returns credential
            coEvery { jiraClient.searchIssues(instance.instanceUrl, credential, any()) } returns emptyList()
            every { instanceRepository.save(any()) } answers { firstArg() }

            service.checkInstanceForUpdates(instance, UUID.randomUUID())

            assertThat(instance.status).isEqualTo(ConnectionState.UP_TO_DATE)
            verify(exactly = 2) { instanceRepository.save(instance) }
        }

        @Test
        fun `should set status to OUT_OF_DATE when new issues exist`() = runTest {
            val instance = jiraInstance()
            val credential = atlassianCredentialSecret()
            val issue = mockk<JiraIssueResponse>()
            every { instanceRepository.findByInstanceUrlWithCollections(instance.instanceUrl) } returns instance
            every { atlassianCredentialApi.findSecret(any(), any()) } returns credential
            coEvery { jiraClient.searchIssues(instance.instanceUrl, credential, any()) } returns listOf(issue)
            every { instanceRepository.save(any()) } answers { firstArg() }

            service.checkInstanceForUpdates(instance, UUID.randomUUID())

            assertThat(instance.status).isEqualTo(ConnectionState.OUT_OF_DATE)
        }
    }

    @Nested
    inner class UpdateInstance {
        @Test
        fun `should set status to UP_TO_DATE after processing empty updates`() = runTest {
            val instance = jiraInstance()
            val credential = atlassianCredentialSecret()
            every { instanceRepository.findByInstanceUrlWithCollections(instance.instanceUrl) } returns instance
            every { atlassianCredentialApi.findSecret(any(), any()) } returns credential
            coEvery { jiraClient.searchIssues(instance.instanceUrl, credential, any()) } returns emptyList()
            every { instanceRepository.save(any()) } answers { firstArg() }

            service.updateInstance(instance, UUID.randomUUID())

            assertThat(instance.status).isEqualTo(ConnectionState.UP_TO_DATE)
        }
    }

    @Nested
    inner class FetchCredentials {
        @Test
        fun `should throw when credentials not found`() = runTest {
            val instance = jiraInstance()
            every { atlassianCredentialApi.findSecret(any(), any()) } returns null

            assertFailsWith<AtlassianCredentialNotFoundException> {
                service.searchAndIngestAllIssuesOfProject(
                    instance,
                    "missing@example.com",
                    "token",
                    "TEST",
                    UUID.randomUUID(),
                )
            }
        }
    }
}
