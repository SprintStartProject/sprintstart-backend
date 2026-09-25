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
}
