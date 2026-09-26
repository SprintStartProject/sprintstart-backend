package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.connectors.github.external.GithubRepositoryRef
import com.sprintstart.sprintstartbackend.connectors.github.external.GithubSourcesApi
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

/*
 * The GitHub actions of team mode's sources area.
 *
 * A repository is one connection however many projects use it, so linking, unlinking and syncing reach
 * past the turn's project. Each action therefore resolves its repository through GithubSourcesScope — it
 * must be linked to the turn's project to be unlinked or synced — and says in its preview when other
 * projects share it.
 *
 * Credentials are names. No action takes a token, and a token name that looks like one is refused without
 * being repeated.
 */

private const val MAX_REPOSITORIES = 20

private const val INGESTION_PAGE = "the data-ingestion page"

private const val NAME_A_REPOSITORY = "Give the repository as owner and name."

private const val NOT_LINKED_HERE =
    "That repository is not linked to this project. Call list_project_sources for the ones that are."

private const val WHAT_A_LINK_REACHES =
    "Its files, commits, issues and pull requests reach this project's hires and the buddy, and its issues " +
        "can be starter work here."

private fun repositoryArguments() =
    toolFields(
        ToolField("owner", "The repository owner, as in owner/name."),
        ToolField("name", "The repository name, as in owner/name."),
        required = listOf("owner", "name"),
    )

/** The repository a tool call names, or null when it names none. */
private fun BuddyToolCallDto.repository(): GithubRepositoryRef? = arguments.repositoryOrNull()

private fun JsonObject.repositoryOrNull(): GithubRepositoryRef? =
    GithubRepositoryRef(text("owner"), text("name")).takeIf { it.owner.isNotEmpty() && it.name.isNotEmpty() }

/** The repository a stored proposal names; it was validated when it was drafted. */
private fun JsonObject.repository(): GithubRepositoryRef = requireNotNull(repositoryOrNull())

private fun GithubRepositoryRef.stored(): JsonObject =
    buildJsonObject {
        put("owner", owner)
        put("name", name)
    }

private fun plural(count: Int, singular: String, plural: String = "${singular}s") =
    "$count ${if (count == 1) singular else plural}"

/** Offers to connect repositories to the project, each on its own. */
@Component
class ConnectRepositoriesAction(
    private val scope: GithubSourcesScope,
    private val githubSourcesApi: GithubSourcesApi,
) : TeamActionHandler {
    override val area = TeamArea.SOURCES
    override val risk = BuddyProposalRisk.BULK
    override val spec = BuddyToolSpecDto(
        name = "connect_repositories",
        description = "Offer to connect GitHub repositories to this project, using one of the manager's stored " +
            "tokens by NAME from list_my_credential_names. Never pass a token, and refuse one pasted into the " +
            "conversation. A new repository starts fetching its code, commits, issues and pull requests in the " +
            "background; one already connected for another project is only linked. Use discover_repositories " +
            "first so the names are real. This does NOT connect anything by itself; the manager confirms.",
        parameters = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("token_name") {
                    put("type", "string")
                    put("description", "The NAME of one of the manager's stored tokens.")
                }
                putJsonObject("repositories") {
                    put("type", "array")
                    put("description", "Up to $MAX_REPOSITORIES repositories.")
                    put("items", repositoryArguments())
                }
            }
            putJsonArray("required") {
                add("token_name")
                add("repositories")
            }
        },
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val token = call.textArgument("token_name")
        scope.tokenProblem(context.authId, token)?.let { return TeamActionDraft.Refused(it) }

        val asked = call.arguments
            .objectArray("repositories")
            .mapNotNull { it.repositoryOrNull() }
            .distinct()
        if (asked.isEmpty()) return TeamActionDraft.Refused("No repository was given. $NAME_A_REPOSITORY")
        if (asked.size > MAX_REPOSITORIES) {
            return TeamActionDraft.Refused("At most $MAX_REPOSITORIES at a time; offer the rest afterwards.")
        }

        val states = asked.map { it to scope.find(it.owner, it.name, context.projectId) }
        val toConnect = states.filter { it.second?.linkedHere != true }
        val alreadyHere = states.filter { it.second?.linkedHere == true }.map { it.first }
        if (toConnect.isEmpty()) {
            return TeamActionDraft.Refused("Every one of those is already connected to this project.")
        }

        return TeamActionDraft.Proposed(
            params = buildJsonObject {
                put("token_name", token)
                putJsonArray("repositories") { toConnect.forEach { add(it.first.stored()) } }
            },
            label = labelFor(toConnect.map { it.first }),
            preview = previewOf(token, toConnect, alreadyHere),
        )
    }

    private fun labelFor(repositories: List<GithubRepositoryRef>): String =
        if (repositories.size == 1) {
            "Connect ${scope.label(repositories.single())}"
        } else {
            "Connect ${repositories.size} repositories"
        }

    private fun previewOf(
        token: String,
        toConnect: List<Pair<GithubRepositoryRef, ConnectedRepository?>>,
        alreadyHere: List<GithubRepositoryRef>,
    ): String =
        buildString {
            appendLine("Connect to this project, using your token “$token”:")
            toConnect.forEach { (ref, existing) ->
                val effect = if (existing == null) NEW_REPOSITORY else SHARED_REPOSITORY
                appendLine("- ${scope.label(ref)} — $effect")
            }
            if (alreadyHere.isNotEmpty()) {
                appendLine("Left out, already on this project: ${alreadyHere.joinToString(", ") { scope.label(it) }}.")
            }
            appendLine()
            appendLine(ASKED_OF_GITHUB)
            append("A new repository keeps using your token “$token” for its nightly updates.")
        }.trim()

    override fun recheck(params: JsonObject, context: TeamToolContext): String? {
        scope.tokenProblem(context.authId, params.text("token_name"))?.let { return it }
        val allLinkedNow = params
            .objectArray("repositories")
            .map { it.repository() }
            .all { scope.find(it.owner, it.name, context.projectId)?.linkedHere == true }
        return ALL_CONNECTED_SINCE.takeIf { allLinkedNow }
    }

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        val results = githubSourcesApi.connectRepositories(
            context.authId,
            context.projectId,
            params.text("token_name"),
            params.objectArray("repositories").map { it.repository() },
        )
        val failed = results.filter { it.failure != null }
        val failures = failed.joinToString("; ") { "${scope.label(it.repository)} — ${it.failure}" }
        if (failed.size == results.size) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "None could be connected: $failures")
        }

        val connected = results.filter { it.failure == null }
        return buildString {
            append("Connected ${connected.joinToString(", ") { scope.label(it.repository) }}. ")
            if (connected.any { !it.reused }) append("New ones fetch in the background; follow it on $INGESTION_PAGE. ")
            if (failed.isNotEmpty()) append("Not connected: $failures.")
        }.trim()
    }

    private companion object {
        const val NEW_REPOSITORY = "new: starts fetching its files, commits, issues and pull requests"
        const val SHARED_REPOSITORY = "already connected for another project: linked here, nothing fetched again"
        const val ASKED_OF_GITHUB =
            "GitHub is asked whether your token can see each one when you confirm; one it cannot is skipped and " +
                "reported."
        const val ALL_CONNECTED_SINCE =
            "Every one of those has been connected to this project since, so nothing was changed."
    }
}

