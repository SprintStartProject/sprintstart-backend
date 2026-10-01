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

            val markdown = renderBlock(node, sections, tables, codeBlocks)

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

    private fun renderBlock(
        node: NotionBlockNode,
        sections: MutableList<ParsedNotionSection>,
        tables: MutableList<String>,
        codeBlocks: MutableList<ParsedNotionCodeBlock>,
    ): String? {
        val block = node.block
        return when (block.type) {
            "table" -> {
                val table = renderTable(node)
                if (table.isNotBlank()) {
                    tables.add(table)
                }
                table
            }

            "paragraph" -> renderRichText(checkNotNull(block.paragraph).richText)
            in HEADING_TYPES -> renderHeading(node, sections)
            in LIST_TYPES -> renderListItem(node)
            "quote", "callout" -> renderQuote(node)
            "toggle" -> renderRichText(checkNotNull(block.toggle).richText)
            "code" -> renderCode(node, codeBlocks)
            else -> null
        }
    }

    private fun renderHeading(
        node: NotionBlockNode,
        sections: MutableList<ParsedNotionSection>,
    ): String? {
        val block = node.block
        val level = block.type.removePrefix("heading_").toInt()
        val richText = when (level) {
            1 -> checkNotNull(block.heading1).richText
            2 -> checkNotNull(block.heading2).richText
            3 -> checkNotNull(block.heading3).richText
            4 -> checkNotNull(block.heading4).richText
            else -> return null
        }
        val plainText = richText.joinToString("") { fragment -> fragment.plainText }.trim()
        if (plainText.isBlank()) {
            return null
        }
        sections.add(ParsedNotionSection(plainText, level))
        return "${"#".repeat(level)} ${renderRichText(richText)}"
    }

    private fun renderListItem(node: NotionBlockNode): String {
        val block = node.block
        return when (block.type) {
            "bulleted_list_item" -> "- ${renderRichText(checkNotNull(block.bulletedListItem).richText)}"
            "numbered_list_item" -> "1. ${renderRichText(checkNotNull(block.numberedListItem).richText)}"
            "to_do" -> {
                val toDo = checkNotNull(block.toDo)
                val checkbox = if (toDo.checked) "[x]" else "[ ]"
                "- $checkbox ${renderRichText(toDo.richText)}"
            }

            else -> error("Unsupported Notion list block type: ${block.type}")
        }
    }

    private fun renderQuote(node: NotionBlockNode): String {
        val block = node.block
        val richText = if (block.type == "quote") {
            checkNotNull(block.quote).richText
        } else {
            checkNotNull(block.callout).richText
        }
        return renderRichText(richText)
            .lines()
            .joinToString(separator = "\n") { line -> "> $line" }
    }

    private fun renderCode(
        node: NotionBlockNode,
        codeBlocks: MutableList<ParsedNotionCodeBlock>,
    ): String {
        val code = checkNotNull(node.block.code)
        val text = code.richText.joinToString(separator = "") { fragment -> fragment.plainText }
        codeBlocks.add(ParsedNotionCodeBlock(code.language, text))
        val codeMarkdown = "```${code.language}\n$text\n```"
        val caption = renderRichText(code.caption)
        return listOf(codeMarkdown, caption)
            .filter { part -> part.isNotBlank() }
            .joinToString(separator = "\n\n")
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
        val HEADING_TYPES = setOf("heading_1", "heading_2", "heading_3", "heading_4")
        val LIST_TYPES = setOf("bulleted_list_item", "numbered_list_item", "to_do")
    }
}
