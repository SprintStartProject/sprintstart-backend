package com.sprintstart.sprintstartbackend.shared.web

import kotlinx.serialization.json.Json
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.http.HttpClient
import java.time.Duration

const val CONNECT_TIMEOUT = 10L

/**
 * Provides shared, application-scoped infrastructure beans for HTTP communication.
 *
 * A single [HttpClient] instance is intentionally shared across all requests so that the JVM's
 * internal connection pool is reused, avoiding the per-request TCP handshake overhead present
 * in the original implementation.
 *
 * A single [Json] instance is similarly shared; constructing it is not free, and the instance
 * is thread-safe once built.
 */
@Configuration
class HttpClientFactory {
    @Bean
    fun httpClient(): HttpClient =
        HttpClient
            .newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT))
            .build()

    @Bean
    fun jsonParser(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }
}
