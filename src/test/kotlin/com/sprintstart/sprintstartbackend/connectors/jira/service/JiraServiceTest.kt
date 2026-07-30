package com.sprintstart.sprintstartbackend.connectors.jira.service

import com.sprintstart.sprintstartbackend.connectors.ConnectionState
import com.sprintstart.sprintstartbackend.connectors.jira.JiraClient
import com.sprintstart.sprintstartbackend.connectors.jira.jiraCredential
import com.sprintstart.sprintstartbackend.connectors.jira.jiraInstance
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.request.ConnectJiraInstanceRequest
import com.sprintstart.sprintstartbackend.connectors.jira.model.entity.JiraInstanceConfig
import com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions.JiraCredentialNotFoundException
import com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions.JiraInstanceNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions.JiraInstanceUnavailableException
import com.sprintstart.sprintstartbackend.connectors.jira.repository.JiraCredentialsRepository
import com.sprintstart.sprintstartbackend.connectors.jira.repository.JiraInstanceConfigRepository
import com.sprintstart.sprintstartbackend.connectors.jira.repository.JiraInstanceRepository
import com.sprintstart.sprintstartbackend.connectors.jira.service.internal.JiraIssueService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
private suspend inline fun <reified T : Throwable> assertThrowsSuspend(block: suspend () -> Unit): T {
    try {
        block()
    } catch (e: Throwable) {
        if (e is T) return e
        throw AssertionError("Expected ${T::class.simpleName} but caught ${e::class.simpleName}", e)
    }
    throw AssertionError("Expected ${T::class.simpleName} but no exception was thrown")
}

