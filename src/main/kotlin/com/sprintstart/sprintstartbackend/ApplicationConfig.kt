package com.sprintstart.sprintstartbackend

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Contains the following application.yml config parameters
 *
 * ```yml
 * sprintstart:
 *     ai: ...
 *     github: ...
 * ```
 */
@ConfigurationProperties(prefix = "sprintstart")
data class ApplicationConfig(
    val ai: AiConfig,
    val github: GithubConfig,
)

/**
 * Contains the following application.yml config parameters
 *
 * ```yml
 * sprintstart:
 *     ai:
 *         base-url: ...
 * ````
 */
data class AiConfig(
    @get:JsonProperty("base-url")
    val baseUrl: String,
)

/**
 * Contains the following application.yml config parameters
 *
 * ´´´yml
 * sprintstart:
 *     github:
 *         base-url: ...
 *         token: ...
 *         cron: ...
 * ´´´
 */
data class GithubConfig(
    @get:JsonProperty("base-url")
    val baseUrl: String,
    @get:JsonProperty("repo-base-url")
    val repoBaseUrl: String,
    @get:JsonProperty("token")
    val token: String,
    @get:JsonProperty("cron")
    val cron: String,
)
