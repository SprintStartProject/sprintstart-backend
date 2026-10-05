package com.sprintstart.sprintstartbackend.shared.git

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GitRepositoryCoordinatesTest {
    @Test
    fun `toString masks the credential`() {
        val coordinates = GitRepositoryCoordinates(
            host = "bitbucket.org",
            namespace = "sprintstart",
            name = "sprintstart-backend",
            username = "x-bitbucket-api-token-auth",
            secret = "super-secret-api-token",
        )

        assertThat(coordinates.toString())
            .contains("bitbucket.org", "sprintstart", "sprintstart-backend", "secret=***")
            .doesNotContain("super-secret-api-token")
    }

    @Test
    fun `splits a flat namespace into one segment`() {
        val coordinates = coordinates(namespace = "sprintstart")

        assertThat(coordinates.namespacePath).containsExactly("sprintstart")
    }

    @Test
    fun `splits a nested namespace so providers with subgroups are addressable`() {
        val coordinates = coordinates(namespace = "group/subgroup")

        assertThat(coordinates.namespacePath).containsExactly("group", "subgroup")
    }

    @Test
    fun `ignores empty namespace segments`() {
        val coordinates = coordinates(namespace = "group//subgroup/")

        assertThat(coordinates.namespacePath).containsExactly("group", "subgroup")
    }

    private fun coordinates(namespace: String) = GitRepositoryCoordinates(
        host = "bitbucket.org",
        namespace = namespace,
        name = "sprintstart-backend",
        username = "x-bitbucket-api-token-auth",
        secret = "secret",
    )
}