class JiraServiceTest {
    private val credentialsRepository = mockk<JiraCredentialsRepository>()
    private val instanceRepository = mockk<JiraInstanceRepository>()
    private val configRepository = mockk<JiraInstanceConfigRepository>()
    private val jiraClient = mockk<JiraClient>()
    private val jiraIssueService = mockk<JiraIssueService>(relaxUnitFun = true)
    private val eventPublisher = mockk<org.springframework.context.ApplicationEventPublisher>(relaxUnitFun = true)
    private val jiraInstanceConfigService = mockk<JiraInstanceConfigService>(relaxUnitFun = true)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val applicationScope = kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher())

    private lateinit var service: JiraService

    @BeforeEach
    fun setUp() {
        every { jiraInstanceConfigService.calculateNextSyncAt(any()) } returns java.time.Instant.now()
        service = JiraService(
            credentialsRepository,
            instanceRepository,
            configRepository,
            jiraClient,
            applicationScope,
            jiraIssueService,
            eventPublisher,
            jiraInstanceConfigService,
        )
    }

    @Nested
    inner class GetInstances {
        @Test
        fun `should return all connected instances`() {
            val instance = jiraInstance()
            every { instanceRepository.findAll() } returns listOf(instance)

            val result = service.getInstances()

            assertThat(result).hasSize(1)
            assertThat(result[0].instanceUrl).isEqualTo(instance.instanceUrl)
        }

        @Test
        fun `should return empty list when no instances connected`() {
            every { instanceRepository.findAll() } returns emptyList()

            val result = service.getInstances()

            assertThat(result).isEmpty()
        }

        @Test
        fun `should return instances for a project`() {
            val projectId = UUID.randomUUID()
            val instance = jiraInstance(projectIds = mutableSetOf(projectId))
            every { instanceRepository.findByProjectId(projectId) } returns listOf(instance)

            val result = service.getInstances(projectId)

            assertThat(result).hasSize(1)
            assertThat(result[0].instanceUrl).isEqualTo(instance.instanceUrl)
        }
    }

    @Nested
    inner class PatchInstance {
        @Test
        fun `should toggle source enabled`() {
            val instance = jiraInstance(sourceEnabled = false)
            every { instanceRepository.findById(instance.instanceUrl) } returns Optional.of(instance)

            service.patchInstance(instance.instanceUrl, true)

            assertThat(instance.sourceEnabled).isTrue()
        }

        @Test
        fun `should throw when instance not found`() {
            every { instanceRepository.findById("unknown") } returns Optional.empty()

            assertFailsWith<JiraInstanceNotConnectedException> { service.patchInstance("unknown", true) }
        }
    }

    @Nested
    inner class ConnectInstanceIfNeeded {
        private val request = ConnectJiraInstanceRequest(
            displayName = "Test Instance",
            url = "https://jira.example.com",
            userEmail = "user@example.com",
            tokenName = "token",
            projectId = UUID.randomUUID(),
        )

        @Test
        fun `should add project to existing instance and return transaction id`() {
            runTest {
                val existing = jiraInstance(
                    instanceUrl = request.url,
                    projectIds = mutableSetOf(),
                    jiraProjectKeys = mutableSetOf("TEST"),
                )
                every { instanceRepository.findById(request.url) } returns Optional.of(existing)
                every { instanceRepository.save(existing) } answers { firstArg() }

                val transactionId = service.connectInstanceIfNeeded(request)

                assertThat(transactionId).isNotNull()
                assertThat(existing.projectIds).contains(request.projectId)
                verify { instanceRepository.save(existing) }
                coVerify(exactly = 0) { jiraClient.checkInstanceCapabilities(any()) }
            }
        }

        @Test
        fun `should connect new instance when not already connected`() {
            runTest {
                val expectedNextSyncAt = java.time.Instant.now()
                val credential = jiraCredential(request.userEmail, request.tokenName)
                every { instanceRepository.findById(request.url) } returns Optional.empty()
                every { credentialsRepository.findById(any()) } returns Optional.of(credential)
                coEvery { jiraClient.checkInstanceCapabilities(request.url) } returns true
                coEvery { jiraClient.searchProjects(request.url, credential) } returns emptyList()
                every { jiraInstanceConfigService.calculateNextSyncAt(any()) } returns expectedNextSyncAt
                every { instanceRepository.save(any()) } answers { firstArg() }
                every { configRepository.save(any()) } answers { firstArg() }
                val transactionId = service.connectInstanceIfNeeded(request)

                assertThat(transactionId).isNotNull()
                verify { instanceRepository.save(any()) }
                verify {
                    configRepository.save(
                        match {
                            (it as JiraInstanceConfig).nextSyncAt == expectedNextSyncAt
                        },
                    )
                }
            }
        }

        @Test
        fun `should throw when credentials not found`() {
            runTest {
                every { instanceRepository.findById(request.url) } returns Optional.empty()
                every { credentialsRepository.findById(any()) } returns Optional.empty()

                assertThrowsSuspend<JiraCredentialNotFoundException> {
                    service.connectInstanceIfNeeded(request)
                }
            }
        }

        @Test
        fun `should throw when instance is unavailable`() {
            runTest {
                val credential = jiraCredential(request.userEmail, request.tokenName)
                every { instanceRepository.findById(request.url) } returns Optional.empty()
                every { credentialsRepository.findById(any()) } returns Optional.of(credential)
                coEvery { jiraClient.checkInstanceCapabilities(request.url) } returns false

                assertThrowsSuspend<JiraInstanceUnavailableException> {
                    service.connectInstanceIfNeeded(request)
                }
            }
        }

        @Test
        fun `should set instance status to UP_TO_DATE after connecting`() {
            runTest {
                val credential = jiraCredential(request.userEmail, request.tokenName)
                every { instanceRepository.findById(request.url) } returns Optional.empty()
                every { credentialsRepository.findById(any()) } returns Optional.of(credential)
                coEvery { jiraClient.checkInstanceCapabilities(request.url) } returns true
                coEvery { jiraClient.searchProjects(request.url, credential) } returns emptyList()
                every { instanceRepository.save(any()) } answers { firstArg() }
                every { configRepository.save(any()) } answers { firstArg() }
                service.connectInstanceIfNeeded(request)

                verify { instanceRepository.save(match { it.status == ConnectionState.UP_TO_DATE }) }
            }
        }
    }
}
