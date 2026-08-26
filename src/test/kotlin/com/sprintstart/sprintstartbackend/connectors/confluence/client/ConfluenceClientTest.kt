package com.sprintstart.sprintstartbackend.connectors.confluence.client

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

internal class ConfluenceClientTest : ConfluenceClientTestSupport() {
    @Test
    fun `validateConnection sends preemptive Basic auth and JSON accept header`() = runTest {
        enqueueJson(spacesResponse())

        client.validateConnection(baseUrl, credentials)

        val request = mockWebServer.takeRequest()
        val authorization = request.getHeader("Authorization")
        assertThat(authorization).startsWith("Basic ")
        assertThat(decodeBasicAuthorization(authorization)).isEqualTo("$TEST_EMAIL:$TEST_TOKEN")
        assertThat(request.getHeader("Accept")).isEqualTo("application/json")
        assertThat(request.path).isEqualTo("/wiki/api/v2/spaces?limit=1")
    }

    @Test
    fun `discoverSpaces maps typed fields and ignores unknown response fields`() = runTest {
        enqueueJson(
            spacesResponse(
                spaces = listOf(spaceJson(id = "42", key = "ARCH", alias = "architecture-alias")),
                extraFields = """, "irrelevant": { "future": true }""",
            ),
        )

        val spaces = client.discoverSpaces(baseUrl, credentials)

        assertThat(spaces).containsExactly(
            ConfluenceSpace(
                id = "42",
                key = "ARCH",
                name = "Architecture",
                type = "global",
                status = "current",
                currentActiveAlias = "architecture-alias",
                webUiPath = "/spaces/architecture-alias",
            ),
        )
    }

    @Test
    fun `discoverSpaces follows the next URL supplied by Confluence`() = runTest {
        enqueueJson(
            spacesResponse(
                spaces = listOf(spaceJson(id = "41", key = "ONE")),
                next = "/wiki/api/v2/spaces?limit=100&cursor=server-cursor",
            ),
        )
        enqueueJson(spacesResponse(listOf(spaceJson(id = "42", key = "TWO"))))

        val spaces = client.discoverSpaces(baseUrl, credentials)

        assertThat(spaces.map { it.id }).containsExactly("41", "42")
        assertThat(mockWebServer.takeRequest().path).isEqualTo("/wiki/api/v2/spaces?limit=100")
        assertThat(mockWebServer.takeRequest().path)
            .isEqualTo("/wiki/api/v2/spaces?limit=100&cursor=server-cursor")
    }

    @Test
    fun `getSpace retrieves a space by numeric ID`() = runTest {
        enqueueJson(spaceJson(id = "8421", key = "OPS", alias = "operations"))

        val space = client.getSpace(baseUrl, credentials, "8421")

        assertThat(space.id).isEqualTo("8421")
        assertThat(space.key).isEqualTo("OPS")
        assertThat(space.currentActiveAlias).isEqualTo("operations")
        assertThat(mockWebServer.takeRequest().path).isEqualTo("/wiki/api/v2/spaces/8421")
    }

    @Test
    fun `getPages requests storage format and handles one root page without next link`() = runTest {
        enqueueJson(pagesResponse(listOf(pageJson(id = "100", parentId = null))))
        enqueueJson(restrictionsResponse())

        val result = client.getPages(baseUrl, credentials, "42")

        assertThat(result.successfulPages).hasSize(1)
        assertThat(result.failures).isEmpty()
        assertThat(result.successfulPages.single().parentId).isNull()

        val pageRequest = mockWebServer.takeRequest()
        assertThat(pageRequest.requestUrl?.encodedPath).isEqualTo("/wiki/api/v2/spaces/42/pages")
        assertThat(pageRequest.requestUrl?.queryParameter("body-format")).isEqualTo("storage")
        assertThat(pageRequest.requestUrl?.queryParameter("limit")).isEqualTo("100")
        assertThat(mockWebServer.takeRequest().requestUrl?.encodedPath)
            .isEqualTo("/wiki/rest/api/content/100/restriction/byOperation/read")
    }

