package com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions

/**
 * Thrown when a Jira instance connects but its project search returns no projects.
 *
 * Jira Cloud answers `/rest/api/3/project/search` with `200` and an empty list when the request is
 * unauthenticated or the account cannot browse any project, so an empty result is the signature of
 * invalid credentials or missing permissions rather than a transient error. There is nothing to
 * ingest in that case, so the connect must fail loudly instead of silently succeeding with an empty
 * project-key set (which left the instance connected but permanently ingesting nothing).
 */
internal data class JiraNoAccessibleProjectsException(
    val url: String,
) : RuntimeException(
        "No Jira projects are accessible with these credentials at '$url'. " +
            "Verify the API token belongs to an account that can browse the project(s) and is current.",
    )
