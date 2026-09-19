package com.sprintstart.sprintstartbackend.user.model.mapper

import com.sprintstart.sprintstartbackend.user.external.dto.ProjectRoleShortDto
import com.sprintstart.sprintstartbackend.user.model.entity.ProjectRole

fun ProjectRole.toShortDto() = ProjectRoleShortDto(
    id = this.id,
    name = this.name,
)
