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
 * Converts Confluence storage-format XHTML into the Markdown body used by Confluence ingestion.
 *
 * The document is walked in document order and every supported element is emitted as a Markdown
 * block, so headings, lists, tables, and code macros keep both their structure and their position
 * on the page. Blocks are separated by a blank line, which is what Markdown rendering and
 * paragraph-based chunking expect. Headings, tables, and code macros are additionally collected
 * into [ParsedConfluenceBody] so ingestion can keep them in the artifact metadata. Structured
 * macros other than the code macro are dropped, because their markup carries no readable text.
 */
@Component
class ConfluenceStorageFormatParser {
    fun parse(storageXhtml: String?): ParsedConfluenceBody {
        if (storageXhtml.isNullOrBlank()) {
            return ParsedConfluenceBody(bodyText = "")
        }

        val document: Document = Jsoup.parse(storageXhtml, "", Parser.xmlParser())
        val renderer = ConfluenceBodyRenderer()
        renderer.render(document)

        return ParsedConfluenceBody(
            bodyText = renderer.blocks.joinToString(BLOCK_SEPARATOR),
            sections = renderer.sections,
            tables = renderer.tables,
            codeBlocks = renderer.codeBlocks,
        )
    }
}

/**
 * Collects the Markdown blocks and the structured metadata of one storage-format document.
 *
 * Inline content between block elements is buffered and flushed as its own paragraph, so text that
 * sits directly inside a layout container is not lost.
 */
private class ConfluenceBodyRenderer {
    val blocks = mutableListOf<String>()
    val sections = mutableListOf<ParsedConfluenceSection>()
    val tables = mutableListOf<String>()
    val codeBlocks = mutableListOf<ParsedConfluenceCodeBlock>()

    private val pendingInline = StringBuilder()

    fun render(node: Node) {
        node.childNodes().forEach { child -> renderNode(child) }
        flushInline()
    }

    private fun renderNode(node: Node) {
        when (node) {
            is TextNode -> pendingInline.append(InlineMarkdown.escape(node.wholeText))
            is Element -> renderElement(node)
            else -> Unit
        }
    }

    private fun renderElement(element: Element) {
        when (val tag = element.tagName().lowercase()) {
            in HEADING_TAGS -> appendHeading(element, tag)
            PARAGRAPH_TAG -> appendParagraph(element)
            in LIST_TAGS -> appendList(element)
            TABLE_TAG -> appendTable(element)
            CONFLUENCE_STRUCTURED_MACRO_TAG -> appendCodeMacro(element)
            in INLINE_TAGS -> pendingInline.append(InlineMarkdown.renderNode(element))
            else -> {
                flushInline()
                render(element)
            }
        }
    }

    private fun appendHeading(element: Element, tag: String) {
        flushInline()
        val heading = element.text().trim()
        if (heading.isBlank()) {
            return
        }

        val level = tag.removePrefix(HEADING_TAG_PREFIX).toInt()
        sections += ParsedConfluenceSection(heading = heading, level = level)
        blocks += "${HEADING_MARKER.repeat(level)} ${InlineMarkdown.renderChildren(element).trim()}"
    }

    private fun appendParagraph(element: Element) {
        flushInline()
        val text = InlineMarkdown.renderChildren(element).trim()
        if (text.isNotBlank()) {
            blocks += InlineMarkdown.escapeBlockStart(text)
        }
    }

    private fun appendList(element: Element) {
        flushInline()
        val lines = listLines(element, 0)
        if (lines.isNotEmpty()) {
            blocks += lines.joinToString(LINE_SEPARATOR)
        }
    }

    private fun listLines(list: Element, depth: Int): List<String> {
        val ordered = list.tagName().lowercase() == ORDERED_LIST_TAG
        val lines = mutableListOf<String>()
        list
            .children()
            .filter { item -> item.tagName().lowercase() == LIST_ITEM_TAG }
            .forEachIndexed { index, item ->
                val marker = if (ordered) "${index + 1}." else UNORDERED_LIST_MARKER
                val text = InlineMarkdown.escapeBlockStart(InlineMarkdown.renderChildren(item).trim())
                lines += "${LIST_INDENT.repeat(depth)}$marker $text".trimEnd()
                item
                    .children()
                    .filter { child -> child.tagName().lowercase() in LIST_TAGS }
                    .forEach { nested -> lines += listLines(nested, depth + 1) }
            }
        return lines
    }

    private fun appendTable(element: Element) {
        flushInline()
        val markdown = InlineMarkdown.tableMarkdown(element) ?: return
        tables += markdown
        blocks += markdown
    }