    @Test
    fun `getPages follows Confluence page next URLs and retains child hierarchy`() = runTest {
        enqueueJson(
            pagesResponse(
                pages = listOf(pageJson(id = "100", parentId = null)),
                next = "/wiki/api/v2/spaces/42/pages?body-format=storage&limit=100&cursor=opaque-cursor",
            ),
        )
        enqueueJson(pagesResponse(listOf(pageJson(id = "101", parentId = "100"))))
        enqueueJson(restrictionsResponse())
        enqueueJson(restrictionsResponse())

        val result = client.getPages(baseUrl, credentials, "42")

        assertThat(result.successfulPages.map { it.id }).containsExactly("100", "101")
        assertThat(result.successfulPages.last().parentId).isEqualTo("100")
        assertThat(result.successfulPages.last().parentType).isEqualTo("page")

        mockWebServer.takeRequest()
        val secondPageRequest = mockWebServer.takeRequest()
        assertThat(secondPageRequest.requestUrl?.queryParameter("cursor")).isEqualTo("opaque-cursor")
    }

    @Test
    fun `getPages preserves storage XHTML verbatim and maps version data`() = runTest {
        val storageXhtml =
            """<p local-id="test-local">Text &amp; more</p><ac:structured-macro ac:name="code">""" +
                "\n<ac:plain-text-body><![CDATA[println(\"VALUE\")]]></ac:plain-text-body>" +
                "</ac:structured-macro>"
        enqueueJson(
            pagesResponse(
                listOf(
                    pageJson(
                        id = "100",
                        storageXhtml = storageXhtml,
                        versionNumber = 7,
                        versionCreatedAt = "2026-08-20T10:15:30+02:00",
                        extraFields = """, "futurePageField": [1, 2, 3]""",
                    ),
                ),
            ),
        )
        enqueueJson(restrictionsResponse(extraFields = """, "futureRestrictionField": true"""))

        val page = client.getPages(baseUrl, credentials, "42").successfulPages.single()

        assertThat(page.storage.value).isEqualTo(storageXhtml)
        assertThat(page.storage.representation).isEqualTo("storage")
        assertThat(page.version.number).isEqualTo(7)
        assertThat(page.version.createdAt).isEqualTo(Instant.parse("2026-08-20T08:15:30Z"))
    }

    @Test
    fun `empty read restrictions map to empty identity collections`() = runTest {
        enqueueJson(pagesResponse(listOf(pageJson(id = "100"))))
        enqueueJson(restrictionsResponse())

        val restrictions = client
            .getPages(baseUrl, credentials, "42")
            .successfulPages
            .single()
            .restrictions

        assertThat(restrictions.users).isEmpty()
        assertThat(restrictions.groups).isEmpty()
    }

    @Test
    fun `empty or incomplete restriction structures fail closed`() = runTest {
        val malformedResponses = mapOf(
            "empty response" to "{}",
            "missing operation" to
                """
                {
                  "restrictions": {
                    "user": {"results":[],"start":0,"limit":100,"size":0},
                    "group": {"results":[],"start":0,"limit":100,"size":0}
                  }
                }
                """.trimIndent(),
            "missing restrictions" to """{"operation":"read"}""",
            "missing user" to
                """{"operation":"read","restrictions":{"group":{"results":[],"start":0,"limit":100,"size":0}}}""",
            "missing group" to
                """{"operation":"read","restrictions":{"user":{"results":[],"start":0,"limit":100,"size":0}}}""",
            "missing user results" to
                """
                {
                  "operation": "read",
                  "restrictions": {
                    "user": {"start":0,"limit":100,"size":0},
                    "group": {"results":[],"start":0,"limit":100,"size":0}
                  }
                }
                """.trimIndent(),
            "missing group results" to
                """
                {
                  "operation": "read",
                  "restrictions": {
                    "user": {"results":[],"start":0,"limit":100,"size":0},
                    "group": {"start":0,"limit":100,"size":0}
                  }
                }
                """.trimIndent(),
        )

        malformedResponses.forEach { (description, responseBody) ->
            enqueueJson(pagesResponse(listOf(pageJson(id = "100"))))
            enqueueJson(responseBody)

            val exception = assertThrows<ConfluenceInvalidResponseException>(description) {
                client.getPages(baseUrl, credentials, "42")
            }

            assertThat(exception.message)
                .`as`(description)
                .doesNotContain(responseBody, TEST_EMAIL, TEST_TOKEN)
            assertThat(exception.cause).isNull()
        }
    }