/** Offers to link an already-connected repository to the project. */
@Component
class LinkRepositoryAction(
    private val scope: GithubSourcesScope,
    private val githubSourcesApi: GithubSourcesApi,
) : TeamActionHandler {
    override val area = TeamArea.SOURCES
    override val risk = BuddyProposalRisk.STANDARD
    override val spec = BuddyToolSpecDto(
        name = "link_repository",
        description = "Offer to link a repository that is already connected for another project to this one, " +
            "without fetching anything again. Its material then reaches this project. If it is not connected " +
            "yet, offer connect_repositories instead. This does NOT link anything by itself; the manager " +
            "confirms.",
        parameters = repositoryArguments(),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val ref = call.repository() ?: return TeamActionDraft.Refused(NAME_A_REPOSITORY)
        val repository = scope.find(ref.owner, ref.name, context.projectId)
            ?: return TeamActionDraft.Refused(
                "${scope.label(ref)} is not connected to SprintStart. Offer connect_repositories with one of " +
                    "the manager's tokens.",
            )
        if (repository.linkedHere) {
            return TeamActionDraft.Refused("${scope.label(ref)} is already linked to this project.")
        }

        return TeamActionDraft.Proposed(
            params = ref.stored(),
            label = "Link ${scope.label(ref)}",
            preview = buildString {
                appendLine("Link ${scope.label(ref)} to this project.")
                appendLine()
                appendLine(WHAT_A_LINK_REACHES)
                appendLine("Nothing is fetched again; it is the connection the other projects already use.")
                append(ASKED_OF_GITHUB)
                scope
                    .sharedNote(repository, "Nothing changes for them.")
                    .takeIf { it.isNotEmpty() }
                    ?.let { append("\n\n$it") }
            }.trim(),
        )
    }

    override fun recheck(params: JsonObject, context: TeamToolContext): String? {
        val ref = params.repository()
        val repository = scope.find(ref.owner, ref.name, context.projectId)
            ?: return "${scope.label(ref)} is no longer connected, so nothing was changed."
        val linkedSince = "${scope.label(ref)} was linked to this project since, so nothing was changed."
        return linkedSince.takeIf { repository.linkedHere }
    }

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        val ref = params.repository()
        val repository = scope.find(ref.owner, ref.name, context.projectId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "${scope.label(ref)} is no longer connected.")
        githubSourcesApi.linkRepository(context.authId, context.projectId, repository.id)
        return "Linked. ${scope.label(ref)} is on this project now."
    }

    private companion object {
        const val ASKED_OF_GITHUB =
            "GitHub is asked whether you can see it with one of your own tokens when you confirm. If you cannot, " +
                "it is refused as not found."
    }
}

