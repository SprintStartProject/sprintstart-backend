package com.sprintstart.sprintstartbackend.connectors.notion.client

import com.sprintstart.sprintstartbackend.AiConfig
import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.CryptoConfig
import com.sprintstart.sprintstartbackend.GithubConfig
import com.sprintstart.sprintstartbackend.NotionConfig
import com.sprintstart.sprintstartbackend.UploadConfig
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import java.net.http.HttpClient
import java.time.Duration
import java.util.function.Supplier

internal class NotionClientConfigurationTest {
    @Test
    fun `registers one shared retry executor and throttle without needing a URI bean`() {
        AnnotationConfigApplicationContext().use { context ->
            context.registerBean(
                "applicationConfig",
                ApplicationConfig::class.java,
                Supplier {
                    ApplicationConfig(
                        ai = AiConfig("http://localhost"),
                        github = GithubConfig("http://localhost"),
                        crypto = CryptoConfig("unused-test-key", "unused-test-salt"),
                        upload = UploadConfig("unused", 1024),
                    )
                },
            )
            context.registerBean(
                "webClient",
                WebClient::class.java,
                Supplier { WebClient(HttpClient.newHttpClient(), NotionJsonFixtures.json) },
            )
            context.register(NotionClientConfiguration::class.java)

            context.refresh()

            assertThat(context.getBeansOfType(NotionClient::class.java)).hasSize(1)
            assertThat(context.getBeansOfType(NotionRetryExecutor::class.java)).hasSize(1)
            assertThat(context.getBeansOfType(NotionRequestThrottle::class.java)).hasSize(1)
        }
    }

    @Test
    fun `binds Notion version retry and throttle properties with defaults`() {
        val properties = MapConfigurationPropertySource(
            mapOf(
                "sprintstart.notion.api-version" to "2026-03-11",
                "sprintstart.notion.retry.max-attempts" to "2",
                "sprintstart.notion.retry.initial-delay" to "750ms",
                "sprintstart.notion.throttle.min-interval" to "500ms",
            ),
        )

        val config = Binder(properties).bind("sprintstart.notion", Bindable.of(NotionConfig::class.java)).get()

        assertThat(config.apiVersion).isEqualTo("2026-03-11")
        assertThat(config.retry.maxAttempts).isEqualTo(2)
        assertThat(config.retry.initialDelay).isEqualTo(Duration.ofMillis(750))
        assertThat(config.retry.maxDelay).isEqualTo(Duration.ofSeconds(30))
        assertThat(config.throttle.minInterval).isEqualTo(Duration.ofMillis(500))
        assertThat(config.throttle.maxWait).isEqualTo(Duration.ofSeconds(30))
    }
}
