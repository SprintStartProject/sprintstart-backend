package com.sprintstart.sprintstartbackend

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Contains YAML config parameters under the `sprintstart: ...` section.
 */
@ConfigurationProperties(prefix = "sprintstart")
internal data class ApplicationConfig(
    val ai: AiConfig,
)

internal data class AiConfig(
    @get:JsonProperty("base-url")
    val baseUrl: String,
)
