package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.connectors.github.external.GithubConnectResultDto
import com.sprintstart.sprintstartbackend.connectors.github.external.GithubRepositoryRef
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

class GithubSourcesTeamActionsTest {
    private val f = SourcesFixture()

    @Nested
    inner class Connect {
        private val action = ConnectRepositoriesAction(f.scope, f.githubSourcesApi)

        private fun connect(token: String, vararg repos: Pair<String, String>) =
            f.call(
                "connect_repositories",
                "token_name" to token,
                "repositories" to JsonArray(repos.map { f.repo(it.first, it.second) }),
            )

        @Test
        fun `is a bulk change in the sources area`() {
            assertThat(action.area).isEqualTo(TeamArea.SOURCES)
            assertThat(action.risk).isEqualTo(BuddyProposalRisk.BULK)
        }

        @Test
        fun `says which are new and which are only linked, and that the token stays with the connection`() {
            f.connected("acme", "shared", linkedHere = false, others = 1)

            val draft = f.proposed(action.draft(connect("work", "acme" to "fresh", "acme" to "shared"), f.context))

            assertThat(draft.label).isEqualTo("Connect 2 repositories")
            assertThat(draft.preview).contains(
                "using your token “work”",
                "acme/fresh — new: starts fetching",
                "acme/shared — already connected for another project: linked here, nothing fetched again",
                "GitHub is asked whether your token can see each one",
                "keeps using your token “work” for its nightly updates",
            )
        }

        @Test
        fun `a repository already on this project is left out and named`() {
            f.connected("acme", "mine", linkedHere = true)

            val draft = f.proposed(action.draft(connect("work", "acme" to "mine", "acme" to "fresh"), f.context))

            assertThat(draft.label).isEqualTo("Connect acme/fresh")
            assertThat(draft.preview).contains("Left out, already on this project: acme/mine")
            assertThat(draft.params.objectArray("repositories")).hasSize(1)
        }

        @Test
        fun `everything already being here is refused`() {
            f.connected("acme", "mine", linkedHere = true)

            assertThat(f.refusal(action.draft(connect("work", "acme" to "mine"), f.context)))
                .contains("already connected to this project")
        }

        @Test
        fun `a token name that is not the manager's is refused`() {
            assertThat(f.refusal(action.draft(connect("nope", "acme" to "app"), f.context)))
                .contains("no GitHub token with that name")
        }

        @Test
        fun `a pasted token is refused, and appears in no refusal, preview or stored params`() {
            val pasted = "ghp_0123456789abcdefghijklmnopqrstuvwxyz"

            val reason = f.refusal(action.draft(connect(pasted, "acme" to "app"), f.context))

            assertThat(reason).contains("Never paste a token").doesNotContain(pasted)
        }

        @Test
        fun `no parameter takes a token value`() {
            val names = (action.spec.parameters["properties"] as JsonObject).keys

            assertThat(names).containsExactlyInAnyOrder("token_name", "repositories")
            assertThat(action.spec.description).contains("NAME").contains("Never pass a token")
        }

        @Test
        fun `nothing, or too many, is refused`() {
            assertThat(f.refusal(action.draft(connect("work"), f.context))).contains("No repository was given")

            val many = (1..21).map { "acme" to "r$it" }.toTypedArray()
            assertThat(f.refusal(action.draft(connect("work", *many), f.context))).contains("At most 20")
        }

        @Test
        fun `a confirm is turned down once everything is on the project`() {
            f.connected("acme", "app", linkedHere = true)
            val params = f.json("token_name" to "work", "repositories" to JsonArray(listOf(f.repo("acme", "app"))))

            assertThat(action.recheck(params, f.context)).contains("has been connected to this project since")
        }

        @Test
        fun `a confirm is turned down when the token was removed since`() {
            val params = f.json("token_name" to "gone", "repositories" to JsonArray(listOf(f.repo("acme", "app"))))

            assertThat(action.recheck(params, f.context)).contains("no GitHub token with that name")
        }

        @Test
        fun `performing reports each repository, and one failing does not hide the others`() =
            runTest {
                val ok = GithubRepositoryRef("acme", "ok")
                val bad = GithubRepositoryRef("acme", "bad")
                coEvery {
                    f.githubSourcesApi.connectRepositories(
                        "auth|pm",
                        f.projectId,
                        "work",
                        listOf(ok, bad),
                    )
                } returns
                    listOf(
                        GithubConnectResultDto(ok, reused = false, failure = null),
                        GithubConnectResultDto(bad, reused = false, failure = "Repository acme/bad not found"),
                    )
                val params = f.json(
                    "token_name" to "work",
                    "repositories" to JsonArray(listOf(f.repo("acme", "ok"), f.repo("acme", "bad"))),
                )

                val result = action.perform(params, f.context)

                assertThat(
                    result,
                ).contains(
                    "Connected acme/ok",
                    "data-ingestion page",
                    "Not connected: acme/bad — Repository acme/bad not found",
                )
            }

        @Test
        fun `performing when none connect fails with the reasons`() =
            runTest {
                val bad = GithubRepositoryRef("acme", "bad")
                coEvery { f.githubSourcesApi.connectRepositories(any(), any(), any(), any()) } returns
                    listOf(GithubConnectResultDto(bad, reused = false, failure = "Repository acme/bad not found"))
                val params = f.json("token_name" to "work", "repositories" to JsonArray(listOf(f.repo("acme", "bad"))))

                assertThat(runCatching { action.perform(params, f.context) }.exceptionOrNull())
                    .hasMessageContaining("None could be connected")
                    .hasMessageContaining("acme/bad")
            }

        @Test
        fun `linking only what was already connected says nothing about fetching`() =
            runTest {
                val shared = GithubRepositoryRef("acme", "shared")
                coEvery { f.githubSourcesApi.connectRepositories(any(), any(), any(), any()) } returns
                    listOf(GithubConnectResultDto(shared, reused = true, failure = null))
                val params = f.json(
                    "token_name" to "work",
                    "repositories" to JsonArray(listOf(f.repo("acme", "shared"))),
                )

                assertThat(action.perform(params, f.context)).doesNotContain("fetch")
            }

        @Test
        fun `drafting connects nothing`() {
            action.draft(connect("work", "acme" to "app"), f.context)

            coVerify(exactly = 0) { f.githubSourcesApi.connectRepositories(any(), any(), any(), any()) }
        }
    }

