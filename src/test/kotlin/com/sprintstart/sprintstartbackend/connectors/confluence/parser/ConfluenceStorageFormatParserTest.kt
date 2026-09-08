package com.sprintstart.sprintstartbackend.connectors.confluence.parser

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ConfluenceStorageFormatParserTest {
    private val parser = ConfluenceStorageFormatParser()

    @Test
    fun `returns empty result for blank storage body`() {
        val result = parser.parse("  ")

        assertThat(result.bodyText).isEmpty()
        assertThat(result.sections).isEmpty()
        assertThat(result.tables).isEmpty()
        assertThat(result.codeBlocks).isEmpty()
    }

    @Test
    fun `renders headings, paragraphs and lists as markdown`() {
        val result = parser.parse(
            """
            <h2>Deployment</h2>
            <p>Deploy the <strong>service</strong> to Kubernetes.</p>
            <ul>
              <li>Build image</li>
              <li>Apply manifest</li>
            </ul>
            """.trimIndent(),
        )

        assertThat(result.bodyText).isEqualTo(
            """
            ## Deployment

            Deploy the **service** to Kubernetes.

            - Build image
            - Apply manifest
            """.trimIndent(),
        )
        assertThat(result.sections).containsExactly(
            ParsedConfluenceSection(heading = "Deployment", level = 2),
        )
        assertThat(result.tables).isEmpty()
        assertThat(result.codeBlocks).isEmpty()
    }

    @Test
    fun `renders nested and ordered lists with indentation`() {
        val result = parser.parse(
            """
            <ol>
              <li>First
                <ul><li>Nested</li></ul>
              </li>
              <li>Second</li>
            </ol>
            """.trimIndent(),
        )

        assertThat(result.bodyText).isEqualTo(
            """
            1. First
              - Nested
            2. Second
            """.trimIndent(),
        )
    }

    @Test
    fun `renders code macro as fenced block and keeps it in the code block list`() {
        val result = parser.parse(
            """
            <p local-id="4a8bfc667ef7">Here is the provided Java class:</p>
            <ac:structured-macro ac:name="code" ac:schema-version="1" ac:local-id="80efce0a5ecd" ac:macro-id="edd44f77-6761-410c-a994-b74a390f3ee7">
              <ac:parameter ac:name="language">java</ac:parameter>
              <ac:parameter ac:name="breakoutMode">wide</ac:parameter>
              <ac:parameter ac:name="breakoutWidth">760</ac:parameter>
              <ac:parameter ac:name="wrap">true</ac:parameter>
              <ac:plain-text-body><![CDATA[public class CodeBlockTest42 {
            public static void main(String[] args) {
            System.out.println("CODE_BLOCK_TEST_42");
            }
            }]]></ac:plain-text-body>
            </ac:structured-macro>
            """.trimIndent(),
        )

        val code = listOf(
            "public class CodeBlockTest42 {",
            "public static void main(String[] args) {",
            "System.out.println(\"CODE_BLOCK_TEST_42\");",
            "}",
            "}",
        ).joinToString("\n")
        assertThat(result.bodyText).isEqualTo(
            "Here is the provided Java class:\n\n```java\n$code\n```",
        )
        assertThat(result.codeBlocks).containsExactly(
            ParsedConfluenceCodeBlock(
                language = "java",
                code = code,
            ),
        )
    }

    @Test
    fun `renders tables in place and keeps them in the table list`() {
        val result = parser.parse(
            """
            <p>Deployment targets:</p>
            <table>
              <tr><th>Environment</th><th>Namespace</th></tr>
              <tr><td>Production</td><td>prod</td></tr>
            </table>
            """.trimIndent(),
        )

        val table = "| Environment | Namespace |\n| --- | --- |\n| Production | prod |"
        assertThat(result.bodyText).isEqualTo("Deployment targets:\n\n$table")
        assertThat(result.tables).containsExactly(table)
    }

    @Test
    fun `removes unsupported structured macros from body text`() {
        val result = parser.parse(
            """
            <p>Visible before macro.</p>
            <ac:structured-macro ac:name="toc">
              <ac:parameter ac:name="maxLevel">2</ac:parameter>
            </ac:structured-macro>
            <p>Visible after macro.</p>
            """.trimIndent(),
        )

        assertThat(result.bodyText).isEqualTo(
            """
            Visible before macro.

            Visible after macro.
            """.trimIndent(),
        )
        assertThat(result.codeBlocks).isEmpty()
    }

    @Test
    fun `renders external links and keeps confluence page links as plain labels`() {
        val result = parser.parse(
            """
            <p>See <a href="https://example.com/docs">the docs</a> and
            <ac:link><ri:page ri:content-title="Runbook" /><ac:plain-text-link-body><![CDATA[Runbook]]></ac:plain-text-link-body></ac:link>.</p>
            """.trimIndent(),
        )

        assertThat(result.bodyText).isEqualTo("See [the docs](https://example.com/docs) and Runbook.")
    }

    @Test
    fun `escapes markdown syntax that comes from page text`() {
        val result = parser.parse("<p>Use the flag --dry_run in the #ops channel.</p>")

        assertThat(result.bodyText).isEqualTo("""Use the flag --dry\_run in the \#ops channel.""")
    }

    @Test
    fun `keeps a paragraph that starts with a dash from becoming a list`() {
        val result = parser.parse("<p>- not a list</p>")

        assertThat(result.bodyText).isEqualTo("""\- not a list""")
    }

    @Test
    fun `reads content that sits inside layout containers`() {
        val result = parser.parse(
            "<ac:layout><ac:layout-section><ac:layout-cell>" +
                "<p>Inside layout</p>" +
                "</ac:layout-cell></ac:layout-section></ac:layout>",
        )

        assertThat(result.bodyText).isEqualTo("Inside layout")
    }
}
