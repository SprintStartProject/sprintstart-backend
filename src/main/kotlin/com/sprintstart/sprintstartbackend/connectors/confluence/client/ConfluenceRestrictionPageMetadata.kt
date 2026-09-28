package com.sprintstart.sprintstartbackend.connectors.confluence.client

import java.time.DateTimeException
import java.time.OffsetDateTime

internal data class ConfluenceRestrictionPageMetadata(
    val nextStart: Int,
    val hasMore: Boolean,
)

internal fun ConfluenceSpaceResponse.toDomain(): ConfluenceSpace {
    return ConfluenceSpace(
        id = id,
        key = key,
        name = name,
        type = type,
        status = status,
        currentActiveAlias = currentActiveAlias,
        webUiPath = links.webui,
    )
}

internal fun ConfluencePageResponse.toDomain(restrictions: ConfluencePageRestrictions): ConfluencePage {
    val createdAt = try {
        OffsetDateTime.parse(version.createdAt).toInstant()
    } catch (@Suppress("SwallowedException") exception: DateTimeException) {
        throw ConfluenceInvalidResponseException("mapping page $id")
    }
    return ConfluencePage(
        id = id,
        title = title,
        status = status,
        spaceId = spaceId,
        parentId = parentId,
        parentType = parentType,
        version = ConfluencePageVersion(
            number = version.number,
            createdAt = createdAt,
        ),
        storage = ConfluenceStorageBody(
            representation = body.storage.representation,
            value = body.storage.value,
        ),
        webUiPath = links.webui,
        restrictions = restrictions,
    )
}

internal fun ConfluenceRestrictionsResponse.validatePage(
    requestedStart: Int,
    requestedLimit: Int,
    requestContext: String,
): ConfluenceRestrictionPageMetadata {
    if (operation != READ_OPERATION) {
        throw ConfluenceInvalidResponseException(requestContext)
    }

    val users = restrictions.user
    val groups = restrictions.group
    validateSharedRestrictionMetadata(users, groups, requestedStart, requestedLimit, requestContext)

    val nextStart = try {
        Math.addExact(requestedStart, users.limit)
    } catch (@Suppress("SwallowedException") exception: ArithmeticException) {
        throw ConfluenceInvalidResponseException(requestContext)
    }
    return ConfluenceRestrictionPageMetadata(
        nextStart = nextStart,
        hasMore = users.size == users.limit || groups.size == groups.limit,
    )
}

private fun validateSharedRestrictionMetadata(
    users: ConfluenceRestrictedUsersResponse,
    groups: ConfluenceRestrictedGroupsResponse,
    requestedStart: Int,
    requestedLimit: Int,
    requestContext: String,
) {
    val invalid = users.start != requestedStart ||
        groups.start != requestedStart ||
        users.limit <= 0 ||
        groups.limit <= 0 ||
        users.limit != groups.limit ||
        users.limit > requestedLimit ||
        users.size != users.results.size ||
        groups.size != groups.results.size ||
        users.size !in 0..users.limit ||
        groups.size !in 0..groups.limit ||
        users.results.any { user -> user.accountId.isBlank() } ||
        groups.results.any { group -> group.id.isBlank() }

    if (invalid) {
        throw ConfluenceInvalidResponseException(requestContext)
    }
}

private const val READ_OPERATION = "read"
