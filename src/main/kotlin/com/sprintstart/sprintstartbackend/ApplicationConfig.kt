package com.sprintstart.sprintstartbackend

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Contains the following application.yml config parameters
 *
 * ```yaml
 * sprintstart:
 *     ai: ...
 *     github: ...
 *     keycloak: ...
 * ```
 */
@ConfigurationProperties(prefix = "sprintstart")
data class ApplicationConfig(
    val ai: AiConfig,
    val github: GithubConfig,
    val keycloak: KeycloakConfig = KeycloakConfig(),
    val crypto: CryptoConfig,
)

/**
 * Contains the following application.yml config parameters
 *
 * ```yaml
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
 * ´´´yaml
 * sprintstart:
 *     github:
 *         base-url: ...
 *         repo-base-url: ...
 *         cron: ...
 * ´´´
 */
data class GithubConfig(
    @get:JsonProperty("base-url")
    val baseUrl: String,
    @get:JsonProperty("repo-base-url")
    val repoBaseUrl: String,
    @get:JsonProperty("cron")
    val cron: String,
)

data class KeycloakConfig(
    val admin: KeycloakAdminConfig = KeycloakAdminConfig(),
)

data class KeycloakAdminConfig(
    @get:JsonProperty("base-url")
    val baseUrl: String? = null,
    val realm: String = "sprintstart",
    @get:JsonProperty("token-realm")
    val tokenRealm: String = "master",
    @get:JsonProperty("client-id")
    val clientId: String = "admin-cli",
    @get:JsonProperty("client-secret")
    val clientSecret: String? = null,
    val username: String? = null,
    val password: String? = null,
)

/**
 * Configuration class representing cryptographic parameters.
 *
 * ```yaml
 * sprintstart:
 *     crypto:
 *         master-key: ...
 *         salt: ...
 * ```
 */
data class CryptoConfig(
    @get:JsonProperty("master-key")
    val masterKey: String,
    @get:JsonProperty("salt")
    val salt: String,
)