    @Test
    fun `restriction operation other than read fails closed`() = runTest {
        enqueueJson(pagesResponse(listOf(pageJson(id = "100"))))
        enqueueJson(restrictionsResponse(operation = "update"))

        val exception = assertThrows<ConfluenceInvalidResponseException> {
            client.getPages(baseUrl, credentials, "42")
        }

        assertThat(exception.requestContext).isEqualTo("retrieving read restrictions for page 100")
    }

    @Test
    fun `blank restriction identities fail closed`() = runTest {
        listOf(
            restrictionsResponse(accountIds = listOf("")),
            restrictionsResponse(groupIds = listOf("")),
        ).forEach { responseBody ->
            enqueueJson(pagesResponse(listOf(pageJson(id = "100"))))
            enqueueJson(responseBody)

            assertThrows<ConfluenceInvalidResponseException> {
                client.getPages(baseUrl, credentials, "42")
            }
        }
    }

    @Test
    fun `invalid restriction page limit produces a controlled failure`() = runTest {
        listOf(0, -1).forEach { invalidLimit ->
            enqueueJson(pagesResponse(listOf(pageJson(id = "100"))))
            enqueueJson(restrictionsResponse(limit = invalidLimit))

            val exception = assertThrows<ConfluenceInvalidResponseException> {
                client.getPages(baseUrl, credentials, "42")
            }

            assertThat(exception.message).isEqualTo(
                "Confluence returned an invalid response while retrieving read restrictions for page 100",
            )
        }
        assertThat(mockWebServer.requestCount).isEqualTo(4)
    }

    @Test
    fun `read restrictions retain only stable user account IDs and group IDs`() = runTest {
        enqueueJson(pagesResponse(listOf(pageJson(id = "100"))))
        enqueueJson(
            restrictionsResponse(
                accountIds = listOf("account-test-1", "account-test-2"),
                groupIds = listOf("group-test-1"),
            ),
        )

        val restrictions = client
            .getPages(baseUrl, credentials, "42")
            .successfulPages
            .single()
            .restrictions

        assertThat(restrictions.users.map { it.accountId })
            .containsExactly("account-test-1", "account-test-2")
        assertThat(restrictions.groups.map { it.id }).containsExactly("group-test-1")
    }

    @Test
    fun `read restrictions paginate users and groups with one shared start sequence`() = runTest {
        enqueueJson(pagesResponse(listOf(pageJson(id = "100"))))
        enqueueJson(
            restrictionsResponse(
                accountIds = listOf("account-test-1", "account-test-2"),
                groupIds = listOf("group-test-1"),
                start = 0,
                limit = 2,
            ),
        )
        enqueueJson(
            restrictionsResponse(
                accountIds = listOf("account-test-3"),
                groupIds = listOf("group-test-2"),
                start = 2,
                limit = 2,
            ),
        )

        val restrictions = client
            .getPages(baseUrl, credentials, "42")
            .successfulPages
            .single()
            .restrictions

        assertThat(restrictions.users.map { it.accountId })
            .containsExactly("account-test-1", "account-test-2", "account-test-3")
        assertThat(restrictions.groups.map { it.id })
            .containsExactly("group-test-1", "group-test-2")

        mockWebServer.takeRequest()
        val firstRestrictionsRequest = mockWebServer.takeRequest()
        val secondRestrictionsRequest = mockWebServer.takeRequest()
        assertThat(firstRestrictionsRequest.requestUrl?.queryParameter("start")).isEqualTo("0")
        assertThat(secondRestrictionsRequest.requestUrl?.queryParameter("start")).isEqualTo("2")
        assertThat(secondRestrictionsRequest.requestUrl?.queryParameter("limit")).isEqualTo("100")
    }

