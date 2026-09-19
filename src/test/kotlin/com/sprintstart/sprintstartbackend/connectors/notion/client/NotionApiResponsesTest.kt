package com.sprintstart.sprintstartbackend.connectors.notion.client

import com.sprintstart.sprintstartbackend.connectors.notion.NotionBlockParser
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NotionApiResponsesTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val parser = NotionBlockParser()

    @Test
    fun `decodes table rows fixture and preserves empty cells`() {
        val fixture = checkNotNull(javaClass.getResource("/notion/table-rows.json")) {
            "Missing test fixture: /notion/table-rows.json"
        }.readText()

        val response = json.decodeFromString<NotionBlocksResponse>(fixture)

        assertThat(response.results).hasSize(3)
        assertThat(response.hasMore).isFalse()
        assertThat(response.nextCursor).isNull()

        val firstBlock = response.results.first()
        assertThat(firstBlock.id).isEqualTo("aaaaaaaa-aaaa-4aaa-8aaa-000000000001")
        assertThat(firstBlock.type).isEqualTo("table_row")
        assertThat(firstBlock.hasChildren).isFalse()
        val firstRow = checkNotNull(firstBlock.tableRow) {
            "A table_row block must contain its row payload"
        }
        assertThat(parser.extractCellTexts(firstRow)).containsExactly("Service", "Purpose", "")
    }

    @Test
    fun `decodes pagination fields when another batch is available`() {
        val body = """
            {
              "object": "list",
              "results": [],
              "has_more": true,
              "next_cursor": "next-batch-cursor"
            }
        """.trimIndent()

        val response = json.decodeFromString<NotionBlocksResponse>(body)

        assertThat(response.results).isEmpty()
        assertThat(response.hasMore).isTrue()
        assertThat(response.nextCursor).isEqualTo("next-batch-cursor")
    }

    @Test
    fun `decodes a block without a table row payload`() {
        val body = """
            {
              "object": "block",
              "id": "bbbbbbbb-bbbb-4bbb-8bbb-000000000001",
              "type": "table",
              "has_children": true,
              "table": {
                "table_width": 3,
                "has_column_header": true,
                "has_row_header": false
              }
            }
        """.trimIndent()

        val block = json.decodeFromString<NotionBlockResponse>(body)

        assertThat(block.type).isEqualTo("table")
        assertThat(block.hasChildren).isTrue()
        assertThat(block.tableRow).isNull()
    }
}
