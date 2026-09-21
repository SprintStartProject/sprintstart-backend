package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.connectors.github.external.GithubRepositoryApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class StarterWorkScopeTest {
    private val githubRepositoryApi: GithubRepositoryApi = mockk()
    private val scope = StarterWorkScope(githubRepositoryApi)

    private val projectId = UUID.randomUUID()

    private fun linked(owner: String, name: String, vararg projects: UUID): UUID {
        val repositoryId = UUID.randomUUID()
        every { githubRepositoryApi.getRepositoryIdByOwnerAndName(owner, name) } returns repositoryId
        every { githubRepositoryApi.getRepositoryProjectIdsById(repositoryId) } returns projects.toSet()
        return repositoryId
    }

    @Test
    fun `reads owner and name from a GitHub source id and nothing from other shapes`() {
        assertThat(githubRepositoryOf("github:acme/shop:ISSUE:42")).isEqualTo(GithubRepositoryName("acme", "shop"))
        assertThat(githubRepositoryOf("authored:${UUID.randomUUID()}")).isNull()
        assertThat(githubRepositoryOf("jira:SHOP-42")).isNull()
        assertThat(githubRepositoryOf("github:acme:ISSUE:42")).isNull()
        assertThat(githubRepositoryOf("github:acme/shop")).isNull()
        assertThat(githubRepositoryOf("github:/shop:ISSUE:42")).isNull()
    }

    @Test
    fun `covers a task whose repository is linked to the project, among others`() {
        linked("acme", "shop", UUID.randomUUID(), projectId)

        assertThat(scope.covers("github:acme/shop:ISSUE:42", projectId)).isTrue()
    }

    @Test
    fun `does not cover a task whose repository is linked only to other projects`() {
        linked("acme", "shop", UUID.randomUUID())

        assertThat(scope.covers("github:acme/shop:ISSUE:42", projectId)).isFalse()
    }

    /** A hand-authored task has no repository to scope it by, so it stays with an admin. */
    @Test
    fun `never covers a source that is not a GitHub issue, without looking anything up`() {
        assertThat(scope.covers("authored:${UUID.randomUUID()}", projectId)).isFalse()

        verify(exactly = 0) { githubRepositoryApi.getRepositoryIdByOwnerAndName(any(), any()) }
    }

    @Test
    fun `does not cover a repository that is not connected`() {
        every { githubRepositoryApi.getRepositoryIdByOwnerAndName("acme", "shop") } returns null

        assertThat(scope.covers("github:acme/shop:ISSUE:42", projectId)).isFalse()
    }

    @Test
    fun `does not cover a repository disconnected between its two lookups`() {
        val repositoryId = UUID.randomUUID()
        every { githubRepositoryApi.getRepositoryIdByOwnerAndName("acme", "shop") } returns repositoryId
        every { githubRepositoryApi.getRepositoryProjectIdsById(repositoryId) } throws NoSuchElementException()

        assertThat(scope.covers("github:acme/shop:ISSUE:42", projectId)).isFalse()
    }

    @Test
    fun `filters a list to the project, looking each repository up once`() {
        val shop = linked("acme", "shop", projectId)
        linked("acme", "billing", UUID.randomUUID())
        val sources = listOf(
            "github:acme/shop:ISSUE:1",
            "github:acme/billing:ISSUE:2",
            "github:acme/shop:ISSUE:3",
            "authored:${UUID.randomUUID()}",
        )

        val onProject = scope.onProject(sources, projectId) { it }

        assertThat(onProject).containsExactly("github:acme/shop:ISSUE:1", "github:acme/shop:ISSUE:3")
        verify(exactly = 1) { githubRepositoryApi.getRepositoryProjectIdsById(shop) }
    }

    /** Hires keep the shared pool of work that never had a repository, plus their own project's. */
    @Test
    fun `gives a hire this project's work and the sources that have no repository`() {
        linked("acme", "shop", projectId, UUID.randomUUID())
        val billing = linked("acme", "billing", UUID.randomUUID())
        val authored = "authored:${UUID.randomUUID()}"
        val sources = listOf(
            "github:acme/shop:ISSUE:1",
            "github:acme/billing:ISSUE:2",
            "github:acme/billing:ISSUE:3",
            authored,
            "jira:SHOP-4",
        )

        val forHires = scope.forHiresOn(sources, projectId) { it }

        assertThat(forHires).containsExactly("github:acme/shop:ISSUE:1", authored, "jira:SHOP-4")
        verify(exactly = 1) { githubRepositoryApi.getRepositoryProjectIdsById(billing) }
    }

    /**
     * A repository unlinked after one of its issues was promoted must not fall back to the shared
     * pool, or that issue would reach hires on unrelated projects.
     */
    @Test
    fun `keeps a GitHub task from every hire when its repository has no project`() {
        linked("acme", "unlinked")

        assertThat(scope.forHiresOn(listOf("github:acme/unlinked:ISSUE:1"), projectId) { it }).isEmpty()
    }

    @Test
    fun `keeps a GitHub task from every hire when its repository is not connected`() {
        every { githubRepositoryApi.getRepositoryIdByOwnerAndName("acme", "gone") } returns null

        assertThat(scope.forHiresOn(listOf("github:acme/gone:ISSUE:1"), projectId) { it }).isEmpty()
    }

    @Test
    fun `keeps a GitHub task from every hire when its repository disconnects mid-read`() {
        val repositoryId = UUID.randomUUID()
        every { githubRepositoryApi.getRepositoryIdByOwnerAndName("acme", "shop") } returns repositoryId
        every { githubRepositoryApi.getRepositoryProjectIdsById(repositoryId) } throws NoSuchElementException()

        assertThat(scope.forHiresOn(listOf("github:acme/shop:ISSUE:1"), projectId) { it }).isEmpty()
    }

    @Test
    fun `keeps a malformed GitHub source from every hire, without looking anything up`() {
        val malformed = listOf("github:acme:ISSUE:1", "github:acme/shop", "github:/shop:ISSUE:2")

        assertThat(scope.forHiresOn(malformed, projectId) { it }).isEmpty()

        verify(exactly = 0) { githubRepositoryApi.getRepositoryIdByOwnerAndName(any(), any()) }
    }
}
