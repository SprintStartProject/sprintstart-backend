package com.sprintstart.sprintstartbackend.connectors.github.models.api.responses

import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositoryConfig
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubUser
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubUserPat
import kotlin.test.Test

class GetRepositoryConfigResponseTest {
    private val user = GithubUser(
        id = GithubUserPat("auth-id", "token-name"),
        token = "test-token",
    )

    @Test
    fun `of() converts correctly of a GithubRepositoryConfig`() {
        val repo = repoConnection("owner", "repo")
        val config = GithubRepositoryConfig(id = repo.id, repository = repo)

        val result = GetRepositoryConfigResponse.of(config)

        assert(result.id == repo.id)
        assert(result.repositoryOwner == repo.owner)
        assert(result.repositoryName == repo.name)
        assert(result.autoUpdate == config.autoUpdate)
        assert(result.spec == config.spec)
        assert(result.schedule == config.schedule)
        assert(result.nextSyncAt == config.nextSyncAt)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun repoConnection(owner: String, name: String) = GithubRepositoryConnection(
        owner = owner,
        name = name,
        user = user,
    )
}
