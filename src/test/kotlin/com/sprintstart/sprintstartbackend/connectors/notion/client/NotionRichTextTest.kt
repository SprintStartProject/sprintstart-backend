package com.sprintstart.sprintstartbackend.connectors.notion.client

import com.sprintstart.sprintstartbackend.connectors.notion.NotionRichText
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NotionRichTextTest {
    private val json = NotionJsonFixtures.json

    @Test
    fun `preserves links formatting and fragment boundaries`() {
        val fragments = json.decodeFromString<List<NotionRichText>>(NotionJsonFixtures.read("rich-text.json"))

        assertThat(fragments).hasSize(4)
        assertThat(fragments[0].plainText).isEqualTo("Read ")
        assertThat(fragments[0].href).isNull()
        assertThat(fragments[0].annotations?.bold).isFalse()
        assertThat(fragments[1].plainText).isEqualTo("the docs")
        assertThat(fragments[1].href).isEqualTo("https://example.com/docs")
        assertThat(fragments[1].annotations?.bold).isTrue()
    }

    @Test
    fun `retains plain text for mentions and equations without needing their typed payloads`() {
        val fragments = json.decodeFromString<List<NotionRichText>>(NotionJsonFixtures.read("rich-text.json"))

        assertThat(fragments[2].type).isEqualTo("mention")
        assertThat(fragments[2].plainText).isEqualTo("Runbook")
        assertThat(fragments[3].type).isEqualTo("equation")
        assertThat(fragments[3].plainText).isEqualTo("x^2")
        val annotations = checkNotNull(fragments[3].annotations)
        assertThat(annotations.bold).isFalse()
        assertThat(annotations.italic).isTrue()
        assertThat(annotations.strikethrough).isTrue()
        assertThat(annotations.underline).isTrue()
        assertThat(annotations.code).isTrue()
        assertThat(annotations.color).isEqualTo("blue")
    }

    @Test
    fun `keeps minimal rich text compatible with existing table row fixtures`() {
        val fragment = json.decodeFromString<NotionRichText>("""{"plain_text":"Hello"}""")

        assertThat(fragment.plainText).isEqualTo("Hello")
        assertThat(fragment.href).isNull()
        assertThat(fragment.annotations).isNull()
        assertThat(fragment.type).isEqualTo("text")
    }
}
