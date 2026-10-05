package com.sprintstart.sprintstartbackend.onboarding.service

import io.mockk.every
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class GithubSourcesScopeTest {
    private val f = SourcesFixture()

    @Test
    fun `a repository that is not connected is not found`() {
        assertThat(f.scope.find("acme", "app", f.projectId)).isNull()
    }

    @Test
    fun `says whether it is linked here and how many other projects share it`() {
        f.connected("acme", "app", linkedHere = true, others = 2)
        f.connected("acme", "lib", linkedHere = false, others = 1)

        val app = f.scope.find("acme", "app", f.projectId)!!
        val lib = f.scope.find("acme", "lib", f.projectId)!!

        assertThat(app.linkedHere).isTrue()
        assertThat(app.otherProjects).isEqualTo(2)
        assertThat(lib.linkedHere).isFalse()
        assertThat(lib.otherProjects).isEqualTo(1)
    }

    @Test
    fun `a repository that disappears between the two lookups is not found`() {
        val id = UUID.randomUUID()
        every { f.githubRepositoryApi.getRepositoryIdByOwnerAndName("acme", "app") } returns id
        every { f.githubRepositoryApi.getRepositoryProjectIdsById(id) } throws NoSuchElementException()

        assertThat(f.scope.find("acme", "app", f.projectId)).isNull()
    }

    @Test
    fun `a name of one of the manager's tokens passes`() {
        assertThat(f.scope.tokenProblem("auth|pm", "work")).isNull()
    }

    @Test
    fun `a name that is not one of their tokens is refused`() {
        assertThat(f.scope.tokenProblem("auth|pm", "somebody-elses")).contains("no GitHub token with that name")
        assertThat(f.scope.tokenProblem("auth|pm", "")).contains("A token name is needed")
    }

    @Test
    fun `something shaped like a token is refused and never repeated`() {
        listOf("ghp_abcdefghijklmnopqrstuvwxyz0123456789", "github_pat_11ABCDEFG0123456789_xyz", "gho_secret", "GHS_x")
            .forEach { pasted ->
                val problem = f.scope.tokenProblem("auth|pm", pasted)

                assertThat(problem).describedAs(pasted).contains("Never paste a token")
                assertThat(problem).describedAs(pasted).doesNotContain(pasted)
            }
    }

    @Test
    fun `the shared note is empty for a repository nobody else uses`() {
        f.connected("acme", "app", linkedHere = true, others = 0)

        assertThat(f.scope.sharedNote(f.scope.find("acme", "app", f.projectId)!!, "They get the update too.")).isEmpty()
    }

    @Test
    fun `the shared note counts the other projects`() {
        f.connected("acme", "one", linkedHere = true, others = 1)
        f.connected("acme", "three", linkedHere = true, others = 3)

        assertThat(f.scope.sharedNote(f.scope.find("acme", "one", f.projectId)!!, "They get the update too."))
            .isEqualTo("One other project shares this repository. They get the update too.")
        assertThat(f.scope.sharedNote(f.scope.find("acme", "three", f.projectId)!!, "They get the update too."))
            .isEqualTo("3 other projects share this repository. They get the update too.")
    }
}
