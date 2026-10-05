package com.sprintstart.sprintstartbackend.onboarding.service

import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.JsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import java.time.Clock

/** What opening the sources area hands the model, through the same mounting the buddy uses. */
class SourcesAreaMountTest {
    private val f = SourcesFixture()

    private val handlers: List<TeamActionHandler> = listOf(
        ConnectRepositoriesAction(f.scope, f.githubSourcesApi),
        LinkRepositoryAction(f.scope, f.githubSourcesApi),
        UnlinkRepositoryAction(f.scope, f.githubSourcesApi),
        SyncRepositoryAction(f.scope, f.githubSourcesApi),
    )

    private val reads = SourcesTeamTools(f.githubSourcesApi, f.githubRepositoryApi, f.scope)

    private val proposals: BuddyProposalService = run {
        val provider: ObjectProvider<TeamActionHandler> = mockk()
        every { provider.orderedStream() } answers { handlers.stream() }
        BuddyProposalService(mockk(), mockk(), provider, Clock.systemUTC())
    }

    private fun propertyNames(schema: JsonObject): Set<String> = (schema["properties"] as? JsonObject)?.keys.orEmpty()

    @Test
    fun `opening the sources area mounts exactly its reads and actions, and none of the global operations`() {
        val mounted = proposals.actionSpecs(setOf(TeamArea.SOURCES)).map { it.name } + reads.toolSpecs().map { it.name }

        assertThat(mounted).containsExactlyInAnyOrder(
            "list_my_credential_names",
            "discover_repositories",
            "list_project_sources",
            "connect_repositories",
            "link_repository",
            "unlink_repository",
            "sync_repository",
        )
        assertThat(mounted.filter { it.contains("all") || it.contains("configure") || it.contains("credential_") })
            .containsExactly("list_my_credential_names")
    }

    @Test
    fun `no tool anywhere in the area accepts a token value`() {
        val specs = handlers.map { it.spec } + reads.toolSpecs()

        specs.forEach { spec ->
            assertThat(propertyNames(spec.parameters))
                .describedAs(spec.name)
                .allMatch { it == "token_name" || it !in setOf("token", "pat", "secret", "password", "credential") }
        }
    }

    @Test
    fun `the risk of each action matches what it does`() {
        val risks = handlers.associate { it.spec.name to it.risk.name }

        assertThat(risks)
            .containsEntry("connect_repositories", "BULK")
            .containsEntry("sync_repository", "BULK")
            .containsEntry("link_repository", "STANDARD")
            .containsEntry("unlink_repository", "DESTRUCTIVE")
    }

    @Test
    fun `no two tools in the area share a name, and every action belongs to it`() {
        val names = handlers.map { it.spec.name } + reads.toolSpecs().map { it.name }

        assertThat(names).doesNotHaveDuplicates()
        assertThat(handlers.map { it.area }).containsOnly(TeamArea.SOURCES)
    }
}
