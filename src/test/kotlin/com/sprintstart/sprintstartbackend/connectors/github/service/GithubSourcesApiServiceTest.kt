package com.sprintstart.sprintstartbackend.connectors.github.service

import com.sprintstart.sprintstartbackend.connectors.github.external.GithubOwnerKind
import com.sprintstart.sprintstartbackend.connectors.github.external.GithubRepositoryRef
import com.sprintstart.sprintstartbackend.connectors.github.models.RepositoryConnectionOutcome
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.ConnectRepositoryRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.DiscoverRepositoriesRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.DiscoverRepositoriesResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.DiscoveredRepository
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.UpdateRepositoryResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.GithubUserPatNotFoundException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.RepositoryNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.RepositoryNotFoundException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.RepositoryNotInitializedException
import io.mockk.coEvery
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

class GithubSourcesApiServiceTest {
    private val users: GithubUserService = mockk(relaxed = true)
    private val connector: GithubConnectorService = mockk(relaxed = true)
    private val visibility: GithubRepositoryVisibilityService = mockk(relaxed = true)
    private val projects: GithubRepositoryProjectService = mockk(relaxed = true)
    private val updates: GithubUpdatesService = mockk(relaxed = true)
    private val api = GithubSourcesApiService(users, connector, visibility, projects, updates)

    private val projectId = UUID.randomUUID()
    private val ok = GithubRepositoryRef("acme", "ok")
    private val bad = GithubRepositoryRef("acme", "bad")

    private fun status(e: Throwable?): HttpStatus = HttpStatus.valueOf(
        (e as ResponseStatusException).statusCode.value(),
    )

    @Test
    fun `token names are the stored names`() {
        every { users.getAllPATNames("auth|pm") } returns listOf("work")

        assertThat(api.getTokenNames("auth|pm")).containsExactly("work")
    }

    @Test
    fun `discovery uses the caller's own auth id and picks the listing by owner kind`() =
        runTest {
            val request = DiscoverRepositoriesRequest("acme", "auth|pm", "work", 0, 20)
            val found = DiscoverRepositoriesResponse(listOf(DiscoveredRepository("app", true, "u", true, false)))
            coEvery { connector.discoverRepositoriesOfOrg(request) } returns found
            coEvery { connector.discoverRepositoriesOfUser(request) } returns DiscoverRepositoriesResponse(emptyList())

            val org = api.discoverRepositories("auth|pm", GithubOwnerKind.ORGANISATION, "acme", "work", 0, 20)
            val user = api.discoverRepositories("auth|pm", GithubOwnerKind.USER, "acme", "work", 0, 20)

            assertThat(org.single().name).isEqualTo("app")
            assertThat(org.single().alreadyConnected).isTrue()
            assertThat(user).isEmpty()
        }

    @Test
    fun `a missing token becomes a 404 that names the token and never a value`() =
        runTest {
            coEvery { connector.discoverRepositoriesOfOrg(any()) } throws
                GithubUserPatNotFoundException("work", "auth|pm")

            val failure = runCatching {
                api.discoverRepositories("auth|pm", GithubOwnerKind.ORGANISATION, "acme", "work", 0, 20)
            }.exceptionOrNull()

            assertThat(status(failure)).isEqualTo(HttpStatus.NOT_FOUND)
            assertThat(failure).hasMessageContaining("no GitHub token named “work”")
        }

    @Test
    fun `connecting is per repository, so one failing does not stop or hide the others`() =
        runTest {
            coEvery {
                connector.connectRepositoryIfNecessary(
                    "auth|pm",
                    ConnectRepositoryRequest("acme", "ok", "work", projectId),
                )
            } returns
                RepositoryConnectionOutcome(UUID.randomUUID(), wasReused = true)
            coEvery {
                connector.connectRepositoryIfNecessary(
                    "auth|pm",
                    ConnectRepositoryRequest("acme", "bad", "work", projectId),
                )
            } throws
                RepositoryNotFoundException("acme", "bad")

            val results = api.connectRepositories("auth|pm", projectId, "work", listOf(bad, ok))

            assertThat(results.map { it.repository }).containsExactly(bad, ok)
            assertThat(results[0].failure).isEqualTo("Repository acme/bad not found")
            assertThat(results[1].failure).isNull()
            assertThat(results[1].reused).isTrue()
        }

    @Test
    fun `an unexpected failure while connecting is reported without its internals`() =
        runTest {
            coEvery { connector.connectRepositoryIfNecessary(any(), any()) } throws
                IllegalStateException("jdbc:secret@host")

            val result = api.connectRepositories("auth|pm", projectId, "work", listOf(ok)).single()

            assertThat(result.failure).isNotNull().doesNotContain("jdbc").doesNotContain("secret")
        }

    @Test
    fun `linking checks that the caller can see the repository before it links`() =
        runTest {
            val id = UUID.randomUUID()
            every { projects.addProjectToRepository("auth|pm", id, projectId) } returns setOf(projectId)

            api.linkRepository("auth|pm", projectId, id)

            coVerifyOrder {
                visibility.requireCallerCanSeeConnection("auth|pm", id)
                projects.addProjectToRepository("auth|pm", id, projectId)
            }
        }

    @Test
    fun `a repository the caller cannot see is not linked, and is a 404`() =
        runTest {
            val id = UUID.randomUUID()
            coEvery { visibility.requireCallerCanSeeConnection("auth|pm", id) } throws
                RepositoryNotFoundException("", "", "not found")

            val failure = runCatching { api.linkRepository("auth|pm", projectId, id) }.exceptionOrNull()

            assertThat(status(failure)).isEqualTo(HttpStatus.NOT_FOUND)
            verify(exactly = 0) { projects.addProjectToRepository(any(), any(), any()) }
        }

    @Test
    fun `unlinking passes the project and the caller through`() {
        val id = UUID.randomUUID()
        every { projects.removeProjectFromRepository("auth|pm", id, projectId) } returns emptySet()

        assertThat(api.unlinkRepository("auth|pm", projectId, id)).isEmpty()
    }

    @Test
    fun `syncing returns the transaction id`() {
        val transaction = UUID.randomUUID()
        every { updates.updateRepository(any(), true) } returns UpdateRepositoryResponse(transaction)

        assertThat(api.syncRepository(ok)).isEqualTo(transaction)
    }

    @Test
    fun `syncing something not connected is a 404, and one not yet fetched is a 400`() {
        every { updates.updateRepository(any(), true) } throws RepositoryNotConnectedException("acme", "ok")
        assertThat(status(runCatching { api.syncRepository(ok) }.exceptionOrNull())).isEqualTo(HttpStatus.NOT_FOUND)

        every { updates.updateRepository(any(), true) } throws RepositoryNotInitializedException("acme", "ok")
        assertThat(status(runCatching { api.syncRepository(ok) }.exceptionOrNull())).isEqualTo(HttpStatus.BAD_REQUEST)
    }
}
