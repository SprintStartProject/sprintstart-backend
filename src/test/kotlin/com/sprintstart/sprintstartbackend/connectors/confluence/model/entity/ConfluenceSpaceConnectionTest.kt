package com.sprintstart.sprintstartbackend.connectors.confluence.model.entity

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class ConfluenceSpaceConnectionTest {
    @Test
    fun `empty allowlist allows pages unless denied`() {
        val connection = connection(denylist = mutableListOf("20"))

        assertThat(connection.allowsPage("10")).isTrue()
        assertThat(connection.allowsPage("20")).isFalse()
        assertThat(connection.allowsPage(" ")).isFalse()
    }

    @Test
    fun `non-empty allowlist includes only listed pages`() {
        val connection = connection(allowlist = mutableListOf("10", "20"))

        assertThat(connection.allowsPage(" 10 ")).isTrue()
        assertThat(connection.allowsPage("30")).isFalse()
    }

    @Test
    fun `denylist wins over allowlist`() {
        val connection = connection(
            allowlist = mutableListOf("10"),
            denylist = mutableListOf("10"),
        )

        assertThat(connection.allowsPage("10")).isFalse()
    }

    private fun connection(
        allowlist: MutableList<String> = mutableListOf(),
        denylist: MutableList<String> = mutableListOf(),
    ): ConfluenceSpaceConnection {
        return ConfluenceSpaceConnection(
            projectId = UUID.randomUUID(),
            baseUrl = "https://tenant.atlassian.net",
            spaceId = "123",
            spaceKey = "ENG",
            pageAllowlistInternal = allowlist,
            pageDenylistInternal = denylist,
        )
    }
}
