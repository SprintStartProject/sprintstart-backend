package com.sprintstart.sprintstartbackend.connectors.jira.service

import com.sprintstart.sprintstartbackend.connectors.jira.jiraInstance
import com.sprintstart.sprintstartbackend.connectors.jira.model.entity.JiraInstance
import com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions.JiraInstanceNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.jira.repository.JiraInstanceRepository
import com.sprintstart.sprintstartbackend.connectors.jira.service.internal.JiraIssueService
import io.mockk.Awaits
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFailsWith

@OptIn(ExperimentalCoroutinesApi::class)
class JiraUpdateServiceTest {
    private val instanceRepository = mockk<JiraInstanceRepository>()
    private val issueService = mockk<JiraIssueService>()
    private val eventPublisher = mockk<org.springframework.context.ApplicationEventPublisher>(relaxUnitFun = true)
    private val testScope = TestScope(UnconfinedTestDispatcher())

    private lateinit var service: JiraUpdateService

    @BeforeEach
    fun setUp() {
        service = JiraUpdateService(instanceRepository, issueService, eventPublisher, testScope)
    }

    @Nested
    inner class UpdateAllJiraInstances {
        @Test
        fun `should return transaction ids for all instances`() {
            val instance1 = jiraInstance(instanceUrl = "https://jira1.example.com")
            val instance2 = jiraInstance(instanceUrl = "https://jira2.example.com")
            every { instanceRepository.findAll() } returns listOf(instance1, instance2)
            coEvery { issueService.updateInstance(any<JiraInstance>(), any<UUID>()) } just Awaits
            every {
                instanceRepository.findByInstanceUrlWithCollections(instance1.instanceUrl)
            } returns instance1
            every {
                instanceRepository.findByInstanceUrlWithCollections(instance2.instanceUrl)
            } returns instance2
            every { instanceRepository.save(any()) } answers { firstArg() }

            val result = service.updateAllJiraInstances()

            assertThat(result).hasSize(2)
            coVerify(exactly = 2) { issueService.updateInstance(any<JiraInstance>(), any<UUID>()) }
        }
    }

    @Nested
    inner class UpdateJiraInstance {
        @Test
        fun `should launch update when performUpdate is true`() {
            val instance = jiraInstance()
            every {
                instanceRepository.findByInstanceUrlWithCollections(instance.instanceUrl)
            } returns instance
            coEvery { issueService.updateInstance(instance, any()) } just Awaits
            every { instanceRepository.save(any()) } answers { firstArg() }

            val result = service.updateJiraInstance(instance.instanceUrl, true)

            assertThat(result.transactionId).isNotNull()
            coVerify { issueService.updateInstance(instance, any()) }
        }

        @Test
        fun `should load the instance with its lazy collections initialized`() {
            // Regression: the update runs on a background coroutine without a Hibernate session,
            // so the instance must be loaded with jiraProjectKeys/projectIds eagerly fetched to
            // avoid a LazyInitializationException crashing the run.
            val instance = jiraInstance()
            every {
                instanceRepository.findByInstanceUrlWithCollections(instance.instanceUrl)
            } returns instance
            coEvery { issueService.updateInstance(instance, any()) } just Awaits
            every { instanceRepository.save(any()) } answers { firstArg() }

            service.updateJiraInstance(instance.instanceUrl, true)

            verify { instanceRepository.findByInstanceUrlWithCollections(instance.instanceUrl) }
        }

        @Test
        fun `should check for updates when performUpdate is false`() {
            val instance = jiraInstance()
            every {
                instanceRepository.findByInstanceUrlWithCollections(instance.instanceUrl)
            } returns instance
            coEvery { issueService.checkInstanceForUpdates(instance, any<UUID>()) } just Awaits

            val result = service.updateJiraInstance(instance.instanceUrl, false)

            assertThat(result.transactionId).isNotNull()
            coVerify { issueService.checkInstanceForUpdates(instance, any()) }
        }

        @Test
        fun `should throw when instance not connected`() {
            every { instanceRepository.findByInstanceUrlWithCollections("unknown") } returns null

            assertFailsWith<JiraInstanceNotConnectedException> { service.updateJiraInstance("unknown", true) }
        }
    }
}
