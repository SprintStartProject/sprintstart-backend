package com.sprintstart.sprintstartbackend.connectors.confluence.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.DataNode
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import org.springframework.stereotype.Component

data class ParsedConfluenceBody(
    val bodyText: String,
    val sections: List<ParsedConfluenceSection> = emptyList(),
    val tables: List<String> = emptyList(),
    val codeBlocks: List<ParsedConfluenceCodeBlock> = emptyList(),
)

data class ParsedConfluenceSection(
    val heading: String,
    val level: Int,
)

data class ParsedConfluenceCodeBlock(
    val language: String?,
    val code: String,
)

/**
 * Converts Confluence storage-format XHTML into the content shape used by Confluence ingestion.
 *
 * The parser extracts rich Confluence-specific structures first and removes them from the parsed
 * tree before reading `bodyText`. That keeps code macro bodies, unsupported macro markup, and table
 * markup from leaking into the clean text that will later be indexed.
 */
@Component
class ConfluenceStorageFormatParser {
    fun parse(storageXhtml: String?): ParsedConfluenceBody {
        if (storageXhtml.isNullOrBlank()) {
            return ParsedConfluenceBody(bodyText = "")
        }

        val document = Jsoup.parse(storageXhtml, "", Parser.xmlParser())

        val codeBlocks = extractCodeBlocks(document)
        removeUnsupportedMacros(document)

        val tables = extractTables(document)
        val sections = extractSections(document)
        val bodyText = extractBodyText(document)

        return ParsedConfluenceBody(
            bodyText = bodyText,
            sections = sections,
            tables = tables,
            codeBlocks = codeBlocks,
        )
    }

    private fun extractCodeBlocks(document: Document): List<ParsedConfluenceCodeBlock> {
        return document
            .getElementsByTag(CONFLUENCE_STRUCTURED_MACRO_TAG)
            .filter { macro -> macro.attr(CONFLUENCE_NAME_ATTRIBUTE) == CODE_MACRO_NAME }
            .map { macro ->
                val language = macro
                    .getElementsByTag(CONFLUENCE_PARAMETER_TAG)
                    .firstOrNull { parameter -> parameter.attr(CONFLUENCE_NAME_ATTRIBUTE) == LANGUAGE_PARAMETER_NAME }
                    ?.text()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }

                val code = macro
                    .getElementsByTag(CONFLUENCE_PLAIN_TEXT_BODY_TAG)
                    .firstOrNull()
                    ?.codeText()
                    ?.trim()
                    .orEmpty()

                macro.remove()

                ParsedConfluenceCodeBlock(
                    language = language,
                    code = code,
                )
            }
    }

    private fun removeUnsupportedMacros(document: Document) {
        document
            .getElementsByTag(CONFLUENCE_STRUCTURED_MACRO_TAG)
            .forEach { macro -> macro.remove() }
    }

    private fun extractTables(document: Document): List<String> {
        return document
            .getElementsByTag(TABLE_TAG)
            .mapNotNull { table ->
                val markdown = tableToMarkdown(table)
                table.remove()
                markdown
            }
    }

    private fun tableToMarkdown(table: Element): String? {
        val rows = table
            .getElementsByTag(TABLE_ROW_TAG)
            .map { row -> row.toMarkdownCells() }
            .filter { row -> row.isNotEmpty() }

        if (rows.isEmpty()) {
            return null
        }

        val header = rows.first()
        val separator = header.map { MARKDOWN_SEPARATOR_CELL }
        val bodyRows = rows.drop(1)

        return (listOf(header, separator) + bodyRows).joinToString("\n") { cells ->
            cells.joinToString(
                prefix = "| ",
                separator = " | ",
                postfix = " |",
            )
        }
    }

    private fun Element.toMarkdownCells(): List<String> {
        return children()
            .filter { cell -> cell.tagName() == TABLE_HEADER_CELL_TAG || cell.tagName() == TABLE_BODY_CELL_TAG }
            .map { cell -> cell.text().trim().escapeMarkdownTableCell() }
    }

    private fun extractSections(document: Document): List<ParsedConfluenceSection> {
        return document
            .getAllElements()
            .mapNotNull { element ->
                val tag = element.tagName().lowercase()

                if (tag !in HEADING_TAGS) {
                    return@mapNotNull null
                }

                val heading = element.text().trim()
                if (heading.isBlank()) {
                    return@mapNotNull null
                }

                ParsedConfluenceSection(
                    heading = heading,
                    level = tag.removePrefix(HEADING_TAG_PREFIX).toInt(),
                )
            }
    }

    private fun extractBodyText(document: Document): String {
        val lines = document
            .getAllElements()
            .filter { element -> element.tagName().lowercase() in BODY_TEXT_TAGS }
            .map { element -> element.text().trim() }
            .filter { text -> text.isNotBlank() }

        if (lines.isNotEmpty()) {
            return lines.joinToString("\n")
        }

        return document.text().trim()
    }

    private fun Element.codeText(): String {
        return childNodes().joinToString(separator = "") { node -> node.codeText() }
    }

    private fun Node.codeText(): String {
        return when (this) {
            is TextNode -> wholeText
            is DataNode -> wholeData
            is Element -> codeText()
            else -> ""
        }
    }

    private fun String.escapeMarkdownTableCell(): String {
        return replace("|", "\\|")
    }

    private companion object {
        const val CONFLUENCE_STRUCTURED_MACRO_TAG = "ac:structured-macro"
        const val CONFLUENCE_PARAMETER_TAG = "ac:parameter"
        const val CONFLUENCE_PLAIN_TEXT_BODY_TAG = "ac:plain-text-body"
        const val CONFLUENCE_NAME_ATTRIBUTE = "ac:name"
        const val CODE_MACRO_NAME = "code"
        const val LANGUAGE_PARAMETER_NAME = "language"
        const val TABLE_TAG = "table"
        const val TABLE_ROW_TAG = "tr"
        const val TABLE_HEADER_CELL_TAG = "th"
        const val TABLE_BODY_CELL_TAG = "td"
        const val MARKDOWN_SEPARATOR_CELL = "---"
        const val HEADING_TAG_PREFIX = "h"

        val HEADING_TAGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
        val BODY_TEXT_TAGS = HEADING_TAGS + setOf("p", "li")
    }
}
