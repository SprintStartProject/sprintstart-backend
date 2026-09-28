package com.sprintstart.sprintstartbackend.connectors.jira.external

import java.time.Instant

/**
 * Module-facing view of a connected Jira instance, exposing only the fields other modules need to
 * build source-instance status rows without reaching into the Jira persistence model.
 *
 * Mirrors [com.sprintstart.sprintstartbackend.connectors.github.external.GithubSourceInstanceDto]
 * for the Jira connector. Jira instances are identified by their URL (there is no numeric id), and
 * Jira ingests only issues, so there is a single sync timestamp ([lastUpdate]) instead of the
 * GitHub connector's per-resource-type snapshots.
 *
 * @property instanceUrl Stable identifier of the instance, its base URL (for example
 * `https://acme.atlassian.net`).
 * @property displayName Human-readable name of the connected instance.
 * @property status Stable source-status vocabulary value, for example `CONNECTED` or `DISABLED`,
 * shared with the GitHub connector so the frontend can treat every source system uniformly.
 * @property enabled Whether the source is currently enabled for ingestion.
 * @property lastUpdate Timestamp of the last successful update, or `null` when never synced.
 * @property jiraProjectKeys Keys of the Jira projects ingested from this instance.
 */
data class JiraSourceInstanceDto(
    val instanceUrl: String,
    val displayName: String,
    val status: String,
    val enabled: Boolean,
    val lastUpdate: Instant?,
    val jiraProjectKeys: Set<String>,
)
