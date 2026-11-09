package com.sprintstart.sprintstartbackend.shared.ai

import com.sprintstart.sprintstartbackend.shared.config.ApplicationConfig
import com.sprintstart.sprintstartbackend.shared.web.WebClient
import com.sprintstart.sprintstartbackend.shared.web.WebClientException
import kotlinx.coroutines.flow.Flow
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI

/**
 * The mechanics every feature's AI client shares: the AI service's base URL, the error wrapping of a
 * blocking call, and the passthrough of a streamed one.
 *
 * Each feature keeps its own client with its own endpoints and request types; this only removes the
 * boilerplate they would otherwise repeat.
 */
@Component
class AiServiceCalls(
    @PublishedApi internal val webClient: WebClient,
    private val applicationConfig: ApplicationConfig,
) {
    @PublishedApi
    internal val logger: Logger = LoggerFactory.getLogger(javaClass)

    /**
     * POSTs [body] to [path] and decodes the response.
     *
     * @param action What the call does, for the error message ("assemble diagram").
     * @throws AiServiceException for a non-2xx response, carrying the upstream status and body.
     */
    final suspend inline fun <reified B, reified R> post(path: String, body: B, action: String): R =
        try {
            webClient
                .post()
                .uri(uri(path))
                .body(body)
                .sync()
                .perform<R>()
        } catch (@Suppress("SwallowedException") e: WebClientException) {
            throw AiServiceException(e.statusCode, e.body, "Failed to $action (HTTP ${e.statusCode}): ${e.body}")
        }

    /**
     * Opens an SSE stream against [path], POSTing [body].
     *
     * A malformed chunk is logged and skipped rather than killing the stream; events the AI service
     * itself sends, including its own `error` event, pass straight through.
     *
     * @param chunkName What the chunks are, for the log line ("AI progress").
     */
    final inline fun <reified B, reified E> stream(path: String, body: B, chunkName: String): Flow<E> =
        webClient
            .post()
            .uri(uri(path))
            .body(body)
            .stream()
            .perform<E>(
                onChunkError = { raw, err ->
                    logger.warn("Skipping malformed {} chunk '{}': {}", chunkName, raw, err.message)
                    true
                },
            )

    /** Builds an absolute URI for [path] against the configured AI service base URL. */
    @PublishedApi
    internal fun uri(path: String): URI = URI.create("${applicationConfig.ai.baseUrl}$path")
}