    @Nested
    inner class Link {
        private val action = LinkRepositoryAction(f.scope, f.githubSourcesApi)

        @Test
        fun `is a standard change`() {
            assertThat(action.risk).isEqualTo(BuddyProposalRisk.STANDARD)
        }

        @Test
        fun `previews what a link reaches, that nothing is fetched, and that GitHub is asked`() {
            f.connected("acme", "app", linkedHere = false, others = 2)

            val draft = f.proposed(
                action.draft(f.call("link_repository", "owner" to "acme", "name" to "app"), f.context),
            )

            assertThat(draft.preview).contains(
                "Link acme/app to this project",
                "reach this project's hires and the buddy",
                "Nothing is fetched again",
                "asked whether you can see it",
                "2 other projects share this repository",
            )
        }

        @Test
        fun `a repository that is not connected is pointed at connect`() {
            assertThat(
                f.refusal(action.draft(f.call("link_repository", "owner" to "acme", "name" to "app"), f.context)),
            ).contains("not connected to SprintStart", "connect_repositories")
        }

        @Test
        fun `a repository already linked is refused`() {
            f.connected("acme", "app", linkedHere = true)

            assertThat(
                f.refusal(action.draft(f.call("link_repository", "owner" to "acme", "name" to "app"), f.context)),
            ).contains("already linked")
        }

        @Test
        fun `a call that names no repository is refused`() {
            assertThat(f.refusal(action.draft(f.call("link_repository", "owner" to "acme"), f.context)))
                .contains("owner and name")
        }

        @Test
        fun `a confirm is turned down when it was linked since or is gone`() {
            f.connected("acme", "app", linkedHere = true)
            assertThat(action.recheck(f.repo("acme", "app"), f.context)).contains("linked to this project since")

            assertThat(action.recheck(f.repo("acme", "other"), f.context)).contains("no longer connected")
        }

        @Test
        fun `performing links by the connection's id for the manager`() =
            runTest {
                val id = f.connected("acme", "app", linkedHere = false)
                coEvery { f.githubSourcesApi.linkRepository("auth|pm", f.projectId, id) } returns setOf(f.projectId)

                action.perform(f.repo("acme", "app"), f.context)

                coVerify { f.githubSourcesApi.linkRepository("auth|pm", f.projectId, id) }
            }

        @Test
        fun `a refusal from GitHub's visibility check is passed on`() =
            runTest {
                val id = f.connected("acme", "secret", linkedHere = false)
                coEvery { f.githubSourcesApi.linkRepository(any(), any(), id) } throws
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Repository acme/secret not found")

                assertThat(runCatching { action.perform(f.repo("acme", "secret"), f.context) }.exceptionOrNull())
                    .hasMessageContaining("not found")
            }
    }

