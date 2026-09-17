package com.sprintstart.sprintstartbackend.connectors.atlassian.service

import com.sprintstart.sprintstartbackend.connectors.atlassian.atlassianCredential
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.api.request.AddAtlassianCredentialRequest
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.api.request.ChangeAtlassianCredentialNameRequest
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.api.request.ChangeAtlassianCredentialTokenRequest
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.api.request.DeleteAtlassianCredentialRequest
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.exception.AtlassianCredentialAlreadyExistsException
import com.sprintstart.sprintstartbackend.connectors.atlassian.model.exception.AtlassianCredentialNotFoundException
import com.sprintstart.sprintstartbackend.connectors.atlassian.repository.AtlassianCredentialRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Optional
import kotlin.test.assertFailsWith

class AtlassianCredentialServiceTest {
    private val credentialsRepository = mockk<AtlassianCredentialRepository>()

    private lateinit var service: AtlassianCredentialService

    @BeforeEach
    fun setUp() {
        service = AtlassianCredentialService(credentialsRepository)
    }

    @Nested
    inner class AddCredentials {
        @Test
        fun `should save new credential`() {
            val request = AddAtlassianCredentialRequest("user@example.com", "token", "secret")
            every { credentialsRepository.existsById(any()) } returns false
            every { credentialsRepository.save(any()) } answers { firstArg() }

            service.addCredentials("auth-id", request)

            verify {
                credentialsRepository.save(
                    match {
                        it.id.authId == "auth-id" &&
                            it.id.name == "token" &&
                            it.userEmail == "user@example.com" &&
                            it.authToken == "secret"
                    },
                )
            }
        }

        @Test
        fun `should throw when credential already exists`() {
            val request = AddAtlassianCredentialRequest("user@example.com", "token", "secret")
            every { credentialsRepository.existsById(any()) } returns true

            assertFailsWith<AtlassianCredentialAlreadyExistsException> { service.addCredentials("auth-id", request) }
        }
    }

    @Nested
    inner class GetCredentialsOfUser {
        @Test
        fun `should return credentials of user`() {
            val credential = atlassianCredential()
            every { credentialsRepository.findAllByAuthId("auth-id") } returns listOf(credential)

            val result = service.getCredentialsOfUser("auth-id")

            assertThat(result).hasSize(1)
            assertThat(result[0].userEmail).isEqualTo("user@example.com")
        }
    }

    @Nested
    inner class RemoveCredential {
        @Test
        fun `should delete existing credential`() {
            val request = DeleteAtlassianCredentialRequest("user@example.com", "token")
            every { credentialsRepository.existsById(any()) } returns true
            every { credentialsRepository.deleteById(any()) } returns Unit

            service.removeCredential("auth-id", request)

            verify { credentialsRepository.deleteById(any()) }
        }

        @Test
        fun `should throw when credential not found`() {
            val request = DeleteAtlassianCredentialRequest("user@example.com", "token")
            every { credentialsRepository.existsById(any()) } returns false

            assertFailsWith<AtlassianCredentialNotFoundException> { service.removeCredential("auth-id", request) }
        }
    }

    @Nested
    inner class ChangeCredentialName {
        @Test
        fun `should update credential name`() {
            val credential = atlassianCredential()
            val request = ChangeAtlassianCredentialNameRequest("user@example.com", "token", "newToken")
            every { credentialsRepository.findById(any()) } returns Optional.of(credential)
            every { credentialsRepository.save(credential) } answers { firstArg() }

            val result = service.changeCredentialName("auth-id", request)

            assertThat(result.displayName).isEqualTo("newToken")
            assertThat(credential.id.name).isEqualTo("newToken")
        }

        @Test
        fun `should throw when credential not found`() {
            val request = ChangeAtlassianCredentialNameRequest("user@example.com", "token", "newToken")
            every { credentialsRepository.findById(any()) } returns Optional.empty()

            assertFailsWith<AtlassianCredentialNotFoundException> { service.changeCredentialName("auth-id", request) }
        }
    }

    @Nested
    inner class ChangeCredentialToken {
        @Test
        fun `should update credential token`() {
            val credential = atlassianCredential()
            val request = ChangeAtlassianCredentialTokenRequest("user@example.com", "token", "newSecret")
            every { credentialsRepository.findById(any()) } returns Optional.of(credential)
            every { credentialsRepository.save(credential) } answers { firstArg() }

            val result = service.changeCredentialToken("auth-id", request)

            assertThat(credential.authToken).isEqualTo("newSecret")
            assertThat(result.userEmail).isEqualTo("user@example.com")
        }

        @Test
        fun `should throw when credential not found`() {
            val request = ChangeAtlassianCredentialTokenRequest("user@example.com", "token", "newSecret")
            every { credentialsRepository.findById(any()) } returns Optional.empty()

            assertFailsWith<AtlassianCredentialNotFoundException> { service.changeCredentialToken("auth-id", request) }
        }
    }
}
