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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Optional
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
            every { instanceRepository.findById(instance1.instanceUrl) } returns Optional.of(instance1)
            every { instanceRepository.findById(instance2.instanceUrl) } returns Optional.of(instance2)
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
            every { instanceRepository.findById(instance.instanceUrl) } returns Optional.of(instance)
            coEvery { issueService.updateInstance(instance, any()) } just Awaits
            every { instanceRepository.save(any()) } answers { firstArg() }

            val result = service.updateJiraInstance(instance.instanceUrl, true)

            assertThat(result.transactionId).isNotNull()
            coVerify { issueService.updateInstance(instance, any()) }
        }

        @Test
        fun `should check for updates when performUpdate is false`() {
            val instance = jiraInstance()
            every { instanceRepository.findById(instance.instanceUrl) } returns Optional.of(instance)
            coEvery { issueService.checkInstanceForUpdates(instance, any<UUID>()) } just Awaits

            val result = service.updateJiraInstance(instance.instanceUrl, false)

            assertThat(result.transactionId).isNotNull()
            coVerify { issueService.checkInstanceForUpdates(instance, any()) }
        }

        @Test
        fun `should throw when instance not connected`() {
            every { instanceRepository.findById("unknown") } returns Optional.empty()

            assertFailsWith<JiraInstanceNotConnectedException> { service.updateJiraInstance("unknown", true) }
        }
    }
}
