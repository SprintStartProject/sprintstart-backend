package com.sprintstart.sprintstartbackend.connectors.jira.service

import com.sprintstart.sprintstartbackend.connectors.jira.jiraCredential
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.request.credentials.AddCredentialRequest
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.request.credentials.ChangeJiraCredentialNameRequest
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.request.credentials.ChangeJiraCredentialTokenRequest
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.request.credentials.DeleteJiraCredentialRequest
import com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions.JiraCredentialAlreadyExistsException
import com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions.JiraCredentialNotFoundException
import com.sprintstart.sprintstartbackend.connectors.jira.repository.JiraCredentialsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Optional
import kotlin.test.assertFailsWith

class JiraCredentialsServiceTest {
    private val credentialsRepository = mockk<JiraCredentialsRepository>()

    private lateinit var service: JiraCredentialsService

    @BeforeEach
    fun setUp() {
        service = JiraCredentialsService(credentialsRepository)
    }

    @Nested
    inner class AddCredentials {
        @Test
        fun `should save new credential`() {
            val request = AddCredentialRequest("user@example.com", "token", "secret")
            every { credentialsRepository.existsById(any()) } returns false
            every { credentialsRepository.save(any()) } answers { firstArg() }

            service.addCredentials(request)

            verify { credentialsRepository.save(any()) }
        }

        @Test
        fun `should throw when credential already exists`() {
            val request = AddCredentialRequest("user@example.com", "token", "secret")
            every { credentialsRepository.existsById(any()) } returns true

            assertFailsWith<JiraCredentialAlreadyExistsException> { service.addCredentials(request) }
        }
    }

    @Nested
    inner class GetCredentialsOfUser {
        @Test
        fun `should return credentials of user`() {
            val credential = jiraCredential()
            every { credentialsRepository.findAllByUserEmail("user@example.com") } returns listOf(credential)

            val result = service.getCredentialsOfUser("user@example.com")

            assertThat(result).hasSize(1)
            assertThat(result[0].userEmail).isEqualTo("user@example.com")
        }
    }

    @Nested
    inner class RemoveCredential {
        @Test
        fun `should delete existing credential`() {
            val request = DeleteJiraCredentialRequest("user@example.com", "token")
            every { credentialsRepository.existsById(any()) } returns true
            every { credentialsRepository.deleteById(any()) } returns Unit

            service.removeCredential(request)

            verify { credentialsRepository.deleteById(any()) }
        }

        @Test
        fun `should throw when credential not found`() {
            val request = DeleteJiraCredentialRequest("user@example.com", "token")
            every { credentialsRepository.existsById(any()) } returns false

            assertFailsWith<JiraCredentialNotFoundException> { service.removeCredential(request) }
        }
    }

    @Nested
    inner class ChangeCredentialName {
        @Test
        fun `should update credential name`() {
            val credential = jiraCredential()
            val request = ChangeJiraCredentialNameRequest("user@example.com", "token", "newToken")
            every { credentialsRepository.findById(any()) } returns Optional.of(credential)
            every { credentialsRepository.save(credential) } answers { firstArg() }

            val result = service.changeCredentialName(request)

            assertThat(result.displayName).isEqualTo("newToken")
            assertThat(credential.id.name).isEqualTo("newToken")
        }

        @Test
        fun `should throw when credential not found`() {
            val request = ChangeJiraCredentialNameRequest("user@example.com", "token", "newToken")
            every { credentialsRepository.findById(any()) } returns Optional.empty()

            assertFailsWith<JiraCredentialNotFoundException> { service.changeCredentialName(request) }
        }
    }

    @Nested
    inner class ChangeCredentialToken {
        @Test
        fun `should update credential token`() {
            val credential = jiraCredential()
            val request = ChangeJiraCredentialTokenRequest("user@example.com", "token", "newSecret")
            every { credentialsRepository.findById(any()) } returns Optional.of(credential)
            every { credentialsRepository.save(credential) } answers { firstArg() }

            val result = service.changeCredentialToken(request)

            assertThat(credential.authToken).isEqualTo("newSecret")
            assertThat(result.userEmail).isEqualTo("user@example.com")
        }

        @Test
        fun `should throw when credential not found`() {
            val request = ChangeJiraCredentialTokenRequest("user@example.com", "token", "newSecret")
            every { credentialsRepository.findById(any()) } returns Optional.empty()

            assertFailsWith<JiraCredentialNotFoundException> { service.changeCredentialToken(request) }
        }
    }
}
