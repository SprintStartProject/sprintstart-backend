package com.sprintstart.sprintstartbackend.connectors.notion.client

import com.sprintstart.sprintstartbackend.connectors.notion.NotionBlockParser
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class NotionBlockTreeTest : NotionClientTestSupport() {
    @Test
    fun `loads every level and pagination cursor in order without following document boundaries`() = runTest {
        enqueueJson(batch(listOf(treeBlock("toggle", "toggle", true)), nextCursor = "root-next"))
        enqueueJson(batch(listOf(treeBlock("child-page", "child_page", true), treeBlock("database", "child_database", true))))
        enqueueJson(batch(listOf(treeBlock("bullet", "bulleted_list_item", true)), nextCursor = "nested-next"))
        enqueueJson(batch(listOf(block("last"))))
        enqueueJson(batch(listOf(block("leaf"))))

        val tree = client.getBlockTree(NOTION_TEST_TOKEN, "page")

        assertThat(tree.map { it.block.id }).containsExactly("toggle", "child-page", "database")
        assertThat(tree[0].children.map { it.block.id }).containsExactly("bullet", "last")
        assertThat(tree[0].children[0].children.single().block.id).isEqualTo("leaf")
        assertThat(tree[1].children).isEmpty()
        assertThat(tree[2].children).isEmpty()
        assertThat(List(5) { takeRequest().path }).containsExactly(
            "/v1/blocks/page/children?page_size=100",
            "/v1/blocks/page/children?page_size=100&start_cursor=root-next",
            "/v1/blocks/toggle/children?page_size=100",
            "/v1/blocks/toggle/children?page_size=100&start_cursor=nested-next",
            "/v1/blocks/bullet/children?page_size=100",
        )
        assertThat(server.requestCount).isEqualTo(5)
    }

    @Test
    fun `failed descendant fails the whole tree instead of returning partial content`() = runTest {
        enqueueJson(batch(listOf(block("first"), treeBlock("nested", "toggle", true))))
        enqueueError(403)

        assertThrows<NotionAccessDeniedException> { client.getBlockTree(NOTION_TEST_TOKEN, "page") }
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `rejects cycles before requesting the same ancestor again`() = runTest {
        enqueueJson(batch(listOf(treeBlock("nested", "toggle", true))))
        enqueueJson(batch(listOf(treeBlock("page", "toggle", true))))

        assertThrows<NotionInvalidResponseException> { client.getBlockTree(NOTION_TEST_TOKEN, "page") }
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `rejects excessive tree depth rather than overflowing the stack`() = runTest {
        repeat(128) { index -> enqueueJson(batch(listOf(treeBlock("level-${index + 1}", "toggle", true)))) }

        assertThrows<NotionInvalidResponseException> { client.getBlockTree(NOTION_TEST_TOKEN, "level-0") }
        assertThat(server.requestCount).isEqualTo(128)
    }

    @Test
    fun `empty page returns an empty tree`() = runTest {
        enqueueJson(batch())

        assertThat(client.getBlockTree(NOTION_TEST_TOKEN, "page")).isEmpty()
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `http fixtures load and parse into one complete page with table metadata`() = runTest {
        val heading = NotionJsonFixtures.block("heading_1")
        val table = NotionJsonFixtures.block("table")
        enqueueJson(batch(listOf(heading, table, NotionJsonFixtures.block("code"))))
        enqueueJson(NotionJsonFixtures.read("table-rows.json"))

        val parsed = NotionBlockParser().parse(client.getBlockTree(NOTION_TEST_TOKEN, "page"))

        assertThat(parsed.sections.single().heading).isEqualTo("Heading 1")
        assertThat(parsed.tables).hasSize(1)
        assertThat(parsed.tables.single()).contains("| Service | Purpose |  |", "| PostgreSQL | Stores application data |  |")
        assertThat(parsed.codeBlocks.single().language).isEqualTo("kotlin")
        assertThat(parsed.bodyText).contains("# Heading 1", parsed.tables.single(), "```kotlin", "Example program")
        assertThat(server.requestCount).isEqualTo(2)
    }

    private fun treeBlock(id: String, type: String, hasChildren: Boolean): JsonObject {
        return JsonObject(block(id) + buildJsonObject {
            put("type", JsonPrimitive(type))
            put("has_children", JsonPrimitive(hasChildren))
        })
    }
}
