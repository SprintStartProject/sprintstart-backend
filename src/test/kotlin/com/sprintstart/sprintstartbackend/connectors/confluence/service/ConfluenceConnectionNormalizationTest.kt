package com.sprintstart.sprintstartbackend.connectors.confluence.service

import com.sprintstart.sprintstartbackend.connectors.confluence.model.exception.ConfluenceConnectionConfigurationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ConfluenceConnectionNormalizationTest {
    @Test
    fun `normalizes equivalent tenant URL variants`() {
        val expected = "https://tenant.atlassian.net"

        assertThat(normalizeConfluenceBaseUrl(" HTTPS://TENANT.ATLASSIAN.NET/wiki/ ")).isEqualTo(expected)
        assertThat(normalizeConfluenceBaseUrl("https://tenant.atlassian.net/")).isEqualTo(expected)
        assertThat(normalizeConfluenceBaseUrl("https://tenant.atlassian.net:443/wiki")).isEqualTo(expected)
    }

    @Test
    fun `rejects unsafe or non-tenant URLs`() {
        val invalidUrls = listOf(
            "http://tenant.atlassian.net",
            "https://user:password@tenant.atlassian.net",
            "https://tenant.atlassian.net/wiki/pages",
            "https://tenant.atlassian.net?token=secret",
            "https://tenant.atlassian.net#fragment",
        )

        invalidUrls.forEach { invalidUrl ->
            assertThatThrownBy { normalizeConfluenceBaseUrl(invalidUrl) }
                .isInstanceOf(ConfluenceConnectionConfigurationException::class.java)
        }
    }

    @Test
    fun `trims and de-duplicates page IDs while preserving order`() {
        assertThat(normalizeConfluencePageIds(listOf(" 20 ", "10", "20"), "page allowlist"))
            .containsExactly("20", "10")
    }

    @Test
    fun `rejects blank page IDs`() {
        assertThatThrownBy { normalizeConfluencePageIds(listOf("10", " "), "page denylist") }
            .isInstanceOf(ConfluenceConnectionConfigurationException::class.java)
            .hasMessage("Confluence page denylist must not contain blank page IDs")
    }
}
