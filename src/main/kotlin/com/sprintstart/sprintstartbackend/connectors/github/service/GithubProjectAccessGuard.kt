package com.sprintstart.sprintstartbackend.connectors.github.service

import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.ProjectAccessDeniedException
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Guards GitHub source operations with project-scoped access checks.
 *
 * The guard delegates project ownership and membership decisions to the user module and emits the
 * same source-agnostic denial for every GitHub entrypoint. Callers must invoke it before looking up
 * a repository connection, PAT, or any other source state.
 */
@Component
class GithubProjectAccessGuard(
    private val userApi: UserApi,
) {
    /**
     * Requires the authenticated caller to have access to the target project.
     *
     * @param authId External authentication identifier of the caller.
     * @param projectId Target project supplied by the caller.
     * @throws ProjectAccessDeniedException when the caller cannot access the target project.
     */
    fun requireProjectAccess(authId: String, projectId: UUID) {
        if (!userApi.userHasAccessToProject(authId, projectId)) {
            throw ProjectAccessDeniedException(projectId)
        }
    }
}
