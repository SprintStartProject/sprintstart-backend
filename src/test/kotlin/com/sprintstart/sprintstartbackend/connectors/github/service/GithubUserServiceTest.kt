package com.sprintstart.sprintstartbackend.connectors.github.service

import com.sprintstart.sprintstartbackend.connectors.github.GithubClient
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubUser
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubUserPat
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.AddPatRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.RemovePatRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.UpdatePatNameRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.UpdatePatRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.GithubUserPatNameAlreadyExistsException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.GithubUserPatNotFoundException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.GithubUserPatStillInUseException
import com.sprintstart.sprintstartbackend.connectors.github.repository.GithubRepositoryConnectionRepository
import com.sprintstart.sprintstartbackend.connectors.github.repository.GithubUserRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.Optional
import kotlin.test.assertFailsWith

class GithubUserServiceTest {
    private val githubUserRepository = mockk<GithubUserRepository>()
    private val githubRepositoryConnectionRepository = mockk<GithubRepositoryConnectionRepository>()
    private val githubClient = mockk<GithubClient>()
    private val service = GithubUserService(githubUserRepository, githubRepositoryConnectionRepository, githubClient)

    private val authId = "test-auth-id"
    private val patName = "ghp_abcdefghijklmnopqrstuvwxyz0123456789"
    private val token = "ghp_abcdefghijklmnopqrstuvwxyz0123456789"
    private val userPat = GithubUserPat(authId, patName)
    private val githubUser = GithubUser(id = userPat, token = token)

    @Nested
    inner class UserExistsInGithub {
        @Test
        fun `returns true when GitHub confirms the user exists`() = runTest {
            coEvery { githubClient.userExists("ada") } returns true

            assertThat(service.userExistsInGithub("ada")).isTrue()
        }

        @Test
        fun `returns false when GitHub says the user does not exist`() = runTest {
            coEvery { githubClient.userExists("nosuchuser") } returns false

            assertThat(service.userExistsInGithub("nosuchuser")).isFalse()
        }

        @Test
        fun `passes GitHub's refusal to answer through as null rather than a verdict`() = runTest {
            coEvery { githubClient.userExists("ada") } returns null

            assertThat(service.userExistsInGithub("ada")).isNull()
        }
    }

    @Nested
    inner class GetAllPATs {
        @Test
        fun `returns list of tokens for the given authId`() {
            every { githubUserRepository.findAllByAuthId(authId) } returns listOf(token)

            val result = service.getAllPATNames(authId)

            assertThat(result).containsExactly(token)
        }

        @Test
        fun `returns empty list when user has no PATs`() {
            every { githubUserRepository.findAllByAuthId(authId) } returns emptyList()

            val result = service.getAllPATNames(authId)

            assertThat(result).isEmpty()
        }
    }

    @Nested
    inner class AddPAT {
        @Test
        fun `saves PAT when name is not taken`() {
            val request = AddPatRequest(patName, token)
            every { githubUserRepository.findById(userPat) } returns Optional.empty()
            every { githubUserRepository.save(any()) } returns githubUser

            service.addPAT(authId, request)

            verify { githubUserRepository.save(match { it.id == userPat && it.token == token }) }
        }

        @Test
        fun `throws GithubUserPatNameAlreadyExistsException when name is taken`() {
            val request = AddPatRequest(patName, token)
            every { githubUserRepository.findById(userPat) } returns Optional.of(githubUser)

            assertFailsWith<GithubUserPatNameAlreadyExistsException> {
                service.addPAT(authId, request)
            }
        }
    }

    @Nested
    inner class UpdatePAT {
        @Test
        fun `updates token when PAT exists`() {
            val newToken = "ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
            val request = UpdatePatRequest(patName, newToken)
            val mutableUser = GithubUser(id = userPat, token = token)
            every { githubUserRepository.findById(userPat) } returns Optional.of(mutableUser)
            every { githubUserRepository.save(any()) } answers { firstArg() }

            service.updatePAT(authId, request)

            assertThat(mutableUser.token).isEqualTo(newToken)
            verify { githubUserRepository.save(mutableUser) }
        }

        @Test
        fun `throws GithubUserPatNotFoundException when PAT does not exist`() {
            val request = UpdatePatRequest(patName, token)
            every { githubUserRepository.findById(userPat) } returns Optional.empty()

            assertFailsWith<GithubUserPatNotFoundException> {
                service.updatePAT(authId, request)
            }
        }
    }

    @Nested
    inner class UpdatePATName {
        @Test
        fun `updates name when PAT exists`() {
            val newName = "ghp_new_name_123456789012345678901234567890"
            val request = UpdatePatNameRequest(patName, newName)
            every { githubUserRepository.updatePatName(authId, patName, newName) } returns 1

            service.updatePATName(authId, request)
        }

        @Test
        fun `throws GithubUserPatNotFoundException when PAT does not exist`() {
            val newName = "ghp_new_name_123456789012345678901234567890"
            val request = UpdatePatNameRequest(patName, newName)
            every { githubUserRepository.updatePatName(authId, patName, newName) } returns 0

            assertFailsWith<GithubUserPatNotFoundException> {
                service.updatePATName(authId, request)
            }
        }
    }

    @Nested
    inner class RemovePAT {
        @Test
        fun `deletes PAT when not in use`() {
            val request = RemovePatRequest(patName)
            every { githubUserRepository.findById(userPat) } returns Optional.of(githubUser)
            every { githubRepositoryConnectionRepository.findByUser(githubUser) } returns emptyList()
            every { githubUserRepository.delete(githubUser) } returns Unit

            service.removePAT(authId, request)

            verify { githubUserRepository.delete(githubUser) }
        }

        @Test
        fun `throws GithubUserPatNotFoundException when PAT does not exist`() {
            val request = RemovePatRequest(patName)
            every { githubUserRepository.findById(userPat) } returns Optional.empty()

            assertFailsWith<GithubUserPatNotFoundException> {
                service.removePAT(authId, request)
            }
        }

        @Test
        fun `throws GithubUserPatStillInUseException when PAT is referenced by a repository connection`() {
            val request = RemovePatRequest(patName)
            every { githubUserRepository.findById(userPat) } returns Optional.of(githubUser)
            every {
                githubRepositoryConnectionRepository.findByUser(githubUser)
            } returns listOf(mockk(relaxed = true))

            assertFailsWith<GithubUserPatStillInUseException> {
                service.removePAT(authId, request)
            }
        }
    }
}
