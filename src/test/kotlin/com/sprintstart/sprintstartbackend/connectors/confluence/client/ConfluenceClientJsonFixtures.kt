package com.sprintstart.sprintstartbackend.connectors.confluence.client

import kotlinx.serialization.json.Json

internal val CONFLUENCE_TEST_JSON = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

internal fun spacesResponse(
    spaces: List<String> = emptyList(),
    next: String? = null,
    extraFields: String = "",
): String {
    return """
        {
          "results": [${spaces.joinToString(",")}],
          "_links": { ${nextJsonField(next)} }$extraFields
        }
        """.trimIndent()
}

internal fun spaceJson(
    id: String,
    key: String,
    alias: String = key.lowercase(),
): String {
    return """
        {
          "id": "$id",
          "key": "$key",
          "name": "Architecture",
          "type": "global",
          "status": "current",
          "currentActiveAlias": "$alias",
          "_links": { "webui": "/spaces/$alias" },
          "unknownSpaceField": "ignored"
        }
        """.trimIndent()
}

internal fun pagesResponse(
    pages: List<String>,
    next: String? = null,
): String {
    return """
        {
          "results": [${pages.joinToString(",")}],
          "_links": { ${nextJsonField(next)} },
          "unknownCollectionField": true
        }
        """.trimIndent()
}

internal fun pageJson(
    id: String,
    parentId: String? = null,
    storageXhtml: String = "<p>Page $id</p>",
    versionNumber: Int = 3,
    versionCreatedAt: String = "2026-08-20T08:15:30Z",
    extraFields: String = "",
): String {
    val parentFields = parentId?.let { ", \"parentId\": \"$it\", \"parentType\": \"page\"" }.orEmpty()
    return """
        {
          "id": "$id",
          "title": "Page $id",
          "status": "current",
          "spaceId": "42"$parentFields,
          "version": {
            "number": $versionNumber,
            "createdAt": "$versionCreatedAt",
            "futureVersionField": false
          },
          "body": {
            "storage": {
              "representation": "storage",
              "value": ${CONFLUENCE_TEST_JSON.encodeToString(storageXhtml)}
            }
          },
          "_links": { "webui": "/spaces/ARCH/pages/$id" }$extraFields
        }
        """.trimIndent()
}

internal fun restrictionsResponse(
    accountIds: List<String> = emptyList(),
    groupIds: List<String> = emptyList(),
    start: Int = 0,
    limit: Int = 100,
    operation: String = "read",
    extraFields: String = "",
): String {
    val users = accountIds.joinToString(",") { accountId ->
        """{"accountId":"$accountId","displayName":"Synthetic User","email":"ignored@example.invalid"}"""
    }
    val groups = groupIds.joinToString(",") { groupId ->
        """{"id":"$groupId","name":"Synthetic Group","type":"group"}"""
    }
    return """
        {
          "operation": "$operation",
          "restrictions": {
            "user": {
              "results": [$users],
              "start": $start,
              "limit": $limit,
              "size": ${accountIds.size},
              "unknownUserCollectionField": "ignored"
            },
            "group": {
              "results": [$groups],
              "start": $start,
              "limit": $limit,
              "size": ${groupIds.size}
            }
          }$extraFields
        }
        """.trimIndent()
}

private fun nextJsonField(next: String?): String {
    return next?.let { "\"next\": ${CONFLUENCE_TEST_JSON.encodeToString(it)}" }.orEmpty()
}