    @Test
    fun `restriction pagination continues when the group collection fills the shared page`() = runTest {
        enqueueJson(pagesResponse(listOf(pageJson(id = "100"))))
        enqueueJson(
            restrictionsResponse(
                accountIds = listOf("account-test-1"),
                groupIds = listOf("group-test-1", "group-test-2"),
                start = 0,
                limit = 2,
            ),
        )
        enqueueJson(
            restrictionsResponse(
                accountIds = listOf("account-test-2"),
                groupIds = listOf("group-test-3"),
                start = 2,
                limit = 2,
            ),
        )

        val restrictions = client
            .getPages(baseUrl, credentials, "42")
            .successfulPages
            .single()
            .restrictions

        assertThat(restrictions.users.map { it.accountId })
            .containsExactly("account-test-1", "account-test-2")
        assertThat(restrictions.groups.map { it.id })
            .containsExactly("group-test-1", "group-test-2", "group-test-3")

        mockWebServer.takeRequest()
        val restrictionRequests = listOf(mockWebServer.takeRequest(), mockWebServer.takeRequest())
        assertThat(restrictionRequests.map { it.requestUrl?.queryParameter("start") })
            .containsExactly("0", "2")
        assertThat(mockWebServer.requestCount).isEqualTo(3)
    }

    @Test
    fun `restriction pagination deduplicates identities and permits a final empty page`() = runTest {
        enqueueJson(pagesResponse(listOf(pageJson(id = "100"))))
        enqueueJson(
            restrictionsResponse(
                accountIds = listOf("account-test-1", "account-test-2"),
                groupIds = listOf("group-test-1", "group-test-2"),
                start = 0,
                limit = 2,
            ),
        )
        enqueueJson(
            restrictionsResponse(
                accountIds = listOf("account-test-2", "account-test-3"),
                groupIds = listOf("group-test-2"),
                start = 2,
                limit = 2,
            ),
        )
        enqueueJson(restrictionsResponse(start = 4, limit = 2))

        val restrictions = client
            .getPages(baseUrl, credentials, "42")
            .successfulPages
            .single()
            .restrictions

        assertThat(restrictions.users.map { it.accountId })
            .containsExactly("account-test-1", "account-test-2", "account-test-3")
        assertThat(restrictions.groups.map { it.id })
            .containsExactly("group-test-1", "group-test-2")

        mockWebServer.takeRequest()
        val starts = List(3) { mockWebServer.takeRequest().requestUrl?.queryParameter("start") }
        assertThat(starts).containsExactly("0", "2", "4")
    }

    @Test
    fun `restriction 404 records a typed failure and continues remaining pages`() = runTest {
        enqueueJson(
            pagesResponse(
                listOf(
                    pageJson(id = "100"),
                    pageJson(id = "101"),
                    pageJson(id = "102"),
                    pageJson(id = "103"),
                ),
            ),
        )
        enqueueJson(restrictionsResponse())
        val upstreamBody = "page disappeared with $TEST_EMAIL and $TEST_TOKEN"
        enqueueError(404, upstreamBody)
        enqueueJson(restrictionsResponse())
        enqueueJson(restrictionsResponse())

        val result = client.getPages(baseUrl, credentials, "42")

        assertThat(result.successfulPages.map { it.id }).containsExactly("100", "102", "103")
        assertThat(result.failures).containsExactly(
            ConfluencePageFailure(
                pageId = "101",
                stage = ConfluencePageFetchStage.RESTRICTIONS,
                httpStatus = 404,
                message = CONFLUENCE_RESTRICTIONS_NOT_FOUND_MESSAGE,
            ),
        )
        assertThat(result.failures.single().message)
            .doesNotContain(upstreamBody, TEST_EMAIL, TEST_TOKEN, basicAuthorizationValue())
        assertThat(mockWebServer.requestCount).isEqualTo(5)
    }

    @Test
    fun `space envelope without results fails parsing`() = runTest {
        val responseBody = """{"_links":{},"sensitive":"$TEST_TOKEN"}"""
        enqueueJson(responseBody)

        val exception = assertThrows<ConfluenceInvalidResponseException> {
            client.validateConnection(baseUrl, credentials)
        }

        assertThat(exception.requestContext).isEqualTo("validating the connection")
        assertThat(exception.message).doesNotContain(responseBody, TEST_TOKEN)
    }

