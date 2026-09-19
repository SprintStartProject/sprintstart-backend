package com.sprintstart.sprintstartbackend.connectors.notion.client

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.decodeFromJsonElement
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class NotionBlockPayloadsTest {
    private val json = NotionJsonFixtures.json

    @ParameterizedTest
    @CsvSource(
        "paragraph,Read the docs",
        "heading_1,Heading 1",
        "heading_2,Heading 2",
        "heading_3,Heading 3",
        "bulleted_list_item,Start services",
        "numbered_list_item,Run tests",
        "quote,Keep it simple",
        "callout,Use a test workspace",
        "toggle,More details",
    )
    fun `decodes text payload for each supported block type`(type: String, expected: String) {
        val block = json.decodeFromJsonElement<NotionBlockResponse>(NotionJsonFixtures.block(type))

        val fragments = when (type) {
            "paragraph" -> block.paragraph?.richText
            "heading_1" -> block.heading1?.richText
            "heading_2" -> block.heading2?.richText
            "heading_3" -> block.heading3?.richText
            "bulleted_list_item" -> block.bulletedListItem?.richText
            "numbered_list_item" -> block.numberedListItem?.richText
            "quote" -> block.quote?.richText
            "callout" -> block.callout?.richText
            "toggle" -> block.toggle?.richText
            else -> error("Unexpected test case: $type")
        }
        assertThat(block.type).isEqualTo(type)
        assertThat(fragments).describedAs("Payload for %s", type).isNotNull()
        assertThat(checkNotNull(fragments).joinToString("") { it.plainText }).isEqualTo(expected)
        assertThat(block.tableRow).isNull()
    }

    @Test
    fun `preserves heading toggleability and child discovery flag`() {
        val block = json.decodeFromJsonElement<NotionBlockResponse>(NotionJsonFixtures.block("heading_2"))

        assertThat(block.hasChildren).isTrue()
        assertThat(block.heading2?.isToggleable).isTrue()
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `preserves both to do checked states`(checked: Boolean) {
        val block = json.decodeFromString<NotionBlockResponse>(
            """
            {"id":"todo-id", "type":"to_do", "has_children":false,
             "to_do":{"rich_text":[{"plain_text":"Verify database"}], "checked":$checked}}
            """.trimIndent(),
        )

        val payload = checkNotNull(block.toDo)
        assertThat(payload.checked).isEqualTo(checked)
        assertThat(payload.richText.map { it.plainText }).containsExactly("Verify database")
    }

    @Test
    fun `preserves code fragments whitespace language and caption`() {
        val block = json.decodeFromJsonElement<NotionBlockResponse>(NotionJsonFixtures.block("code"))

        val code = checkNotNull(block.code)
        assertThat(code.language).isEqualTo("kotlin")
        assertThat(code.richText.map { it.plainText }).containsExactly(
            "fun main() {\n",
            "    println(\"Hello\")\n}\n",
        )
        assertThat(code.caption.map { it.plainText }).containsExactly("Example program")
    }

    @Test
    fun `decodes table dimensions and header flags`() {
        val block = json.decodeFromJsonElement<NotionBlockResponse>(NotionJsonFixtures.block("table"))

        val table = checkNotNull(block.table)
        assertThat(table.tableWidth).isEqualTo(3)
        assertThat(table.hasColumnHeader).isTrue()
        assertThat(table.hasRowHeader).isFalse()
        assertThat(block.hasChildren).isTrue()
    }

    @Test
    fun `preserves multiple rich text fragments and empty cells in a row`() {
        val block = json.decodeFromJsonElement<NotionBlockResponse>(NotionJsonFixtures.block("table_row"))

        val cells = checkNotNull(block.tableRow).cells
        assertThat(cells).hasSize(3)
        assertThat(cells[0].map { it.plainText }).containsExactly("Stores", " application data")
        assertThat(cells[0][0].annotations?.bold).isTrue()
        assertThat(cells[1]).isEmpty()
        assertThat(cells[2].map { it.plainText }).containsExactly("PostgreSQL")
    }

    @ParameterizedTest
    @CsvSource("child_page,Separate source", "child_database,Out of scope")
    fun `retains child references without requiring their contents`(type: String, title: String) {
        val block = json.decodeFromJsonElement<NotionBlockResponse>(NotionJsonFixtures.block(type))

        val reference = if (type == "child_page") block.childPage else block.childDatabase
        assertThat(reference?.title).isEqualTo(title)
    }

    @Test
    fun `ignores unsupported payload fields while retaining block identity`() {
        val block = json.decodeFromString<NotionBlockResponse>(
            """
            {"id":"future-block", "type":"future_type", "has_children":true,
             "future_type":{"new_field":42}, "another_new_field":"ignored"}
            """.trimIndent(),
        )

        assertThat(block.id).isEqualTo("future-block")
        assertThat(block.type).isEqualTo("future_type")
        assertThat(block.hasChildren).isTrue()
        assertThat(block.paragraph).isNull()
        assertThat(block.tableRow).isNull()
    }

    @Test
    fun `allows empty text but rejects missing text in a known text payload`() {
        val empty = json.decodeFromString<NotionTextBlockPayload>("""{"rich_text":[]}""")
        assertThat(empty.richText).isEmpty()

        assertThatThrownBy {
            json.decodeFromString<NotionTextBlockPayload>("{}")
        }.isInstanceOf(SerializationException::class.java)
    }

    @Test
    fun `decodes the complete mixed block batch`() {
        val response = json.decodeFromString<NotionBlocksResponse>(NotionJsonFixtures.read("supported-blocks.json"))

        assertThat(response.results).hasSize(15)
        assertThat(response.results.map { it.id }).doesNotHaveDuplicates()
        assertThat(response.nextCursor).isNull()
        assertThat(response.hasMore).isFalse()
    }
}
