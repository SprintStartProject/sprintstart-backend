package com.sprintstart.sprintstartbackend.connectors.notion.client

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class NotionPageResponsesTest {
    private val json = NotionJsonFixtures.json

    @Test
    fun `decodes page identity edit time and workspace parent`() {
        val page = json.decodeFromString<NotionPageResponse>(NotionJsonFixtures.read("page.json"))

        assertThat(page.id).isEqualTo("11111111-1111-4111-8111-111111111111")
        assertThat(page.url).isEqualTo("https://www.notion.so/11111111111141118111111111111111")
        assertThat(page.lastEditedTime).isEqualTo("2026-09-19T09:30:00.000Z")
        assertThat(page.inTrash).isFalse()
        assertThat(page.parent.type).isEqualTo("workspace")
        assertThat(page.parent.workspace).isTrue()
        assertThat(page.parent.pageId).isNull()
    }

    @Test
    fun `preserves title fragments under a user chosen property name`() {
        val page = json.decodeFromString<NotionPageResponse>(NotionJsonFixtures.read("page.json"))

        assertThat(page.properties).containsKey("Document title")
        val title = checkNotNull(page.properties.values.single { it.type == "title" }.title)
        assertThat(title.map { it.plainText }).containsExactly("SprintStart ", "Playground")
        assertThat(title.last().annotations?.bold).isTrue()
        assertThat(page.properties.getValue("Status").title).isNull()
    }

    @Test
    fun `decodes search continuation without filtering results in the model`() {
        val response = json.decodeFromString<NotionSearchResponse>(NotionJsonFixtures.read("search-pages.json"))

        assertThat(response.hasMore).isTrue()
        assertThat(response.nextCursor).isEqualTo("opaque-next-cursor")
        assertThat(response.results).hasSize(2)
        val parent = response.results.last().parent
        assertThat(parent.type).isEqualTo("data_source_id")
        assertThat(parent.dataSourceId).isEqualTo("33333333-3333-4333-8333-333333333333")
        assertThat(parent.databaseId).isEqualTo("44444444-4444-4444-8444-444444444444")
    }

    @Test
    fun `decodes an empty final search batch`() {
        val response = json.decodeFromString<NotionSearchResponse>(
            """{"results": [], "has_more": false, "next_cursor": null}""",
        )

        assertThat(response.results).isEmpty()
        assertThat(response.hasMore).isFalse()
        assertThat(response.nextCursor).isNull()
    }

    @ParameterizedTest
    @ValueSource(strings = ["page_id", "block_id", "data_source_id", "database_id"])
    fun `decodes each parent id variant`(type: String) {
        val parent = json.decodeFromString<NotionParentResponse>(
            """{"type": "$type", "$type": "55555555-5555-4555-8555-555555555555"}""",
        )

        val actual = when (type) {
            "page_id" -> parent.pageId
            "block_id" -> parent.blockId
            "data_source_id" -> parent.dataSourceId
            "database_id" -> parent.databaseId
            else -> error("Unexpected test case: $type")
        }
        assertThat(actual).isEqualTo("55555555-5555-4555-8555-555555555555")
        assertThat(parent.workspace).isNull()
    }

    @Test
    fun `accepts an empty title without inventing text`() {
        val property = json.decodeFromString<NotionPagePropertyResponse>(
            """{"id":"title", "type":"title", "title":[]}""",
        )

        assertThat(property.title).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = ["id", "url", "last_edited_time", "in_trash", "parent", "properties"])
    fun `rejects missing required page fields`(field: String) {
        val valid = json.parseToJsonElement(NotionJsonFixtures.read("page.json")).jsonObject
        json.decodeFromJsonElement<NotionPageResponse>(valid)

        assertThatThrownBy {
            json.decodeFromJsonElement<NotionPageResponse>(JsonObject(valid - field))
        }.isInstanceOf(SerializationException::class.java)
    }
}