    @Test
    fun `page envelope without results fails parsing`() = runTest {
        val responseBody = """{"_links":{},"sensitive":"$TEST_TOKEN"}"""
        enqueueJson(responseBody)

        val exception = assertThrows<ConfluenceInvalidResponseException> {
            client.getPages(baseUrl, credentials, "42")
        }

        assertThat(exception.requestContext).isEqualTo("retrieving pages for space 42")
        assertThat(exception.message).doesNotContain(responseBody, TEST_TOKEN)
        assertThat(mockWebServer.requestCount).isEqualTo(1)
    }

    @Test
    fun `401 maps to authentication exception without credential leakage`() = runTest {
        enqueueError(401, "invalid $TEST_EMAIL and $TEST_TOKEN")

        val exception = assertThrows<ConfluenceAuthenticationException> {
            client.validateConnection(baseUrl, credentials)
        }

        assertThat(exception.httpStatus).isEqualTo(401)
        assertThat(exception.message).doesNotContain(TEST_EMAIL, TEST_TOKEN, "invalid")
        assertThat(exception.cause).isNull()
        assertThat(credentials.toString()).doesNotContain(TEST_EMAIL, TEST_TOKEN)
    }

    @Test
    fun `403 maps to access denied exception`() = runTest {
        enqueueError(403, "sensitive upstream response")

        val exception = assertThrows<ConfluenceAccessDeniedException> {
            client.getSpace(baseUrl, credentials, "8421")
        }

        assertThat(exception.httpStatus).isEqualTo(403)
        assertThat(exception.requestContext).contains("space 8421")
        assertThat(exception.message).doesNotContain("sensitive upstream response")
    }

    @Test
    fun `404 maps to resource not found exception with useful request context`() = runTest {
        enqueueError(404, "sensitive upstream response")

        val exception = assertThrows<ConfluenceResourceNotFoundException> {
            client.getSpace(baseUrl, credentials, "8421")
        }

        assertThat(exception.httpStatus).isEqualTo(404)
        assertThat(exception.requestContext).isEqualTo("retrieving space 8421")
        assertThat(exception.message).doesNotContain("sensitive upstream response")
    }

    @Test
    fun `page-list 404 remains terminal`() = runTest {
        enqueueError(404, "space disappeared")

        val exception = assertThrows<ConfluenceResourceNotFoundException> {
            client.getPages(baseUrl, credentials, "42")
        }

        assertThat(exception.requestContext).isEqualTo("retrieving pages for space 42")
        assertThat(mockWebServer.requestCount).isEqualTo(1)
    }

    @Test
    fun `restriction 401 remains terminal for the complete operation`() = runTest {
        enqueueJson(pagesResponse(listOf(pageJson(id = "100"))))
        enqueueError(401, "invalid credentials")

        val exception = assertThrows<ConfluenceAuthenticationException> {
            client.getPages(baseUrl, credentials, "42")
        }

        assertThat(exception.requestContext).isEqualTo("retrieving read restrictions for page 100")
    }

    @Test
    fun `restriction 403 remains terminal for the complete operation`() = runTest {
        enqueueJson(pagesResponse(listOf(pageJson(id = "100"))))
        enqueueError(403, "permission details")

        val exception = assertThrows<ConfluenceAccessDeniedException> {
            client.getPages(baseUrl, credentials, "42")
        }

        assertThat(exception.requestContext).isEqualTo("retrieving read restrictions for page 100")
    }

    @Test
    fun `other HTTP errors use sanitized typed Confluence exception`() = runTest {
        val upstreamBody = "upstream included $TEST_EMAIL and $TEST_TOKEN"
        enqueueError(500, upstreamBody)
        val basicAuthorization = basicAuthorizationValue()

        val exception = assertThrows<ConfluenceExternalServiceException> {
            client.validateConnection(baseUrl, credentials)
        }

        assertThat(exception.httpStatus).isEqualTo(500)
        assertThat(exception.requestContext).isEqualTo("validating the connection")
        assertThat(exception.message)
            .doesNotContain(TEST_EMAIL, TEST_TOKEN, basicAuthorization, upstreamBody, "upstream included")
        assertThat(exception.cause).isNull()
    }
}