    private fun appendCodeMacro(macro: Element) {
        flushInline()
        if (macro.attr(CONFLUENCE_NAME_ATTRIBUTE) != CODE_MACRO_NAME) {
            return
        }

        val language = macro
            .getElementsByTag(CONFLUENCE_PARAMETER_TAG)
            .firstOrNull { parameter -> parameter.attr(CONFLUENCE_NAME_ATTRIBUTE) == LANGUAGE_PARAMETER_NAME }
            ?.text()
            ?.trim()
            ?.takeIf { value -> value.isNotBlank() }

        val code = macro
            .getElementsByTag(CONFLUENCE_PLAIN_TEXT_BODY_TAG)
            .firstOrNull()
            ?.rawText()
            ?.trim()
            .orEmpty()

        codeBlocks += ParsedConfluenceCodeBlock(language = language, code = code)
        if (code.isNotBlank()) {
            blocks += "$CODE_FENCE${language.orEmpty()}$LINE_SEPARATOR$code$LINE_SEPARATOR$CODE_FENCE"
        }
    }

    private fun flushInline() {
        val text = pendingInline.toString().trim()
        pendingInline.setLength(0)
        if (text.isNotBlank()) {
            blocks += InlineMarkdown.escapeBlockStart(text)
        }
    }
}

/** Renders inline storage-format markup, table markup, and Markdown escaping. */
private object InlineMarkdown {
    fun renderChildren(node: Node): String {
        return node.childNodes().joinToString(separator = "") { child -> renderNode(child) }
    }

    fun renderNode(node: Node): String {
        if (node is TextNode) {
            return escape(node.wholeText)
        }
        if (node !is Element) {
            return ""
        }

        return when (node.tagName().lowercase()) {
            LINE_BREAK_TAG -> LINE_BREAK
            PARAGRAPH_TAG -> "${renderChildren(node)} "
            in STRONG_TAGS -> emphasis(renderChildren(node), STRONG_MARKER)
            in EMPHASIS_TAGS -> emphasis(renderChildren(node), EMPHASIS_MARKER)
            CODE_TAG -> inlineCode(node.text())
            ANCHOR_TAG -> link(node)
            CONFLUENCE_LINK_TAG -> confluenceLink(node)
            in SKIPPED_INLINE_TAGS -> ""
            else -> renderChildren(node)
        }
    }

    fun tableMarkdown(table: Element): String? {
        val rows = table
            .getElementsByTag(TABLE_ROW_TAG)
            .map { row -> tableCells(row) }
            .filter { cells -> cells.isNotEmpty() }

        if (rows.isEmpty()) {
            return null
        }

        val header = rows.first()
        val separator = header.map { MARKDOWN_SEPARATOR_CELL }

        return (listOf(header, separator) + rows.drop(1)).joinToString(LINE_SEPARATOR) { cells ->
            cells.joinToString(
                prefix = "| ",
                separator = " | ",
                postfix = " |",
            )
        }
    }

    /**
     * Escapes the characters that would otherwise be read as Markdown syntax.
     *
     * Whitespace is collapsed first because storage-format markup is indented, and that raw
     * indentation would turn into unintended code blocks or ragged paragraphs.
     */
    fun escape(raw: String): String {
        val collapsed = raw.replace(WHITESPACE_PATTERN, " ")
        return collapsed.replace(MARKDOWN_SPECIAL_PATTERN) { match -> "\\${match.value}" }
    }

    /** Escapes a leading list marker so page text that starts with a dash stays a paragraph. */
    fun escapeBlockStart(text: String): String {
        return if (BLOCK_MARKER_PATTERN.containsMatchIn(text)) "\\$text" else text
    }

    private fun tableCells(row: Element): List<String> {
        return row
            .children()
            .filter { cell -> cell.tagName().lowercase() in TABLE_CELL_TAGS }
            .map { cell -> renderChildren(cell).replace(LINE_BREAK, " ").trim() }
    }

    private fun emphasis(text: String, marker: String): String {
        val trimmed = text.trim()
        return if (trimmed.isBlank()) "" else "$marker$trimmed$marker"
    }

    private fun inlineCode(text: String): String {
        val fence = if (text.contains(INLINE_CODE_MARKER)) DOUBLE_INLINE_CODE_MARKER else INLINE_CODE_MARKER
        return "$fence$text$fence"
    }

