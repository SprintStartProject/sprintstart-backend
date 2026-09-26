package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.connectors.github.external.GithubOwnerKind
import com.sprintstart.sprintstartbackend.connectors.github.external.GithubRepositoryApi
import com.sprintstart.sprintstartbackend.connectors.github.external.GithubSourceInstanceDto
import com.sprintstart.sprintstartbackend.connectors.github.external.GithubSourcesApi
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.time.ZoneOffset
import java.util.UUID

private const val DEFAULT_PAGE_SIZE = 20

private val OWNER_KINDS = mapOf("organisation" to GithubOwnerKind.ORGANISATION, "user" to GithubOwnerKind.USER)

private fun GithubSourceInstanceDto.said(): String =
    "$owner/$name — ${status.lowercase().replace('_', ' ')}, ${if (enabled) "enabled" else "disabled"}" +
        (lastCommitsSyncAt?.let { ", commits last synced ${it.atZone(ZoneOffset.UTC).toLocalDate()}" } ?: "")

/**
 * The read tools of team mode's sources area: which credentials the manager has stored, what GitHub lists
 * for an owner, and what this project has connected.
 *
 * Credentials are read as names and nothing else. Nothing here returns, or accepts, a token.
 *
 * Only GitHub is behind this so far; Jira, Confluence and uploads join the same area, and these reads,
 * when they get their tools.
 */
@Component
class SourcesTeamTools(
    private val githubSourcesApi: GithubSourcesApi,
    private val githubRepositoryApi: GithubRepositoryApi,
    private val scope: GithubSourcesScope,
) : TeamAreaTools {
    override val area = TeamArea.SOURCES

    override fun toolSpecs(): List<BuddyToolSpecDto> = listOf(
        CREDENTIAL_NAMES_SPEC,
        DISCOVER_SPEC,
        PROJECT_SOURCES_SPEC,
    )

    override fun handles(toolName: String): Boolean = toolSpecs().any { it.name == toolName }

    override fun execute(call: BuddyToolCallDto, context: TeamToolContext): String =
        when (call.name) {
            LIST_MY_CREDENTIAL_NAMES -> credentialNames(context.authId)
            DISCOVER_REPOSITORIES -> discover(call, context)
            LIST_PROJECT_SOURCES -> projectSources(context.projectId)
            else -> "Unknown tool: ${call.name}."
        }

    private fun credentialNames(authId: String): String {
        val names = githubSourcesApi.getTokenNames(authId).sorted()
        if (names.isEmpty()) {
            return "The manager has stored no GitHub token yet. One is added on the settings page, not here — " +
                "point them to it, and never ask them to paste a token into the conversation."
        }
        return "The manager's stored GitHub tokens (names only):\n" + names.joinToString("\n") { "- $it" } +
            "\nPass one of these names as token_name. Tokens are added and removed on the settings page."
    }

    /**
     * What GitHub lists for an owner, read with one of the manager's own tokens.
     *
     * A network call, and this tool interface is not suspending, so it blocks the request thread for
     * as long as GitHub takes. That is what a manager waiting for an answer is doing anyway.
     */
    private fun discover(call: BuddyToolCallDto, context: TeamToolContext): String {
        val kind = OWNER_KINDS[call.textArgument("kind").lowercase()]
            ?: return "kind must be organisation or user: the two are listed differently."
        val owner = call.textArgument("owner")
        if (owner.isEmpty()) return "An owner is needed: the organisation or user whose repositories to list."
        val token = call.textArgument("token_name")
        scope.tokenProblem(context.authId, token)?.let { return it }
        val page = call.textArgument("page").toIntOrNull()?.takeIf { it >= 0 } ?: 0

        val found = try {
            runBlocking {
                githubSourcesApi.discoverRepositories(
                    context.authId,
                    kind,
                    owner,
                    token,
                    page,
                    DEFAULT_PAGE_SIZE,
                )
            }
        } catch (e: ResponseStatusException) {
            return e.reason ?: "GitHub could not list that."
        }
        if (found.isEmpty()) {
            val none = if (page ==
                0
            ) {
                "GitHub lists no repositories for $owner with that token."
            } else {
                "No more repositories."
            }
            return none
        }
        return buildString {
            appendLine("Repositories of $owner (page $page, up to $DEFAULT_PAGE_SIZE per page):")
            found.forEach {
                append("- ${it.name}${if (it.isPrivate) " (private)" else ""}")
                if (it.alreadyConnected) append(" — already connected${if (it.enabled == false) ", disabled" else ""}")
                appendLine()
            }
            if (found.size == DEFAULT_PAGE_SIZE) append("There may be more: ask again with page ${page + 1}.")
        }.trim()
    }

    private fun projectSources(projectId: UUID): String {
        val repositories = githubRepositoryApi.getSourceInstances(projectId)
        if (repositories.isEmpty()) return "This project has no GitHub repository connected."
        return buildString {
            appendLine("GitHub repositories connected to this project:")
            repositories.forEach { repository ->
                append("- ${repository.said()}")
                val shared = scope.find(repository.owner, repository.name, projectId)?.otherProjects ?: 0
                if (shared > 0) append(" — shared with $shared other project${if (shared == 1) "" else "s"}")
                appendLine()
            }
        }.trim()
    }

    companion object {
        const val LIST_MY_CREDENTIAL_NAMES = "list_my_credential_names"
        const val DISCOVER_REPOSITORIES = "discover_repositories"
        const val LIST_PROJECT_SOURCES = "list_project_sources"

        private fun noArgs() =
            buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject { })
            }

        private val CREDENTIAL_NAMES_SPEC = BuddyToolSpecDto(
            name = LIST_MY_CREDENTIAL_NAMES,
            description = "The names of the GitHub tokens the manager has stored. Names only: you never see a " +
                "token and must never ask for one. If the manager pastes a token into the conversation, do " +
                "not use it and do not repeat it; tell them to store it on the settings page under a name. " +
                "Takes no arguments.",
            parameters = noArgs(),
        )

        private val DISCOVER_SPEC = BuddyToolSpecDto(
            name = DISCOVER_REPOSITORIES,
            description = "List the repositories GitHub shows for an organisation or a user, read with one of " +
                "the manager's own tokens, and which of them SprintStart already has. Use it before offering " +
                "to connect repositories, so the names are real. Pass a token NAME from " +
                "list_my_credential_names, never a token.",
            parameters = toolFields(
                ToolField(
                    "kind",
                    "Whether the owner is an organisation or a user.",
                    values = OWNER_KINDS.keys.toList(),
                ),
                ToolField("owner", "The organisation or user name."),
                ToolField("token_name", "The name of one of the manager's stored tokens."),
                ToolField("page", "Optional. Which page to read, from 0.", "integer"),
                required = listOf("kind", "owner", "token_name"),
            ),
        )

        private val PROJECT_SOURCES_SPEC = BuddyToolSpecDto(
            name = LIST_PROJECT_SOURCES,
            description = "The GitHub repositories connected to this project, each with whether it is enabled, " +
                "when it last synced, and how many other projects share it. Read it before offering to link, " +
                "unlink or sync one. Takes no arguments.",
            parameters = noArgs(),
        )
    }
}
