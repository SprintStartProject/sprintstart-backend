package com.sprintstart.sprintstartbackend.connectors.notion.client

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import kotlinx.coroutines.delay
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Instant
import kotlin.time.toKotlinDuration

@Configuration
internal class NotionClientConfiguration {
    @Bean
    fun notionRetrySleeper(): NotionRetrySleeper {
        return NotionRetrySleeper { duration -> delay(duration.toKotlinDuration()) }
    }

    @Bean
    fun notionRetryClock(): NotionRetryClock {
        return NotionRetryClock { Instant.now() }
    }

    @Bean
    fun notionRequestThrottle(
        applicationConfig: ApplicationConfig,
        sleeper: NotionRetrySleeper,
        clock: NotionRetryClock,
    ): NotionRequestThrottle {
        return NotionRequestThrottle(applicationConfig.notion.throttle, sleeper, clock)
    }

    @Bean
    fun notionRetryExecutor(
        applicationConfig: ApplicationConfig,
        sleeper: NotionRetrySleeper,
        clock: NotionRetryClock,
        throttle: NotionRequestThrottle,
    ): NotionRetryExecutor {
        return NotionRetryExecutor(applicationConfig.notion.retry, sleeper, clock, throttle)
    }

    @Bean
    fun notionClient(
        webClient: WebClient,
        retryExecutor: NotionRetryExecutor,
        applicationConfig: ApplicationConfig,
    ): NotionClient {
        return NotionClient(webClient, retryExecutor, apiVersion = applicationConfig.notion.apiVersion)
    }
}
