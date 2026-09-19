package com.sprintstart.sprintstartbackend.user.external

import com.sprintstart.sprintstartbackend.user.external.dto.ProjectRoleShortDto
import java.util.UUID

interface ProjectRoleApi {
    /**
     * Returns the project roles matching the given ids.
     *
     * Unknown ids are silently omitted, so the result may be smaller than the requested id
     * set — or empty. Roles are returned in their short form without linked skills.
     *
     * @param ids Ids of the project roles to resolve.
     * @return The matching project roles, mapped to [ProjectRoleShortDto]s.
     */
    fun getProjectRolesByIds(ids: Set<UUID>): Set<ProjectRoleShortDto>
}
