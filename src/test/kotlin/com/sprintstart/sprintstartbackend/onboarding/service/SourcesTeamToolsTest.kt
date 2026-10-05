package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.connectors.github.external.GithubDiscoveredRepositoryDto
import com.sprintstart.sprintstartbackend.connectors.github.external.GithubOwnerKind
import com.sprintstart.sprintstartbackend.connectors.github.external.GithubSourceInstanceDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

class SourcesTeamToolsTest {
    private val f = SourcesFixture()
    private val tools = SourcesTeamTools(f.githubSourcesApi, f.githubRepositoryApi, f.scope)

    private fun read(name: String, vararg args: Pair<String, Any?>) = tools.execute(f.call(name, *args), f.context)

    @Test
    fun `mounts exactly the reads of the area, in the sources area`() {
        assertThat(tools.area).isEqualTo(TeamArea.SOURCES)
        assertThat(tools.toolSpecs().map { it.name })
            .containsExactly("list_my_credential_names", "discover_repositories", "list_project_sources")
        assertThat(tools.toolSpecs().all { tools.handles(it.name) }).isTrue()
    }

    @Test
    fun `credential names are the names and nothing else`() {
        val text = read("list_my_credential_names")

        assertThat(text).contains("- personal", "- work", "names only", "settings page")
    }

    @Test
    fun `with no token stored it points to the settings page and warns against pasting one`() {
        every { f.githubSourcesApi.getTokenNames("auth|pm") } returns emptyList()

        assertThat(
            read("list_my_credential_names"),
        ).contains("no GitHub token", "settings page", "never ask them to paste")
    }

    @Test
    fun `no read tool takes a token value`() {
        tools.toolSpecs().forEach { spec ->
            val properties = (spec.parameters["properties"] as kotlinx.serialization.json.JsonObject).keys
            assertThat(properties).describedAs(spec.name).doesNotContain("token", "pat", "secret", "password")
        }
    }

    @Test
    fun `discovery lists what GitHub shows and marks what is already connected`() {
        coEvery {
            f.githubSourcesApi.discoverRepositories("auth|pm", GithubOwnerKind.ORGANISATION, "acme", "work", 0, 20)
        } returns
            listOf(
                GithubDiscoveredRepositoryDto(
                    "app",
                    isPrivate = true,
                    url = "u",
                    alreadyConnected = true,
                    enabled = false,
                ),
                GithubDiscoveredRepositoryDto(
                    "lib",
                    isPrivate = false,
                    url = "u",
                    alreadyConnected = false,
                    enabled = null,
                ),
            )

        val text = read("discover_repositories", "kind" to "organisation", "owner" to "acme", "token_name" to "work")

        assertThat(text).contains("- app (private) — already connected, disabled", "- lib")
        assertThat(text).doesNotContain("- lib (private)").doesNotContain("- lib — already")
    }

    @Test
    fun `discovery refuses a token name that is not the manager's before asking GitHub`() {
        val text = read("discover_repositories", "kind" to "user", "owner" to "sam", "token_name" to "nope")

        assertThat(text).contains("no GitHub token with that name")
        coVerify(exactly = 0) { f.githubSourcesApi.discoverRepositories(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a token pasted as the token name is refused and not repeated`() {
        val pasted = "github_pat_11ABCDEFG0123456789_secretsecret"

        val text = read("discover_repositories", "kind" to "user", "owner" to "sam", "token_name" to pasted)

        assertThat(text).contains("Never paste a token").doesNotContain(pasted)
    }

    @Test
    fun `an unknown kind is refused, and a GitHub failure is reported as its reason`() {
        assertThat(read("discover_repositories", "kind" to "team", "owner" to "x", "token_name" to "work"))
            .contains("organisation or user")

        coEvery { f.githubSourcesApi.discoverRepositories(any(), any(), any(), any(), any(), any()) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND, "There is no GitHub token named “work”.")
        assertThat(read("discover_repositories", "kind" to "user", "owner" to "x", "token_name" to "work"))
            .contains("There is no GitHub token named")
    }

    @Test
    fun `an empty page says so, and a full page says there may be more`() {
        coEvery { f.githubSourcesApi.discoverRepositories(any(), any(), any(), any(), 0, any()) } returns emptyList()
        coEvery { f.githubSourcesApi.discoverRepositories(any(), any(), any(), any(), 1, any()) } returns
            List(20) { GithubDiscoveredRepositoryDto("r$it", false, "u", false, null) }

        assertThat(read("discover_repositories", "kind" to "user", "owner" to "x", "token_name" to "work"))
            .contains("lists no repositories")
        assertThat(read("discover_repositories", "kind" to "user", "owner" to "x", "token_name" to "work", "page" to 1))
            .contains("ask again with page 2")
    }

    @Test
    fun `project sources list this project's repositories and say which are shared`() {
        f.connected("acme", "app", linkedHere = true, others = 2)
        f.connected("acme", "solo", linkedHere = true, others = 0)
        every { f.githubRepositoryApi.getSourceInstances(f.projectId) } returns
            listOf(
                GithubSourceInstanceDto(
                    java.util.UUID.randomUUID(),
                    "acme",
                    "app",
                    "CONNECTED",
                    true,
                    Instant.parse("2026-09-01T10:00:00Z"),
                    null,
                    null,
                ),
                GithubSourceInstanceDto(
                    java.util.UUID.randomUUID(),
                    "acme",
                    "solo",
                    "DISABLED",
                    false,
                    null,
                    null,
                    null,
                ),
            )

        val text = read("list_project_sources")

        assertThat(text).contains(
            "acme/app — connected, enabled, commits last synced 2026-09-01 — shared with 2 other projects",
            "acme/solo — disabled, disabled",
        )
        assertThat(text).doesNotContain("acme/solo — disabled, disabled — shared")
    }

    @Test
    fun `a project with nothing connected says so`() {
        every { f.githubRepositoryApi.getSourceInstances(f.projectId) } returns emptyList()

        assertThat(read("list_project_sources")).contains("no GitHub repository connected")
    }
}