/** Offers to take the project off a repository, leaving the connection for whoever else uses it. */
@Component
class UnlinkRepositoryAction(
    private val scope: GithubSourcesScope,
    private val githubSourcesApi: GithubSourcesApi,
) : TeamActionHandler {
    override val area = TeamArea.SOURCES
    override val risk = BuddyProposalRisk.DESTRUCTIVE
    override val spec = BuddyToolSpecDto(
        name = "unlink_repository",
        description = "Offer to take a repository off this project. Only this project's link goes: the " +
            "connection stays for any other project using it. Read list_project_sources first. This does NOT " +
            "unlink anything by itself; the manager confirms.",
        parameters = repositoryArguments(),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val ref = call.repository() ?: return TeamActionDraft.Refused(NAME_A_REPOSITORY)
        val repository = scope.find(ref.owner, ref.name, context.projectId)?.takeIf { it.linkedHere }
            ?: return TeamActionDraft.Refused(NOT_LINKED_HERE)

        return TeamActionDraft.Proposed(
            params = ref.stored(),
            label = "Unlink ${scope.label(ref)}",
            preview = buildString {
                appendLine("Take ${scope.label(ref)} off this project.")
                appendLine()
                appendLine(
                    "Its files, commits, issues and pull requests stop reaching this project's hires and the " +
                        "buddy, and its issues leave this project's starter-work pool.",
                )
                append(whatRemains(repository.otherProjects))
            }.trim(),
        )
    }

    private fun whatRemains(otherProjects: Int): String =
        when (otherProjects) {
            0 -> "Nothing else uses it, so it stays connected but reaches no project."
            1 -> "The connection stays, and one other project keeps it."
            else -> "The connection stays, and ${plural(otherProjects, "other project")} keep it."
        }

    override fun recheck(params: JsonObject, context: TeamToolContext): String? {
        val ref = params.repository()
        val goneSince = "${scope.label(ref)} is no longer linked to this project, so nothing was changed."
        return goneSince.takeIf { scope.find(ref.owner, ref.name, context.projectId)?.linkedHere != true }
    }

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        val ref = params.repository()
        val repository = scope.find(ref.owner, ref.name, context.projectId)?.takeIf { it.linkedHere }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, NOT_LINKED_HERE)
        githubSourcesApi.unlinkRepository(context.authId, context.projectId, repository.id)
        return "Unlinked. ${scope.label(ref)} is off this project."
    }
}

/** Offers to fetch a repository's latest state now. */
@Component
class SyncRepositoryAction(
    private val scope: GithubSourcesScope,
    private val githubSourcesApi: GithubSourcesApi,
) : TeamActionHandler {
    override val area = TeamArea.SOURCES
    override val risk = BuddyProposalRisk.BULK
    override val spec = BuddyToolSpecDto(
        name = "sync_repository",
        description = "Offer to fetch the latest files, commits, issues and pull requests of a repository " +
            "linked to this project, now instead of at the nightly run. It runs in the background. Only a " +
            "repository linked to this project can be synced. Read list_project_sources first. This does NOT " +
            "start anything by itself; the manager confirms.",
        parameters = repositoryArguments(),
    )

    override fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft {
        val ref = call.repository() ?: return TeamActionDraft.Refused(NAME_A_REPOSITORY)
        val repository = scope.find(ref.owner, ref.name, context.projectId)?.takeIf { it.linkedHere }
            ?: return TeamActionDraft.Refused("$NOT_LINKED_HERE It cannot be synced from here otherwise.")

        return TeamActionDraft.Proposed(
            params = ref.stored(),
            label = "Sync ${scope.label(ref)}",
            preview = buildString {
                appendLine("Fetch the latest files, commits, issues and pull requests of ${scope.label(ref)} now.")
                append("It runs in the background and you can follow it on $INGESTION_PAGE.")
                scope
                    .sharedNote(repository, "They get the update too.")
                    .takeIf { it.isNotEmpty() }
                    ?.let { append("\n\n$it") }
            }.trim(),
        )
    }

    override fun recheck(params: JsonObject, context: TeamToolContext): String? {
        val ref = params.repository()
        val goneSince = "${scope.label(ref)} is no longer linked to this project, so nothing was changed."
        return goneSince.takeIf { scope.find(ref.owner, ref.name, context.projectId)?.linkedHere != true }
    }

    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        val ref = params.repository()
        githubSourcesApi.syncRepository(ref)
        return "Started. ${scope.label(ref)} is fetching in the background; follow it on $INGESTION_PAGE."
    }
}
