package com.sprintstart.sprintstartbackend.connectors.github

import com.sprintstart.sprintstartbackend.connectors.github.models.client.graphql.GithubIssuesResponse
import com.sprintstart.sprintstartbackend.connectors.github.util.GithubQueryLoader
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Guards the two issue queries against drifting apart.
 *
 * Both feed the same `Issue` DTO, whose fields are non-nullable and have no defaults. The parser
 * is configured with `ignoreUnknownKeys`, so selecting a field nobody reads is harmless -- but a
 * field the DTO requires and the query omits throws `MissingFieldException` for the whole page.
 *
 * That failure is close to invisible in production: it happens inside a fire-and-forget coroutine,
 * the fetch-failed listener still marks the phase finished, and the ingestion run ends up looking
 * successful with nothing ingested. `issues-since.graphql` was missing `updatedAt` this way, which
 * silently broke *every* incremental issue sync -- edited issues, closed issues and brand-new ones
 * alike -- while the initial import kept working because it uses the other query.
 */
class GithubIssueQueryContractTest {
    private val queryLoader = GithubQueryLoader()

    private val fullQuery = queryLoader.load("github/graphql/100-issues.graphql")
    private val incrementalQuery = queryLoader.load("github/graphql/issues-since.graphql")

    @Test
    fun `the incremental issue query selects every field the full query does`() {
        val missing = selectedFields(fullQuery) - selectedFields(incrementalQuery)

        assertThat(missing)
            .`as`("fields selected by 100-issues.graphql but missing from issues-since.graphql")
            .isEmpty()
    }

    @Test
    fun `an issue page shaped like the incremental query deserializes`() {
        val json = Json { ignoreUnknownKeys = true }

        val page = json.decodeFromString<GithubIssuesResponse>(issuePageJson())

        val issue = page.results.single()
        assertThat(issue.number).isEqualTo(280)
        assertThat(issue.body).contains("- [x]")
    }

    /**
     * The bare field names a query selects, ignoring arguments, braces and the query header.
     */
    private fun selectedFields(query: String): Set<String> =
        query
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line -> Regex("^([a-zA-Z][a-zA-Z0-9_]*)\\s*(\\(|\\{|$)").find(line)?.groupValues?.get(1) }
            .filterNot { it == "query" }
            .toSet()

    /**
     * A response page carrying exactly the fields `issues-since.graphql` asks for, so a field the
     * DTO requires but the query drops makes this fail.
     */
    private fun issuePageJson(): String {
        val selected = selectedFields(incrementalQuery)
        val scalars = buildString {
            if ("number" in selected) append("""  "number": 280,""" + "\n")
            if ("title" in selected) append("""  "title": "Delete connected repos",""" + "\n")
            if ("body" in selected) append("""  "body": "### Acceptance Criteria\n- [x] done",""" + "\n")
            if ("state" in selected) append("""  "state": "CLOSED",""" + "\n")
            if ("createdAt" in selected) append("""  "createdAt": "2026-08-12T10:00:00Z",""" + "\n")
            if ("updatedAt" in selected) append("""  "updatedAt": "2026-08-21T14:00:00Z",""" + "\n")
            if ("closedAt" in selected) append("""  "closedAt": null,""" + "\n")
            if ("url" in selected) append("""  "url": "https://github.com/acme/repo/issues/280",""" + "\n")
        }

        return """
            {
              "data": {
                "repository": {
                  "issues": {
                    "pageInfo": { "hasNextPage": false, "endCursor": null },
                    "nodes": [
                      {
                        $scalars
                        "author": { "login": "octocat" },
                        "labels": { "nodes": [ { "name": "story" } ] },
                        "assignees": { "nodes": [ { "login": "octocat" } ] },
                        "comments": { "nodes": [] }
                      }
                    ]
                  }
                }
              }
            }
            """.trimIndent()
    }
}
