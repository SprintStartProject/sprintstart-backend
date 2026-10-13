package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service

import com.sprintstart.sprintstartbackend.user.external.UserApi
import java.util.UUID

/**
 * Whether the caller may act on a connection through any of its linked projects.
 *
 * Project membership is the tenant boundary: a caller reaches a connection when they can access at
 * least one project it is linked to. Admins pass through [UserApi.userHasAccessToProject], so no
 * role needs special-casing here. A connection linked to no project reaches nobody.
 *
 * @param authId The authenticated caller subject.
 * @param projectIds The projects the connection is linked to.
 */
internal fun UserApi.canAccessConnection(authId: String, projectIds: Set<UUID>): Boolean =
    projectIds.any { projectId -> userHasAccessToProject(authId, projectId) }
