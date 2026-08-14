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
    val upload: UploadConfig,
    val insights: InsightsConfig = InsightsConfig(),
)

/**
 * Contains the following application.yml config parameters
 *
 * ```yaml
 * sprintstart:
 *     insights:
 *         faq: ...
 *         knowledge-gaps: ...
 * ```
 */
data class InsightsConfig(
    val faq: FaqInsightsConfig = FaqInsightsConfig(),
    @get:JsonProperty("knowledge-gaps")
    val knowledgeGaps: KnowledgeGapsInsightsConfig = KnowledgeGapsInsightsConfig(),
)

/**
 * Bounds on the FAQ insight, which is maintained incrementally as questions are asked.
 *
 * The two ceilings are what keep the FAQ readable as it grows: without them the category list and
 * the group list under each category would both grow without limit, which is the "drowning in a
 * flat list" problem the grouping exists to solve. Crossing a ceiling triggers a consolidation
 * pass rather than rejecting the new entry, so a limit never loses a question.
 *
 * ```yaml
 * sprintstart:
 *     insights:
 *         faq:
 *             live-updates: ...
 *             max-categories: ...
 *             max-groups-per-category: ...
 *             candidate-groups: ...
 *             sample-questions: ...
 *             trend-window-days: ...
 * ```
 *
 * @property liveUpdates whether a question asked in chat updates the FAQ right away
 * @property maxCategories ceiling on distinct categories per project
 * @property maxGroupsPerCategory ceiling on groups within one category
 * @property candidateGroups how many existing groups a single classification may consider
 * @property sampleQuestions how many sample questions a group's detail view shows
 * @property trendWindowDays length of the window a group's trend is measured over
 */
data class FaqInsightsConfig(
    @get:JsonProperty("live-updates")
    val liveUpdates: Boolean = true,
    @get:JsonProperty("max-categories")
    val maxCategories: Int = 12,
    @get:JsonProperty("max-groups-per-category")
    val maxGroupsPerCategory: Int = 20,
    @get:JsonProperty("candidate-groups")
    val candidateGroups: Int = 40,
    @get:JsonProperty("sample-questions")
    val sampleQuestions: Int = 10,
    @get:JsonProperty("trend-window-days")
    val trendWindowDays: Long = 14,
)

/**
 * Settings for refreshing the knowledge-gap insight after ingestion.
 *
 * ```yaml
 * sprintstart:
 *     insights:
 *         knowledge-gaps:
 *             auto-refresh: ...
 *             debounce-seconds: ...
 * ```
 *
 * @property autoRefresh whether newly indexed artifacts trigger a rescan on their own
 * @property debounceSeconds how long to wait for further runs before rescanning, so a burst of
 * ingestion runs costs one scan instead of one per run
 */
data class KnowledgeGapsInsightsConfig(
    @get:JsonProperty("auto-refresh")
    val autoRefresh: Boolean = true,
    @get:JsonProperty("debounce-seconds")
    val debounceSeconds: Long = 60,
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
 *         cron: ...
 * ´´´
 */
data class GithubConfig(
    @get:JsonProperty("base-url")
    val baseUrl: String,
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

data class UploadConfig(
    val directory: String,
    @get:JsonProperty("max-file-size-bytes")
    val maxFileSizeBytes: Long,
)
