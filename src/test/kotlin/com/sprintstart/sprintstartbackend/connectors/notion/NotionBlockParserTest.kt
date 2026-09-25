package com.sprintstart.sprintstartbackend.connectors.notion

import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionBlockNode
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionBlockResponse
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionCodePayload
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionHeadingPayload
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionTablePayload
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionTextBlockPayload
import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionToDoPayload
import kotlinx.serialization.json.Json
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class NotionBlockParserTest {
    private val parser = NotionBlockParser()

    @Test
    fun `renders document order and collects plain headings tables and raw code`() {
        val heading = NotionBlockNode(
            NotionBlockResponse("heading", "heading_1", false, heading1 = NotionHeadingPayload(
                listOf(NotionRichText("Setup", annotations = annotations(bold = true))),
            )),
        )
        val result = parser.parse(listOf(
            heading,
            textNode("paragraph", "Install Docker"),
            table(listOf(listOf("Name", "Purpose"), listOf("DB", "Storage"))),
            code("val answer = 42"),
        ))

        val markdownTable = "| Name | Purpose |\n| --- | --- |\n| DB | Storage |"
        assertThat(result.bodyText).isEqualTo(
            "# **Setup**\n\nInstall Docker\n\n$markdownTable\n\n```kotlin\nval answer = 42\n```",
        )
        assertThat(result.sections).containsExactly(ParsedNotionSection("Setup", 1))
        assertThat(result.tables).containsExactly(markdownTable)
        assertThat(result.codeBlocks).containsExactly(ParsedNotionCodeBlock("kotlin", "val answer = 42"))
    }

    @Test
    fun `empty input has no metadata and calls do not share mutable state`() {
        parser.parse(listOf(table(listOf(listOf("A", "B"))), code("hello")))

        assertThat(parser.parse(emptyList())).isEqualTo(ParsedNotionBody(""))
    }

    @Test
    fun `keeps nested list descendants indented`() {
        val nodes = listOf(
            textNode("numbered_list_item", "First", listOf(
                textNode("bulleted_list_item", "Child", listOf(textNode("bulleted_list_item", "Grandchild"))),
            )),
            textNode("numbered_list_item", "Second"),
            textNode("paragraph", "Break"),
            textNode("numbered_list_item", "Restart"),
        )

        assertThat(parser.parse(nodes).bodyText).isEqualTo(
            "1. First\n\n   - Child\n\n      - Grandchild\n\n1. Second\n\nBreak\n\n1. Restart",
        )
    }

    @Test
    fun `indents multiline list text and child paragraphs within their list item`() {
        val nodes = listOf(textNode("numbered_list_item", "First\ncontinued", listOf(textNode("paragraph", "Details"))))

        assertThat(parser.parse(nodes).bodyText).isEqualTo("1. First\ncontinued\n\n   Details")
    }

    @Test
    fun `preserves toggle and unknown container children without accidental code indentation`() {
        val columns = textNode("column_list", "", listOf(textNode("column", "", listOf(textNode("paragraph", "Inside")))))
        val toggle = textNode("toggle", "Details", listOf(columns))

        assertThat(parser.parse(listOf(toggle)).bodyText).isEqualTo("Details\n\nInside")
    }

    @ParameterizedTest
    @ValueSource(strings = ["quote", "callout"])
    fun `quotes the quote or callout text and preserves descendants`(type: String) {
        val node = textNode(type, "First\nSecond", listOf(textNode("paragraph", "Child")))

        assertThat(parser.parse(listOf(node)).bodyText).isEqualTo("> First\n> Second\n\nChild")
    }

    @Test
    fun `renders both checkbox states`() {
        assertThat(parser.parse(listOf(
            textNode("to_do", "Done", checked = true),
            textNode("to_do", "Pending"),
        )).bodyText).isEqualTo("- [x] Done\n\n- [ ] Pending")
    }

    @ParameterizedTest
    @ValueSource(strings = ["child_page", "child_database"])
    fun `does not parse descendants of document boundaries even if supplied`(type: String) {
        val node = textNode(type, "", listOf(code("private child")))

        assertThat(parser.parse(listOf(node))).isEqualTo(ParsedNotionBody(""))
    }

    @Test
    fun `decodes and renders all four heading levels and ignores empty headings`() {
        val nodes = (1..4).map { level ->
            val json = """{"id":"h$level","type":"heading_$level","has_children":false,"heading_$level":{"rich_text":[{"plain_text":"Title $level"}]}}"""
            NotionBlockNode(Json.decodeFromString<NotionBlockResponse>(json))
        } + NotionBlockNode(NotionBlockResponse("empty", "heading_1", false, heading1 = NotionHeadingPayload(emptyList())))

        val parsed = parser.parse(nodes)

        assertThat(parsed.bodyText).isEqualTo("# Title 1\n\n## Title 2\n\n### Title 3\n\n#### Title 4")
        assertThat(parsed.sections).containsExactlyElementsOf((1..4).map { ParsedNotionSection("Title $it", it) })
    }

    @Test
    fun `keeps nested section metadata in document order`() {
        val child = NotionBlockNode(
            NotionBlockResponse(
                "child-heading",
                "heading_2",
                false,
                heading2 = NotionHeadingPayload(listOf(NotionRichText("Child"))),
            ),
        )
        val parent = NotionBlockNode(
            NotionBlockResponse(
                "parent-heading",
                "heading_1",
                true,
                heading1 = NotionHeadingPayload(listOf(NotionRichText("Parent"))),
            ),
            listOf(child),
        )

        assertThat(parser.parse(listOf(parent)).sections).containsExactly(
            ParsedNotionSection("Parent", 1),
            ParsedNotionSection("Child", 2),
        )
    }

    @Test
    fun `table without headers retains its first row and all empty cells`() {
        val result = parser.parse(listOf(table(listOf(listOf("abc", "", "a", "")), width = 4, header = false)))

        assertThat(result.bodyText).isEqualTo("|  |  |  |  |\n| --- | --- | --- | --- |\n| abc |  | a |  |")
        assertThat(result.tables).containsExactly(result.bodyText)
    }

    @Test
    fun `normalizes table width and escapes pipes backslashes and line breaks in headers and data`() {
        val result = parser.parse(listOf(table(listOf(
            listOf("A|B", "C\\D"),
            listOf("One\r\nTwo"),
            listOf("1", "2", "extra"),
        ))))

        assertThat(result.bodyText).isEqualTo(
            "| A\\|B | C\\\\D |\n| --- | --- |\n| One Two |  |\n| 1 | 2 |",
        )
    }

    @Test
    fun `empty tables add neither body text nor metadata`() {
        assertThat(parser.parse(listOf(table(emptyList())))).isEqualTo(ParsedNotionBody(""))
    }

    @Test
    fun `renders code metadata and caption`() {
        val source = "val x = 1"
        val node = NotionBlockNode(NotionBlockResponse("code", "code", false, code = NotionCodePayload(
            richText = listOf(NotionRichText(source, annotations = annotations(bold = true))),
            language = "kotlin",
            caption = listOf(NotionRichText("Example")),
        )))
        val result = parser.parse(listOf(node))

        assertThat(result.bodyText).isEqualTo("```kotlin\n$source\n```\n\nExample")
        assertThat(result.codeBlocks).containsExactly(ParsedNotionCodeBlock("kotlin", source))
    }

    @Test
    fun `renders bold rich text`() {
        val node = paragraph(listOf(
            NotionRichText("Read "),
            NotionRichText("docs", annotations = annotations(bold = true)),
            NotionRichText(" later"),
        ))

        assertThat(parser.parse(listOf(node)).bodyText).isEqualTo("Read **docs** later")
    }

    @Test
    fun `renders italic and strike annotations`() {
        val node = paragraph(listOf(
            NotionRichText("Hello", annotations = annotations(bold = true)),
            NotionRichText(" "),
            NotionRichText("world", annotations = annotations(italic = true, strike = true)),
        ))

        assertThat(parser.parse(listOf(node)).bodyText).isEqualTo("**Hello** ~~*world*~~")
    }

    @Test
    fun `renders inline code`() {
        val node = paragraph(listOf(NotionRichText("answer", annotations = annotations(code = true, bold = true))))

        assertThat(parser.parse(listOf(node)).bodyText).isEqualTo("`answer`")
    }

    @Test
    fun `renders a rich text link`() {
        val node = paragraph(listOf(NotionRichText("Docs", href = "https://example.com/docs")))

        assertThat(parser.parse(listOf(node)).bodyText)
            .isEqualTo("[Docs](https://example.com/docs)")
    }

    @Test
    fun `rejects missing known payloads invalid widths and invalid table children`() {
        val missing = NotionBlockNode(NotionBlockResponse("p", "paragraph", false))
        assertThrows<IllegalStateException> { parser.parse(listOf(missing)) }
        assertThrows<IllegalArgumentException> { parser.parse(listOf(table(emptyList(), width = 0))) }
        val invalidTable = table(emptyList()).copy(children = listOf(textNode("paragraph", "bad row")))
        assertThrows<IllegalStateException> { parser.parse(listOf(invalidTable)) }
    }

    private fun paragraph(fragments: List<NotionRichText>): NotionBlockNode {
        return NotionBlockNode(NotionBlockResponse("p", "paragraph", false, paragraph = NotionTextBlockPayload(fragments)))
    }

    private fun textNode(
        type: String,
        text: String,
        children: List<NotionBlockNode> = emptyList(),
        checked: Boolean = false,
    ): NotionBlockNode {
        val payload = NotionTextBlockPayload(listOf(NotionRichText(text)))
        val block = NotionBlockResponse(
            id = type,
            type = type,
            hasChildren = children.isNotEmpty(),
            paragraph = payload.takeIf { type == "paragraph" },
            bulletedListItem = payload.takeIf { type == "bulleted_list_item" },
            numberedListItem = payload.takeIf { type == "numbered_list_item" },
            toDo = NotionToDoPayload(payload.richText, checked).takeIf { type == "to_do" },
            quote = payload.takeIf { type == "quote" },
            callout = payload.takeIf { type == "callout" },
            toggle = payload.takeIf { type == "toggle" },
        )
        return NotionBlockNode(block, children)
    }

    private fun table(rows: List<List<String>>, width: Int = 2, header: Boolean = true): NotionBlockNode {
        val children = rows.mapIndexed { index, cells ->
            NotionBlockNode(NotionBlockResponse(
                "$index", "table_row", false,
                tableRow = NotionTableRow(cells.map { listOf(NotionRichText(it)) }),
            ))
        }
        return NotionBlockNode(
            NotionBlockResponse("table", "table", true, table = NotionTablePayload(width, header, false)),
            children,
        )
    }

    private fun code(text: String): NotionBlockNode {
        return NotionBlockNode(NotionBlockResponse(
            "code", "code", false, code = NotionCodePayload(listOf(NotionRichText(text)), "kotlin"),
        ))
    }

    private fun annotations(
        bold: Boolean = false,
        italic: Boolean = false,
        strike: Boolean = false,
        code: Boolean = false,
    ): NotionTextAnnotations {
        return NotionTextAnnotations(bold, italic, strike, false, code, "default")
    }
}
