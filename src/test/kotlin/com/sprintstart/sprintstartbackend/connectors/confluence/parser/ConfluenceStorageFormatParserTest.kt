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
    fun `extracts clean body text and headings`() {
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
            Deployment
            Deploy the service to Kubernetes.
            Build image
            Apply manifest
            """.trimIndent(),
        )
        assertThat(result.sections).containsExactly(
            ParsedConfluenceSection(heading = "Deployment", level = 2),
        )
        assertThat(result.tables).isEmpty()
        assertThat(result.codeBlocks).isEmpty()
    }

    @Test
    fun `extracts code macro and excludes it from body text`() {
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

        assertThat(result.bodyText).isEqualTo("Here is the provided Java class:")
        assertThat(result.codeBlocks).containsExactly(
            ParsedConfluenceCodeBlock(
                language = "java",
                code = listOf(
                    "public class CodeBlockTest42 {",
                    "public static void main(String[] args) {",
                    "System.out.println(\"CODE_BLOCK_TEST_42\");",
                    "}",
                    "}",
                ).joinToString("\n"),
            ),
        )
    }

    @Test
    fun `converts tables to markdown and removes them from body text`() {
        val result = parser.parse(
            """
            <p>Deployment targets:</p>
            <table>
              <tr><th>Environment</th><th>Namespace</th></tr>
              <tr><td>Production</td><td>prod</td></tr>
            </table>
            """.trimIndent(),
        )

        assertThat(result.bodyText).isEqualTo("Deployment targets:")
        assertThat(result.tables).containsExactly(
            "| Environment | Namespace |\n| --- | --- |\n| Production | prod |",
        )
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
}
