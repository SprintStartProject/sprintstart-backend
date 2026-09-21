package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.connectors.github.external.GithubRepositoryApi
import com.sprintstart.sprintstartbackend.connectors.github.external.GithubSourcesApi
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.assertj.core.api.Assertions.assertThat
import java.util.UUID

/** A manager with two stored GitHub tokens and a project, for the sources area's tests. */
internal class SourcesFixture {
    val githubRepositoryApi: GithubRepositoryApi = mockk(relaxed = true)
    val githubSourcesApi: GithubSourcesApi = mockk(relaxed = true)
    val scope = GithubSourcesScope(githubRepositoryApi, githubSourcesApi)

    val projectId: UUID = UUID.randomUUID()
    val context = TeamToolContext(userId = UUID.randomUUID(), authId = "auth|pm", projectId = projectId)

    init {
        every { githubSourcesApi.getTokenNames("auth|pm") } returns listOf("work", "personal")
        every { githubRepositoryApi.getRepositoryIdByOwnerAndName(any(), any()) } returns null
    }

    /** A repository that is connected, linked to this project or not, and to [others] other projects. */
    fun connected(owner: String, name: String, linkedHere: Boolean, others: Int = 0): UUID {
        val id = UUID.randomUUID()
        every { githubRepositoryApi.getRepositoryIdByOwnerAndName(owner, name) } returns id
        every { githubRepositoryApi.getRepositoryProjectIdsById(id) } returns
            (if (linkedHere) setOf(projectId) else emptySet()) + List(others) { UUID.randomUUID() }
        return id
    }

    fun call(name: String, vararg args: Pair<String, Any?>) =
        BuddyToolCallDto(id = "c1", name = name, arguments = json(*args))

    fun json(vararg args: Pair<String, Any?>): JsonObject =
        buildJsonObject {
            args.forEach { (key, value) ->
                when (value) {
                    is Number -> put(key, value)
                    is JsonElement -> put(key, value)
                    else -> put(key, JsonPrimitive(value.toString()))
                }
            }
        }

    fun repo(owner: String, name: String) = json("owner" to owner, "name" to name)

    fun proposed(draft: TeamActionDraft): TeamActionDraft.Proposed {
        assertThat(draft).isInstanceOf(TeamActionDraft.Proposed::class.java)
        return draft as TeamActionDraft.Proposed
    }

    fun refusal(draft: TeamActionDraft): String {
        assertThat(draft).isInstanceOf(TeamActionDraft.Refused::class.java)
        return (draft as TeamActionDraft.Refused).reason
    }
}