    private fun link(element: Element): String {
        val text = renderChildren(element).trim()
        val href = element.attr(HREF_ATTRIBUTE).trim()
        if (href.isBlank()) {
            return text
        }

        val label = text.ifBlank { escape(href) }
        val needsAngleBrackets = href.any { character -> character.isWhitespace() } || href.contains(')')
        val target = if (needsAngleBrackets) "<$href>" else href
        return "[$label]($target)"
    }

    /**
     * Renders a Confluence internal link as plain text.
     *
     * Storage format references the target by page title rather than by URL, and the tenant base
     * URL is not known inside the parser, so the label is kept without a link target.
     */
    private fun confluenceLink(element: Element): String {
        val body = element.getElementsByTag(CONFLUENCE_LINK_BODY_TAG).firstOrNull()
            ?: element.getElementsByTag(CONFLUENCE_PLAIN_TEXT_LINK_BODY_TAG).firstOrNull()
        val label = body?.rawText()?.trim()?.takeIf { value -> value.isNotBlank() }
            ?: element
                .getElementsByTag(CONFLUENCE_PAGE_TAG)
                .firstOrNull()
                ?.attr(CONFLUENCE_CONTENT_TITLE_ATTRIBUTE)
                ?.trim()

        return escape(label.orEmpty())
    }
}

/** Reads the verbatim text of a node, including the CDATA sections used by code macro bodies. */
private fun Node.rawText(): String {
    return when (this) {
        is TextNode -> wholeText
        is DataNode -> wholeData
        is Element -> childNodes().joinToString(separator = "") { child -> child.rawText() }
        else -> ""
    }
}

private const val BLOCK_SEPARATOR = "\n\n"
private const val LINE_SEPARATOR = "\n"
private const val LINE_BREAK = "  \n"
private const val HEADING_MARKER = "#"
private const val HEADING_TAG_PREFIX = "h"
private const val UNORDERED_LIST_MARKER = "-"
private const val LIST_INDENT = "  "
private const val STRONG_MARKER = "**"
private const val EMPHASIS_MARKER = "*"
private const val INLINE_CODE_MARKER = "`"
private const val DOUBLE_INLINE_CODE_MARKER = "``"
private const val CODE_FENCE = "```"
private const val MARKDOWN_SEPARATOR_CELL = "---"
private const val PARAGRAPH_TAG = "p"
private const val LINE_BREAK_TAG = "br"
private const val CODE_TAG = "code"
private const val ANCHOR_TAG = "a"
private const val HREF_ATTRIBUTE = "href"
private const val TABLE_TAG = "table"
private const val TABLE_ROW_TAG = "tr"
private const val ORDERED_LIST_TAG = "ol"
private const val LIST_ITEM_TAG = "li"
private const val CONFLUENCE_STRUCTURED_MACRO_TAG = "ac:structured-macro"
private const val CONFLUENCE_PARAMETER_TAG = "ac:parameter"
private const val CONFLUENCE_PLAIN_TEXT_BODY_TAG = "ac:plain-text-body"
private const val CONFLUENCE_LINK_TAG = "ac:link"
private const val CONFLUENCE_LINK_BODY_TAG = "ac:link-body"
private const val CONFLUENCE_PLAIN_TEXT_LINK_BODY_TAG = "ac:plain-text-link-body"
private const val CONFLUENCE_PAGE_TAG = "ri:page"
private const val CONFLUENCE_CONTENT_TITLE_ATTRIBUTE = "ri:content-title"
private const val CONFLUENCE_NAME_ATTRIBUTE = "ac:name"
private const val CODE_MACRO_NAME = "code"
private const val LANGUAGE_PARAMETER_NAME = "language"

private val HEADING_TAGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
private val LIST_TAGS = setOf("ul", "ol")
private val TABLE_CELL_TAGS = setOf("th", "td")
private val STRONG_TAGS = setOf("strong", "b")
private val EMPHASIS_TAGS = setOf("em", "i")
private val INLINE_TAGS = STRONG_TAGS + EMPHASIS_TAGS + setOf(
    LINE_BREAK_TAG,
    CODE_TAG,
    ANCHOR_TAG,
    CONFLUENCE_LINK_TAG,
    "span",
    "u",
    "s",
    "sub",
    "sup",
    "time",
)
private val SKIPPED_INLINE_TAGS = LIST_TAGS + setOf(TABLE_TAG, CONFLUENCE_STRUCTURED_MACRO_TAG)
private val WHITESPACE_PATTERN = Regex("""\s+""")
private val MARKDOWN_SPECIAL_PATTERN = Regex("""[\\`*_\[\]<>#|]""")
private val BLOCK_MARKER_PATTERN = Regex("""^([-+]\s|\d+[.)]\s)""")
