package com.sprintstart.sprintstartbackend.user.external

import com.sprintstart.sprintstartbackend.user.external.dto.ProjectRoleShortDto
import java.util.UUID

interface ProjectRoleApi {
    // Todo: add doc
    fun getProjectRolesByIds(ids: Set<UUID>): Set<ProjectRoleShortDto>
}