    @Nested
    inner class Unlink {
        private val action = UnlinkRepositoryAction(f.scope, f.githubSourcesApi)

        private fun unlink(name: String) = f.call("unlink_repository", "owner" to "acme", "name" to name)

        @Test
        fun `is destructive`() {
            assertThat(action.risk).isEqualTo(BuddyProposalRisk.DESTRUCTIVE)
        }

        @Test
        fun `says only this project goes when others use it`() {
            f.connected("acme", "app", linkedHere = true, others = 1)

            val draft = f.proposed(action.draft(unlink("app"), f.context))

            assertThat(draft.preview).contains(
                "Take acme/app off this project",
                "leave this project's starter-work pool",
                "The connection stays, and one other project keeps it",
            )
        }

        @Test
        fun `says it stays connected but reaches nobody when nothing else uses it`() {
            f.connected("acme", "app", linkedHere = true, others = 0)

            assertThat(f.proposed(action.draft(unlink("app"), f.context)).preview)
                .contains("stays connected but reaches no project")
        }

        @Test
        fun `counts several other projects`() {
            f.connected("acme", "app", linkedHere = true, others = 3)

            assertThat(f.proposed(action.draft(unlink("app"), f.context)).preview)
                .contains("3 other projects keep it")
        }

        @Test
        fun `a repository not linked to this project is refused, even when it is connected elsewhere`() {
            f.connected("acme", "theirs", linkedHere = false, others = 1)

            assertThat(f.refusal(action.draft(unlink("theirs"), f.context))).contains("not linked to this project")
            assertThat(f.refusal(action.draft(unlink("unknown"), f.context))).contains("not linked to this project")
        }

        @Test
        fun `a confirm is turned down when it is no longer linked`() {
            f.connected("acme", "app", linkedHere = false)

            assertThat(action.recheck(f.repo("acme", "app"), f.context)).contains("no longer linked")
        }

        @Test
        fun `performing unlinks this project only, by the connection's id`() =
            runTest {
                val id = f.connected("acme", "app", linkedHere = true, others = 1)
                every { f.githubSourcesApi.unlinkRepository("auth|pm", f.projectId, id) } returns emptySet()

                action.perform(f.repo("acme", "app"), f.context)

                verify { f.githubSourcesApi.unlinkRepository("auth|pm", f.projectId, id) }
            }

        @Test
        fun `performing for a repository that left the project fails instead of unlinking something else`() =
            runTest {
                f.connected("acme", "app", linkedHere = false)

                assertThat(runCatching { action.perform(f.repo("acme", "app"), f.context) }.exceptionOrNull())
                    .hasMessageContaining("not linked to this project")
                verify(exactly = 0) { f.githubSourcesApi.unlinkRepository(any(), any(), any()) }
            }
    }

    @Nested
    inner class Sync {
        private val action = SyncRepositoryAction(f.scope, f.githubSourcesApi)

        private fun sync(name: String) = f.call("sync_repository", "owner" to "acme", "name" to name)

        @Test
        fun `is a bulk change`() {
            assertThat(action.risk).isEqualTo(BuddyProposalRisk.BULK)
        }

        @Test
        fun `says it runs in the background, and that the projects sharing it get the update`() {
            f.connected("acme", "app", linkedHere = true, others = 2)

            val draft = f.proposed(action.draft(sync("app"), f.context))

            assertThat(draft.preview).contains(
                "Fetch the latest",
                "runs in the background",
                "data-ingestion page",
                "2 other projects share this repository. They get the update too.",
            )
        }

        @Test
        fun `a repository nobody else shares has no shared note`() {
            f.connected("acme", "app", linkedHere = true)

            assertThat(f.proposed(action.draft(sync("app"), f.context)).preview).doesNotContain("share")
        }

        @Test
        fun `a repository not linked to this project cannot be synced, though it is connected`() {
            f.connected("acme", "theirs", linkedHere = false, others = 1)

            assertThat(f.refusal(action.draft(sync("theirs"), f.context))).contains("not linked to this project")
            assertThat(f.refusal(action.draft(sync("unknown"), f.context))).contains("not linked to this project")
        }

        @Test
        fun `a confirm is turned down when it left the project since`() {
            f.connected("acme", "app", linkedHere = false)

            assertThat(action.recheck(f.repo("acme", "app"), f.context)).contains("no longer linked")
        }

        @Test
        fun `performing starts the sync of that repository`() =
            runTest {
                every { f.githubSourcesApi.syncRepository(GithubRepositoryRef("acme", "app")) } returns
                    UUID.randomUUID()

                val result = action.perform(f.repo("acme", "app"), f.context)

                verify { f.githubSourcesApi.syncRepository(GithubRepositoryRef("acme", "app")) }
                assertThat(result).contains("fetching in the background", "data-ingestion page")
            }
    }
}
