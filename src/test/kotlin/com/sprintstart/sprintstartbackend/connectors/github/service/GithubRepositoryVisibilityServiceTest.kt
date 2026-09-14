package com.sprintstart.sprintstartbackend.connectors.github.service

import com.sprintstart.sprintstartbackend.connectors.github.GithubClient
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubUser
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubUserPat
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.GithubUserPatNotFoundException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.RepositoryNotFoundException
import com.sprintstart.sprintstartbackend.connectors.github.repository.GithubRepositoryConnectionRepository
import com.sprintstart.sprintstartbackend.connectors.github.repository.GithubUserRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID
import kotlin.test.assertFailsWith

/**
 * The gap this closes: both link paths authorized only the *target project*. Linking is how a
 * repository's artifacts and index chunks reach a project, so without a visibility check any PM who
 * knew an `owner/name` -- or held a connection id, which source-overview responses hand out -- could
 * attach another team's private repository to their own project, on that team's PAT, and have the
 * whole thing become answerable in their chat.
 */
class GithubRepositoryVisibilityServiceTest {
    private val githubUserRepository = mockk<GithubUserRepository>()
    private val repoConnectionRepository = mockk<GithubRepositoryConnectionRepository>()
    private val githubClient = mockk<GithubClient>()
    private val userApi = mockk<UserApi>()
    private val service = GithubRepositoryVisibilityService(
        githubUserRepository,
        repoConnectionRepository,
        githubClient,
        userApi,
    )

    private val callersPat = GithubUser(GithubUserPat("caller", "my-pat"), token = "my-token")

    @BeforeEach
    fun setUp() {
        every { userApi.getUserIdByAuthId("caller") } returns Optional.of(UUID.randomUUID())
    }

    @Test
    fun `a caller whose own PAT sees the repository passes`() = runTest {
        every {
            githubUserRepository.findById(GithubUserPat("caller", "my-pat"))
        } returns Optional.of(callersPat)
        coEvery { githubClient.repositoryExists(any()) } returns true

        service.requireCallerCanSeeRepository("caller", "my-pat", "acme", "repo")
    }

    @Test
    fun `a caller whose PAT cannot see the repository is refused`() = runTest {
        every {
            githubUserRepository.findById(GithubUserPat("caller", "my-pat"))
        } returns Optional.of(callersPat)
        coEvery { githubClient.repositoryExists(any()) } returns false

        assertFailsWith<RepositoryNotFoundException> {
            service.requireCallerCanSeeRepository("caller", "my-pat", "acme", "private-repo")
        }
    }

    @Test
    fun `the probe uses the caller's own credentials, never the connection's`() = runTest {
        val probed = mutableListOf<GithubRepositoryConnection>()
        every {
            githubUserRepository.findById(GithubUserPat("caller", "my-pat"))
        } returns Optional.of(callersPat)
        coEvery { githubClient.repositoryExists(capture(probed)) } returns true

        service.requireCallerCanSeeRepository("caller", "my-pat", "acme", "repo")

        // Borrowing the first PM's token to answer "may this PM see it?" would defeat the check
        // entirely -- that token is exactly what the caller must not get to use.
        assertThat(
            probed
                .single()
                .user.id.authId,
        ).isEqualTo("caller")
        assertThat(probed.single().owner).isEqualTo("acme")
        assertThat(probed.single().name).isEqualTo("repo")
    }

    @Test
    fun `a caller with no such PAT is refused`() = runTest {
        every { githubUserRepository.findById(any()) } returns Optional.empty()

        assertFailsWith<GithubUserPatNotFoundException> {
            service.requireCallerCanSeeRepository("caller", "my-pat", "acme", "repo")
        }
    }

    @Test
    fun `a connection is visible when any of the caller's PATs can see it`() = runTest {
        val connection = connection()
        every { repoConnectionRepository.findById(connection.id) } returns Optional.of(connection)
        every { githubUserRepository.findAllByAuthId("caller") } returns listOf("dead-pat", "my-pat")
        every {
            githubUserRepository.findById(GithubUserPat("caller", "dead-pat"))
        } returns Optional.of(GithubUser(GithubUserPat("caller", "dead-pat"), token = "dead"))
        every {
            githubUserRepository.findById(GithubUserPat("caller", "my-pat"))
        } returns Optional.of(callersPat)
        // The endpoint names no PAT, so any of the caller's own tokens may answer the question.
        coEvery { githubClient.repositoryExists(match { it.user.id.name == "dead-pat" }) } returns false
        coEvery { githubClient.repositoryExists(match { it.user.id.name == "my-pat" }) } returns true

        service.requireCallerCanSeeConnection("caller", connection.id)
    }

    @Test
    fun `a connection none of the caller's PATs can see is refused`() = runTest {
        val connection = connection()
        every { repoConnectionRepository.findById(connection.id) } returns Optional.of(connection)
        every { githubUserRepository.findAllByAuthId("caller") } returns listOf("my-pat")
        every {
            githubUserRepository.findById(GithubUserPat("caller", "my-pat"))
        } returns Optional.of(callersPat)
        coEvery { githubClient.repositoryExists(any()) } returns false

        assertFailsWith<RepositoryNotFoundException> {
            service.requireCallerCanSeeConnection("caller", connection.id)
        }
    }

    @Test
    fun `a caller with no PAT at all is refused`() = runTest {
        val connection = connection()
        every { repoConnectionRepository.findById(connection.id) } returns Optional.of(connection)
        every { githubUserRepository.findAllByAuthId("caller") } returns emptyList()

        assertFailsWith<RepositoryNotFoundException> {
            service.requireCallerCanSeeConnection("caller", connection.id)
        }
    }

    @Test
    fun `an unknown connection is refused the same way an invisible one is`() = runTest {
        val unknown = UUID.randomUUID()
        every { repoConnectionRepository.findById(unknown) } returns Optional.empty()

        // Same exception either way, so a caller cannot use the response to find out whether a
        // connection id exists at all.
        assertFailsWith<RepositoryNotFoundException> {
            service.requireCallerCanSeeConnection("caller", unknown)
        }
    }

    private fun connection() = GithubRepositoryConnection(
        owner = "acme",
        name = "private-repo",
        user = GithubUser(GithubUserPat("other-pm", "their-pat"), token = "their-token"),
        projectIdsInternal = mutableSetOf(UUID.randomUUID()),
    )
}
