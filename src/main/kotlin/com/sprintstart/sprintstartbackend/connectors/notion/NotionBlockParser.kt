package com.sprintstart.sprintstartbackend.connectors.notion

import com.sprintstart.sprintstartbackend.connectors.notion.client.NotionBlockNode
import org.springframework.stereotype.Component

@Component
class NotionBlockParser {
    fun parse(nodes: List<NotionBlockNode>): ParsedNotionBody {
        val bodyBlocks = mutableListOf<String>()
        val sections = mutableListOf<ParsedNotionSection>()
        val tables = mutableListOf<String>()
        val codeBlocks = mutableListOf<ParsedNotionCodeBlock>()

        recursiveExtract(
            nodes = nodes,
            bodyBlocks = bodyBlocks,
            sections = sections,
            tables = tables,
            codeBlocks = codeBlocks,
        )

        return ParsedNotionBody(
            bodyText = bodyBlocks.joinToString(separator = "\n\n"),
            sections = sections,
            tables = tables,
            codeBlocks = codeBlocks,
        )
    }

    private fun recursiveExtract(
        nodes: List<NotionBlockNode>,
        bodyBlocks: MutableList<String>,
        sections: MutableList<ParsedNotionSection>,
        tables: MutableList<String>,
        codeBlocks: MutableList<ParsedNotionCodeBlock>,
        indentation: String = "",
    ) {
        for (node in nodes) {
            val block = node.block

            if (block.type == "child_page" || block.type == "child_database") {
                continue
            }

            val markdown = when (block.type) {
                "table" -> {
                    val table = renderTable(node)
                    if (table.isNotBlank()) {
                        tables.add(table)
                    }
                    table
                }

                "paragraph" -> renderRichText(checkNotNull(block.paragraph).richText)

                "heading_1" -> {
                    val heading = checkNotNull(block.heading1)
                    val text = renderRichText(heading.richText)
                    val plainText = heading.richText.joinToString("") { it.plainText }.trim()
                    if (plainText.isBlank()) {
                        null
                    } else {
                        sections.add(ParsedNotionSection(plainText, 1))
                        "# $text"
                    }
                }

                "heading_2" -> {
                    val heading = checkNotNull(block.heading2)
                    val text = renderRichText(heading.richText)
                    val plainText = heading.richText.joinToString("") { it.plainText }.trim()
                    if (plainText.isBlank()) {
                        null
                    } else {
                        sections.add(ParsedNotionSection(plainText, 2))
                        "## $text"
                    }
                }

                "heading_3" -> {
                    val heading = checkNotNull(block.heading3)
                    val text = renderRichText(heading.richText)
                    val plainText = heading.richText.joinToString("") { it.plainText }.trim()
                    if (plainText.isBlank()) {
                        null
                    } else {
                        sections.add(ParsedNotionSection(plainText, 3))
                        "### $text"
                    }
                }

                "heading_4" -> {
                    val heading = checkNotNull(block.heading4)
                    val text = renderRichText(heading.richText)
                    val plainText = heading.richText.joinToString("") { it.plainText }.trim()
                    if (plainText.isBlank()) {
                        null
                    } else {
                        sections.add(ParsedNotionSection(plainText, 4))
                        "#### $text"
                    }
                }

                "bulleted_list_item" -> {
                    val item = checkNotNull(block.bulletedListItem)
                    "- ${renderRichText(item.richText)}"
                }

                "numbered_list_item" -> {
                    val item = checkNotNull(block.numberedListItem)
                    "1. ${renderRichText(item.richText)}"
                }

                "to_do" -> {
                    val toDo = checkNotNull(block.toDo)
                    val checkbox = if (toDo.checked) "[x]" else "[ ]"
                    "- $checkbox ${renderRichText(toDo.richText)}"
                }

                "quote" -> {
                    val quote = checkNotNull(block.quote)
                    val text = renderRichText(quote.richText)
                    text.lines().joinToString(separator = "\n") { line -> "> $line" }
                }

                "callout" -> {
                    val callout = checkNotNull(block.callout)
                    val text = renderRichText(callout.richText)
                    text.lines().joinToString(separator = "\n") { line -> "> $line" }
                }

                "toggle" -> {
                    val toggle = checkNotNull(block.toggle)
                    renderRichText(toggle.richText)
                }

                "code" -> {
                    val code = checkNotNull(block.code)
                    val text = code.richText.joinToString(separator = "") { fragment -> fragment.plainText }
                    codeBlocks.add(ParsedNotionCodeBlock(code.language, text))
                    val codeMarkdown = "```${code.language}\n$text\n```"
                    val caption = renderRichText(code.caption)
                    listOf(codeMarkdown, caption)
                        .filter { part -> part.isNotBlank() }
                        .joinToString(separator = "\n\n")
                }

                else -> null
            }

            if (!markdown.isNullOrBlank()) {
                bodyBlocks.add(markdown.prependIndent(indentation))
            }

            if (block.type != "table") {
                val childIndentation = if (block.type in LIST_TYPES) {
                    indentation + "   "
                } else {
                    indentation
                }
                recursiveExtract(
                    nodes = node.children,
                    bodyBlocks = bodyBlocks,
                    sections = sections,
                    tables = tables,
                    codeBlocks = codeBlocks,
                    indentation = childIndentation,
                )
            }
        }
    }

    internal fun extractCellTexts(row: NotionTableRow): List<String> {
        return row.cells.map { cell ->
            cell.joinToString(separator = "") { fragment -> fragment.plainText }
        }
    }

    private fun renderRichText(fragments: List<NotionRichText>): String {
        return fragments.joinToString(separator = "") { fragment ->
            var text = fragment.plainText
            val annotations = fragment.annotations

            if (annotations?.code == true) {
                text = "`$text`"
            } else {
                if (annotations?.bold == true) {
                    text = "**$text**"
                }
                if (annotations?.italic == true) {
                    text = "*$text*"
                }
                if (annotations?.strikethrough == true) {
                    text = "~~$text~~"
                }
            }

            if (fragment.href != null) {
                text = "[$text](${fragment.href})"
            }
            text
        }
    }

    private fun renderTable(node: NotionBlockNode): String {
        val table = checkNotNull(node.block.table)
        require(table.tableWidth > 0) { "Notion table width must be positive" }

        val rows = node.children.map { child ->
            val tableRow = checkNotNull(child.block.tableRow)
            val cells = extractCellTexts(tableRow)
            List(table.tableWidth) { index ->
                normalizeTableCell(cells.getOrNull(index).orEmpty())
            }
        }
        if (rows.isEmpty()) {
            return ""
        }

        val header = if (table.hasColumnHeader) {
            rows.first()
        } else {
            List(table.tableWidth) { "" }
        }
        val dataRows = if (table.hasColumnHeader) rows.drop(1) else rows
        val separator = List(table.tableWidth) { "---" }

        return (listOf(header, separator) + dataRows).joinToString(separator = "\n") { row ->
            row.joinToString(prefix = "| ", separator = " | ", postfix = " |")
        }
    }

    private fun normalizeTableCell(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace("|", "\\|")
            .replace(Regex("\r\n|\r|\n"), " ")
    }

    private companion object {
        val LIST_TYPES = setOf("bulleted_list_item", "numbered_list_item", "to_do")
    }
}
