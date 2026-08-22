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
 * [maxGroups] is what keeps the FAQ readable as it grows: without it the list would grow without
 * limit, which is the "drowning in questions" problem the grouping exists to solve. Crossing it
 * triggers a merge pass rather than rejecting the new entry, so the limit never loses a question.
 *
 * ```yaml
 * sprintstart:
 *     insights:
 *         faq:
 *             live-updates: ...
 *             max-groups: ...
 *             candidate-groups: ...
 *             sample-questions: ...
 *             trend-window-days: ...
 * ```
 *
 * @property liveUpdates whether a question asked in chat updates the FAQ right away
 * @property maxGroups ceiling on recurring-question entries per project
 * @property candidateGroups how many existing entries a single classification may consider.
 * Keep this at least as large as [maxGroups]: below it, a classification stops seeing part of the
 * FAQ, fails to find matches that exist, and opens duplicates for them.
 * @property sampleQuestions how many phrasings an entry's detail view shows
 * @property trendWindowDays length of the window an entry's trend is measured over
 * @property rebuildQuestionLimit how many questions a manual rebuild may send to the AI service.
 * A hard cap, not a warning: the rebuild is the one path that puts raw questions into a prompt, so
 * it is the one path whose size grows without bound as a project keeps chatting.
 */
data class FaqInsightsConfig(
    @get:JsonProperty("live-updates")
    val liveUpdates: Boolean = true,
    @get:JsonProperty("max-groups")
    val maxGroups: Int = 40,
    @get:JsonProperty("candidate-groups")
    val candidateGroups: Int = 40,
    @get:JsonProperty("sample-questions")
    val sampleQuestions: Int = 10,
    @get:JsonProperty("trend-window-days")
    val trendWindowDays: Long = 14,
    @get:JsonProperty("rebuild-question-limit")
    val rebuildQuestionLimit: Int = 2000,
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
